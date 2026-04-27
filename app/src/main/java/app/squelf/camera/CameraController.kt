package app.squelf.camera

import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class FlashMode { OFF, AUTO, ON, TORCH }

data class CameraState(
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 0.5f,
    val maxZoom: Float = 10.0f,
    val evStops: Float = 0f,
    val minEv: Float = -2.0f,
    val maxEv: Float = 2.0f,
    val flashMode: FlashMode = FlashMode.OFF,
    val isCapturing: Boolean = false,
    val isReady: Boolean = false
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
    fun setFlashMode(mode: FlashMode)
    fun cycleFlash()
    // Preview and capture need separate rotations: the PreviewView is rotated
    // by Compose graphicsLayer in the parent, so Preview's targetRotation must
    // cancel that; the saved JPEG's orientation comes purely from ImageCapture's
    // targetRotation, which must match the actual physical device orientation.
    fun setTargetRotation(previewRotation: Int, captureRotation: Int)
    suspend fun capture(): CaptureResult
}
