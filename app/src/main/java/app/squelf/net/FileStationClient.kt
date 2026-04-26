package app.squelf.net

import app.squelf.data.SquelfSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class FileStationClient(private val settings: SquelfSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = "https://${settings.nasHost}:${settings.nasPort}"

    @Throws(IOException::class)
    fun login(): String {
        val url = buildString {
            append(baseUrl)
            append("/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login")
            append("&format=sid&session=FileStation")
            append("&account=").append(encode(settings.nasUsername))
            append("&passwd=").append(encode(settings.nasPassword))
        }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("login http ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            if (!json.optBoolean("success")) {
                val code = json.optJSONObject("error")?.optInt("code", -1) ?: -1
                throw IOException("login failed (synology code $code)")
            }
            return json.getJSONObject("data").getString("sid")
        }
    }

    @Throws(IOException::class)
    fun upload(sid: String, file: File, targetPath: String) {
        val url = "$baseUrl/webapi/entry.cgi?_sid=${encode(sid)}"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api", "SYNO.FileStation.Upload")
            .addFormDataPart("version", "2")
            .addFormDataPart("method", "upload")
            .addFormDataPart("path", targetPath)
            .addFormDataPart("create_parents", "true")
            .addFormDataPart("overwrite", "true")
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("image/jpeg".toMediaType())
            )
            .build()
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("upload http ${response.code}")
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text)
            if (!json.optBoolean("success")) {
                val code = json.optJSONObject("error")?.optInt("code", -1) ?: -1
                throw IOException("upload failed (synology code $code)")
            }
        }
    }

    fun logout(sid: String) {
        try {
            val url = "$baseUrl/webapi/auth.cgi?" +
                "api=SYNO.API.Auth&version=6&method=logout&session=FileStation" +
                "&_sid=${encode(sid)}"
            client.newCall(Request.Builder().url(url).get().build()).execute().close()
        } catch (_: Exception) {
            // best-effort; uploads already completed
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
