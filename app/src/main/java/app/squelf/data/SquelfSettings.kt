package app.squelf.data

data class SquelfSettings(
    val nasHost: String = "",
    val nasPort: Int = 5001,
    val nasUsername: String = "",
    val nasPassword: String = "",
    val nasFolder: String = "/home/Squelf",
    val autoDatedFolder: Boolean = true,
    val wifiOnlyUpload: Boolean = true,
    val isoAuto: Boolean = true,
    val showThumbnail: Boolean = true,
    val burstFps: Int = 2,
    val burstCount: Int = 5
)
