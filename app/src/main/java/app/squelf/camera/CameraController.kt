package app.squelf.camera

import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class CameraState(
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 0.5f,
    val maxZoom: Float = 10.0f,
    val evStops: Float = 0f,
    val minEv: Float = -2.0f,
    val maxEv: Float = 2.0f,
    val isCapturing: Boolean = false
)

sealed class CaptureResult {
    data class Success(val file: File) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
}

interface CameraController {
    val state: StateFlow<CameraState>
    fun setZoom(ratio: Float)
    fun adjustZoom(delta: Float)
    fun setEvStops(stops: Float)
    fun adjustEv(delta: Float)
    suspend fun capture(): CaptureResult
}
