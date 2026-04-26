package app.squelf.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): SquelfSettings = SquelfSettings(
        nasHost = prefs.getString(KEY_HOST, "").orEmpty(),
        nasPort = prefs.getInt(KEY_PORT, 5001),
        nasUsername = prefs.getString(KEY_USERNAME, "").orEmpty(),
        nasPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        nasFolder = prefs.getString(KEY_FOLDER, "/home/Squelf").orEmpty(),
        autoDatedFolder = prefs.getBoolean(KEY_AUTO_DATED, true),
        wifiOnlyUpload = prefs.getBoolean(KEY_WIFI_ONLY, true),
        isoAuto = prefs.getBoolean(KEY_ISO_AUTO, true),
        showThumbnail = prefs.getBoolean(KEY_SHOW_THUMBNAIL, true),
        burstFps = prefs.getInt(KEY_BURST_FPS, 2),
        burstCount = prefs.getInt(KEY_BURST_COUNT, 5)
    )

    fun save(settings: SquelfSettings) {
        prefs.edit()
            .putString(KEY_HOST, settings.nasHost)
            .putInt(KEY_PORT, settings.nasPort)
            .putString(KEY_USERNAME, settings.nasUsername)
            .putString(KEY_PASSWORD, settings.nasPassword)
            .putString(KEY_FOLDER, settings.nasFolder)
            .putBoolean(KEY_AUTO_DATED, settings.autoDatedFolder)
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnlyUpload)
            .putBoolean(KEY_ISO_AUTO, settings.isoAuto)
            .putBoolean(KEY_SHOW_THUMBNAIL, settings.showThumbnail)
            .putInt(KEY_BURST_FPS, settings.burstFps)
            .putInt(KEY_BURST_COUNT, settings.burstCount)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "squelf_settings"
        const val KEY_HOST = "nasHost"
        const val KEY_PORT = "nasPort"
        const val KEY_USERNAME = "nasUsername"
        const val KEY_PASSWORD = "nasPassword"
        const val KEY_FOLDER = "nasFolder"
        const val KEY_AUTO_DATED = "autoDatedFolder"
        const val KEY_WIFI_ONLY = "wifiOnlyUpload"
        const val KEY_ISO_AUTO = "isoAuto"
        const val KEY_SHOW_THUMBNAIL = "showThumbnail"
        const val KEY_BURST_FPS = "burstFps"
        const val KEY_BURST_COUNT = "burstCount"
    }
}
