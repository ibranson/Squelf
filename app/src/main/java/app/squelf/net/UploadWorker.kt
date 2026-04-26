package app.squelf.net

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.squelf.data.SettingsRepository
import app.squelf.data.SquelfSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val path = inputData.getString(KEY_FILE_PATH)
            ?: return@withContext Result.failure(errorData("missing file path"))
        val file = File(path)
        if (!file.exists()) {
            return@withContext Result.failure(errorData("file not found: $path"))
        }

        val settings = SettingsRepository(applicationContext).load()
        if (settings.nasHost.isBlank() || settings.nasUsername.isBlank()) {
            return@withContext Result.failure(errorData("NAS not configured"))
        }

        val targetPath = resolveTargetPath(settings, file)
        val client = FileStationClient(settings)
        try {
            val sid = client.login()
            try {
                client.upload(sid, file, targetPath)
            } finally {
                client.logout(sid)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else Result.failure(errorData(e.message ?: "upload error"))
        }
    }

    private fun resolveTargetPath(settings: SquelfSettings, file: File): String {
        val base = settings.nasFolder.trimEnd('/')
        if (!settings.autoDatedFolder) return base
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(file.lastModified()))
        return "$base/$dateStr"
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    companion object {
        const val KEY_FILE_PATH = "filePath"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context, filePath: String, wifiOnly: Boolean): UUID {
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FILE_PATH to filePath))
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }
}
