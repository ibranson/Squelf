package app.squelf.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MockCameraController(private val context: Context) : CameraController {

    private val _state = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    override fun setZoom(ratio: Float) {
        _state.update { it.copy(zoomRatio = ratio.coerceIn(it.minZoom, it.maxZoom)) }
    }

    override fun adjustZoom(delta: Float) = setZoom(_state.value.zoomRatio + delta)

    override fun setEvStops(stops: Float) {
        _state.update { it.copy(evStops = stops.coerceIn(it.minEv, it.maxEv)) }
    }

    override fun adjustEv(delta: Float) = setEvStops(_state.value.evStops + delta)

    override fun setFlashMode(mode: FlashMode) {
        _state.update { it.copy(flashMode = mode) }
    }

    override fun setTargetRotation(previewRotation: Int, captureRotation: Int) {
        // Mock has no real preview surface; nothing to rotate.
    }

    override fun cycleFlash() {
        val next = when (_state.value.flashMode) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.TORCH
            FlashMode.TORCH -> FlashMode.OFF
        }
        setFlashMode(next)
    }

    override suspend fun capture(): CaptureResult = withContext(Dispatchers.IO) {
        _state.update { it.copy(isCapturing = true) }
        val result = try {
            delay(250)
            val snap = _state.value
            val bitmap = renderPlaceholder(snap.zoomRatio, snap.evStops)
            val file = writeJpeg(bitmap)
            bitmap.recycle()
            CaptureResult.Success(file)
        } catch (e: Exception) {
            CaptureResult.Error(e.message ?: "capture failed")
        }
        _state.update { it.copy(isCapturing = false) }
        result
    }

    private fun renderPlaceholder(zoom: Float, ev: Float): Bitmap {
        val bmp = Bitmap.createBitmap(1920, 2560, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        paint.textSize = 140f
        canvas.drawText("SQUELF MOCK", bmp.width / 2f, bmp.height / 2f - 160f, paint)
        paint.textSize = 100f
        canvas.drawText(
            "zoom %.1fx   ev %+.1f".format(zoom, ev),
            bmp.width / 2f, bmp.height / 2f, paint
        )
        paint.textSize = 80f
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText(ts, bmp.width / 2f, bmp.height / 2f + 200f, paint)
        return bmp
    }

    private fun writeJpeg(bitmap: Bitmap): File {
        val dir = (context.getExternalFilesDir("captures") ?: context.filesDir).apply { mkdirs() }
        val name = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(dir, name)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file
    }
}
