package app.squelf.camera

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class RealCameraController(private val context: Context) : CameraController {

    private val _state = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = _state.asStateFlow()

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val preview: Preview = Preview.Builder().build()

    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture = capture
        provider.unbindAll()
        val cam = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview, capture
        )
        camera = cam

        val zoomState = cam.cameraInfo.zoomState.value
        val expState = cam.cameraInfo.exposureState
        val expStep = expState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.333f
        val expRange = expState.exposureCompensationRange
        _state.update { current ->
            current.copy(
                minZoom = zoomState?.minZoomRatio ?: current.minZoom,
                maxZoom = zoomState?.maxZoomRatio ?: current.maxZoom,
                zoomRatio = zoomState?.zoomRatio ?: current.zoomRatio,
                minEv = if (expState.isExposureCompensationSupported) expRange.lower * expStep else 0f,
                maxEv = if (expState.isExposureCompensationSupported) expRange.upper * expStep else 0f,
                isReady = true
            )
        }
    }

    override fun setZoom(ratio: Float) {
        val s = _state.value
        val target = ratio.coerceIn(s.minZoom, s.maxZoom)
        camera?.cameraControl?.setZoomRatio(target)
        _state.update { it.copy(zoomRatio = target) }
    }

    override fun adjustZoom(delta: Float) = setZoom(_state.value.zoomRatio + delta)

    override fun setEvStops(stops: Float) {
        val cam = camera ?: return
        val expState = cam.cameraInfo.exposureState
        if (!expState.isExposureCompensationSupported) return
        val step = expState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.333f
        val range = expState.exposureCompensationRange
        val target = stops.coerceIn(_state.value.minEv, _state.value.maxEv)
        val index = (target / step).roundToInt().coerceIn(range.lower, range.upper)
        cam.cameraControl.setExposureCompensationIndex(index)
        _state.update { it.copy(evStops = index * step) }
    }

    override fun adjustEv(delta: Float) = setEvStops(_state.value.evStops + delta)

    override fun setFlashMode(mode: FlashMode) {
        val cap = imageCapture
        val cam = camera
        when (mode) {
            FlashMode.OFF -> {
                cap?.flashMode = ImageCapture.FLASH_MODE_OFF
                cam?.cameraControl?.enableTorch(false)
            }
            FlashMode.AUTO -> {
                cap?.flashMode = ImageCapture.FLASH_MODE_AUTO
                cam?.cameraControl?.enableTorch(false)
            }
            FlashMode.ON -> {
                cap?.flashMode = ImageCapture.FLASH_MODE_ON
                cam?.cameraControl?.enableTorch(false)
            }
            FlashMode.TORCH -> {
                // Torch overrides per-shot flash; keep capture flash off so it doesn't double-fire.
                cap?.flashMode = ImageCapture.FLASH_MODE_OFF
                cam?.cameraControl?.enableTorch(true)
            }
        }
        _state.update { it.copy(flashMode = mode) }
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

    override suspend fun capture(): CaptureResult {
        val capture = imageCapture ?: return CaptureResult.Error("camera not ready")
        _state.update { it.copy(isCapturing = true) }
        return try {
            withContext(Dispatchers.IO) {
                val name = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".jpg"
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/Squelf"
                    )
                }
                val output = ImageCapture.OutputFileOptions
                    .Builder(resolver, collection, values)
                    .build()

                suspendCancellableCoroutine<CaptureResult> { cont ->
                    capture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                val savedUri = results.savedUri
                                val path = savedUri?.let { resolveFilePath(it) }
                                if (path != null) {
                                    cont.resume(CaptureResult.Success(File(path)))
                                } else {
                                    cont.resume(CaptureResult.Error("saved but could not resolve path"))
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                cont.resume(
                                    CaptureResult.Error(exception.message ?: "capture failed")
                                )
                            }
                        }
                    )
                }
            }
        } catch (e: Exception) {
            CaptureResult.Error(e.message ?: "capture failed")
        } finally {
            _state.update { it.copy(isCapturing = false) }
        }
    }

    private fun resolveFilePath(uri: android.net.Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                cursor.getString(idx)
            } else null
        }
    }
}
