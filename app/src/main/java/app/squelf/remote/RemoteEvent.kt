package app.squelf.remote

sealed class RemoteEvent {
    object Shutter : RemoteEvent()
    object Burst : RemoteEvent()
    object ZoomIn : RemoteEvent()
    object ZoomOut : RemoteEvent()
    object EvUp : RemoteEvent()
    object EvDown : RemoteEvent()
    object ToggleLevel : RemoteEvent()
    object CycleFlash : RemoteEvent()
}
