package app.squelf

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.squelf.camera.CameraController
import app.squelf.camera.CaptureResult
import app.squelf.camera.RealCameraController
import app.squelf.data.SettingsRepository
import app.squelf.posture.PostureInfo
import app.squelf.posture.hingePostureFlow
import app.squelf.remote.RemoteEvent
import app.squelf.ui.CaptureControls
import app.squelf.ui.CaptureFlash
import app.squelf.ui.HingeUpControls
import app.squelf.ui.HorizonLine
import app.squelf.ui.SettingsScreen
import app.squelf.ui.ThumbnailPreview
import app.squelf.ui.rememberDisplayQuadrant
import app.squelf.ui.rememberRollDegrees
import app.squelf.ui.theme.SquelfTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

private const val BURST_MAX_SHOTS = 10
private const val ZOOM_STEP = 0.2f
private const val EV_STEP = 0.3f
private val MAPPED_KEYCODES = setOf(
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_SPACE,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_DOWN,
)

class MainActivity : ComponentActivity() {

    private val _remoteEvents = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 16)
    private val remoteEvents: SharedFlow<RemoteEvent> = _remoteEvents.asSharedFlow()

    @Volatile private var currentlyTented: Boolean = false

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || !currentlyTented) return super.dispatchKeyEvent(event)
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val firstDown = isDown && event.repeatCount == 0
        val fromRealDevice = event.device?.isVirtual == false
        val mapped: RemoteEvent? = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> if (firstDown) RemoteEvent.Shutter else null
            KeyEvent.KEYCODE_BACK -> if (fromRealDevice && firstDown) RemoteEvent.Burst else null
            KeyEvent.KEYCODE_SPACE -> if (firstDown) RemoteEvent.ToggleLevel else null
            KeyEvent.KEYCODE_BUTTON_L1 -> if (isDown) RemoteEvent.EvDown else null
            KeyEvent.KEYCODE_BUTTON_R1 -> if (firstDown) RemoteEvent.CycleFlash else null
            KeyEvent.KEYCODE_DPAD_UP -> if (isDown) RemoteEvent.ZoomOut else null
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isDown) RemoteEvent.ZoomIn else null
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isDown) RemoteEvent.EvDown else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isDown) RemoteEvent.EvUp else null
            // Volume keys as a fallback shutter when the remote isn't handy.
            // Consume both DOWN and UP so the system volume UI doesn't appear.
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (firstDown) RemoteEvent.Shutter else null
            else -> null
        }
        // Consume only mapped keys; BACK from a virtual device (system on-screen back / gesture)
        // falls through so navigation continues to behave normally.
        val ours = event.keyCode in MAPPED_KEYCODES &&
            (event.keyCode != KeyEvent.KEYCODE_BACK || fromRealDevice)
        if (mapped != null) _remoteEvents.tryEmit(mapped)
        return if (ours) true else super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(applicationContext)
        val cameraController: CameraController = RealCameraController(applicationContext)
        val postureFlow = hingePostureFlow(applicationContext)

        lifecycleScope.launch {
            postureFlow.collect { info -> currentlyTented = info.isTented }
        }

        setContent {
            SquelfTheme {
                val posture by postureFlow.collectAsStateWithLifecycle(
                    initialValue = PostureInfo(isTented = false)
                )
                var showSettings by remember { mutableStateOf(false) }

                val quadrant = rememberDisplayQuadrant()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { innerPadding ->
                    BoxWithConstraints(
                        modifier = Modifier.padding(innerPadding).fillMaxSize()
                    ) {
                        val isLandscape = quadrant == 90 || quadrant == 270
                        val contentWidth = if (isLandscape) maxHeight else maxWidth
                        val contentHeight = if (isLandscape) maxWidth else maxHeight
                        Box(
                            modifier = Modifier
                                .size(contentWidth, contentHeight)
                                .align(Alignment.Center)
                                // Quadrant is the gravity-snapped angle of device "down"
                            // measured from default-portrait-down. Activity is locked
                            // to portrait, so the framebuffer renders as if the device
                            // is upright; rotating content by +quadrant cancels the
                            // device tilt so content reads upright to the viewer.
                            .graphicsLayer { rotationZ = quadrant.toFloat() }
                        ) {
                            val contentModifier = Modifier.fillMaxSize()
                            // Hinge-down (quadrant 180) is unsupported — UI rotation
                            // and camera handling don't behave well there. Prompt the
                            // user to rotate to one of the three valid orientations.
                            val isUpsideDown = quadrant == 180
                            when {
                                posture.isTented && isUpsideDown -> RotatePrompt(
                                    modifier = contentModifier
                                )
                                posture.isTented -> ViewfinderScreen(
                                    cameraController = cameraController,
                                    settingsRepository = settingsRepository,
                                    remoteEvents = remoteEvents,
                                    quadrant = quadrant,
                                    onBindPreview = { previewView ->
                                        if (cameraController is RealCameraController) {
                                            lifecycleScope.launch {
                                                cameraController.bind(this@MainActivity, previewView)
                                            }
                                        }
                                    },
                                    modifier = contentModifier
                                )
                                showSettings -> SettingsScreen(
                                    repository = settingsRepository,
                                    onDone = { showSettings = false },
                                    modifier = contentModifier
                                )
                                else -> TentPrompt(
                                    onOpenSettings = { showSettings = true },
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hinge-up layout: portrait camera frame in a landscape cover display.
 * Camera preview occupies the left ~60% of the screen; tap on it to set
 * focus + metering. Right ~40% holds compact controls anchored at the top
 * (including the dedicated shutter), leaving the bottom-right clear for
 * the camera lens cutouts.
 */
@Composable
private fun HingeUpLayout(
    modifier: Modifier,
    hasCameraPermission: Boolean,
    requestPermission: () -> Unit,
    previewFactory: (android.content.Context) -> PreviewView,
    state: app.squelf.camera.CameraState,
    roll: Float,
    levelVisible: Boolean,
    onToggleLevel: () -> Unit,
    burstActive: Boolean,
    burstShotCount: Int,
    burstTotal: Int,
    lastCaptureFile: File?,
    showThumbnail: Boolean,
    onShutter: () -> Unit,
    onTapFocus: (androidx.compose.ui.geometry.Offset) -> Unit,
    focusRingOffset: androidx.compose.ui.geometry.Offset?,
    focusRingAlpha: Float,
    onSetZoom: (Float) -> Unit,
    onAdjustEv: (Float) -> Unit
) {
    val previewShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    Row(modifier = modifier) {
        // Left: preview area, framed with a thin white rounded border. The
        // Box is aspect-locked to the camera frame (3:4 portrait) so
        // FIT_CENTER doesn't need to letterbox inside the bordered area.
        // Tap = focus + meter at that point. Shutter lives on the controls
        // strip and is also bound to the remote / volume keys.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(6.dp)
                .aspectRatio(3f / 4f)
                .border(width = 1.5.dp, color = Color.White, shape = previewShape)
                .clip(previewShape)
                .pointerInput(state.isReady) {
                    if (state.isReady) {
                        detectTapGestures { offset -> onTapFocus(offset) }
                    }
                }
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = previewFactory,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Camera permission required",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = requestPermission) { Text("Grant") }
                }
            }

            if (levelVisible && !state.isCapturing) {
                HorizonLine(rollDegrees = roll, modifier = Modifier.matchParentSize())
            }

            if (!state.isReady) {
                Text(
                    text = "Camera starting…",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (burstActive) {
                val targetAlpha = if (state.isCapturing) 0.45f else 0.22f
                val pulseAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    label = "burst-b-pulse"
                )
                val burstColor = Color(0xFFE53935)
                Text(
                    text = "B",
                    color = burstColor.copy(alpha = pulseAlpha),
                    fontSize = 240.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                LinearProgressIndicator(
                    progress = { burstShotCount / burstTotal.coerceAtLeast(1).toFloat() },
                    color = burstColor,
                    trackColor = burstColor.copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            CaptureFlash(
                visible = state.isCapturing && !burstActive,
                modifier = Modifier.matchParentSize()
            )

            FocusRing(offset = focusRingOffset, alpha = focusRingAlpha)
        }

        // Right: controls strip — top portion holds controls, bottom is left
        // clear for the camera lens cutouts at the bottom-right of the display.
        // weight(1f) absorbs whatever width is left after the 3:4 preview
        // claims its (height-derived) width.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HingeUpControls(
                state = state,
                levelVisible = levelVisible,
                onToggleLevel = onToggleLevel,
                onShutter = onShutter,
                onSetZoom = onSetZoom,
                onAdjustEv = onAdjustEv,
                modifier = Modifier.fillMaxWidth()
            )
            lastCaptureFile?.let { file ->
                if (showThumbnail) {
                    ThumbnailPreview(
                        file = file,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .size(width = 96.dp, height = 128.dp)
                    )
                }
            }
            // Spacer pushes any remaining content up; bottom of strip stays
            // empty so the lens cutouts (bottom-right ~40% of the cover
            // display) remain unobstructed by UI.
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun RotatePrompt(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Rotate the phone",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Hold the phone with the hinge up, left, or right — not down.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TentPrompt(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Tent the phone",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Fold the phone into a tent with the rear cameras facing the subject.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun ViewfinderScreen(
    cameraController: CameraController,
    settingsRepository: SettingsRepository,
    remoteEvents: SharedFlow<RemoteEvent>,
    quadrant: Int,
    onBindPreview: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    // Activity is locked to portrait, so Display.getRotation() always reports 0
    // and CameraX delivers portrait-aspect frames. When the user rotates the
    // phone to a landscape posture our content container also rotates (via
    // graphicsLayer in the parent), but the PreviewView inside ends up with
    // landscape dimensions. Without telling CameraX about that, the portrait
    // frame gets stretched to fill, squashing the image horizontally.
    //
    // Push the gravity-derived quadrant into CameraX's targetRotation so it
    // delivers frames whose aspect matches the rotated container.
    // ImageCapture's targetRotation drives the EXIF orientation written into
    // the saved JPEG. It must describe the actual physical device orientation
    // (Surface.ROTATION_* is the rotation of the displayed content, which is
    // the inverse of the physical rotation):
    //   hinge-left  (quadrant 90, device 90° CCW physical) → ROTATION_270
    //   hinge-right (quadrant 270, device 90° CW physical) → ROTATION_90
    val captureRotation = when (quadrant) {
        90 -> android.view.Surface.ROTATION_270
        180 -> android.view.Surface.ROTATION_180
        270 -> android.view.Surface.ROTATION_90
        else -> android.view.Surface.ROTATION_0
    }
    // Preview's targetRotation rotates the displayed frame. The parent's
    // graphicsLayer already rotates the entire PreviewView visually, so the
    // preview rotation must be the inverse of capture's so the two cancel
    // and the camera image reads upright. (UI siblings inside the same
    // graphicsLayer parent are unaffected — they only get the graphicsLayer
    // rotation, which is correct for them.)
    val previewRotation = when (quadrant) {
        90 -> android.view.Surface.ROTATION_90
        180 -> android.view.Surface.ROTATION_180
        270 -> android.view.Surface.ROTATION_270
        else -> android.view.Surface.ROTATION_0
    }
    LaunchedEffect(previewRotation, captureRotation) {
        cameraController.setTargetRotation(previewRotation, captureRotation)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by cameraController.state.collectAsStateWithLifecycle()
    var levelVisible by remember { mutableStateOf(true) }
    var lastCaptureFile by remember { mutableStateOf<File?>(null) }
    val roll = rememberRollDegrees()
    val showThumbnail = remember { settingsRepository.load().showThumbnail }

    val captureOnce: () -> Unit = {
        scope.launch {
            val result = cameraController.capture()
            if (result is CaptureResult.Success) {
                lastCaptureFile = result.file
            }
        }
        Unit
    }

    var burstJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var burstActive by remember { mutableStateOf(false) }
    var burstShotCount by remember { mutableStateOf(0) }
    var burstTotal by remember { mutableStateOf(BURST_MAX_SHOTS) }

    // Tap-to-focus: previewView is captured at AndroidView creation and used
    // to build CameraX MeteringPoints in PreviewView's pixel space (the tap
    // offset comes in that same space because AndroidView matches the
    // VF Box). focusRingOffset drives a small ring overlay; focusRingAlpha
    // is animated 1→0 each tap and the offset is cleared at the end.
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var focusRingOffset by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val focusRingAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val onTapFocus: (androidx.compose.ui.geometry.Offset) -> Unit = { offset ->
        previewView?.let { pv ->
            val factory = pv.meteringPointFactory
            cameraController.focusAt(factory.createPoint(offset.x, offset.y))
        }
        focusRingOffset = offset
        scope.launch {
            focusRingAlpha.snapTo(1f)
            focusRingAlpha.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500)
            )
            focusRingOffset = null
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            // Don't leave the LED torch on when the user untents.
            cameraController.setFlashMode(app.squelf.camera.FlashMode.OFF)
        }
    }

    LaunchedEffect(Unit) {
        remoteEvents.collect { event ->
            when (event) {
                RemoteEvent.Shutter -> {
                    if (state.isReady) captureOnce()
                }
                RemoteEvent.Burst -> {
                    if (!state.isReady) return@collect
                    if (burstActive) return@collect // ignore re-press while burst is running
                    val s = settingsRepository.load()
                    val count = s.burstCount.coerceIn(1, BURST_MAX_SHOTS)
                    val intervalMs = (1000L / s.burstFps.coerceAtLeast(1)).coerceAtLeast(50L)
                    burstTotal = count
                    burstJob?.cancel()
                    burstJob = scope.launch {
                        burstActive = true
                        burstShotCount = 0
                        try {
                            repeat(count) {
                                val r = cameraController.capture()
                                if (r is CaptureResult.Success) {
                                    lastCaptureFile = r.file
                                    burstShotCount += 1
                                }
                                if (it < count - 1) kotlinx.coroutines.delay(intervalMs)
                            }
                        } finally {
                            burstActive = false
                            burstShotCount = 0
                        }
                    }
                }
                RemoteEvent.ZoomIn -> cameraController.adjustZoom(ZOOM_STEP)
                RemoteEvent.ZoomOut -> cameraController.adjustZoom(-ZOOM_STEP)
                RemoteEvent.EvUp -> cameraController.adjustEv(EV_STEP)
                RemoteEvent.EvDown -> cameraController.adjustEv(-EV_STEP)
                RemoteEvent.ToggleLevel -> levelVisible = !levelVisible
                RemoteEvent.CycleFlash -> cameraController.cycleFlash()
            }
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewFactory: (android.content.Context) -> PreviewView = { ctx ->
        PreviewView(ctx).apply {
            // SurfaceView (PERFORMANCE) renders to its window rect and ignores
            // Compose graphicsLayer transforms, which produces horizontal
            // compression once the parent is rotated for the landscape hinge
            // orientations. TextureView respects them.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // FIT_CENTER everywhere: the bordered preview Box is aspect-locked
            // to the camera frame so the image fills the border exactly with
            // no edge cropping. Any leftover space falls outside the border.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }.also {
            previewView = it
            onBindPreview(it)
        }
    }

    if (quadrant == 0) {
        HingeUpLayout(
            modifier = modifier.background(Color.Black),
            hasCameraPermission = hasCameraPermission,
            requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            previewFactory = previewFactory,
            state = state,
            roll = roll,
            levelVisible = levelVisible,
            onToggleLevel = { levelVisible = !levelVisible },
            burstActive = burstActive,
            burstShotCount = burstShotCount,
            burstTotal = burstTotal,
            lastCaptureFile = lastCaptureFile,
            showThumbnail = showThumbnail,
            onShutter = {
                scope.launch {
                    val result = cameraController.capture()
                    if (result is CaptureResult.Success) {
                        lastCaptureFile = result.file
                    }
                }
            },
            onTapFocus = onTapFocus,
            focusRingOffset = focusRingOffset,
            focusRingAlpha = focusRingAlpha.value,
            onSetZoom = cameraController::setZoom,
            onAdjustEv = cameraController::adjustEv
        )
        return
    }

    val previewShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val onShutterTap = {
        scope.launch {
            val result = cameraController.capture()
            if (result is CaptureResult.Success) {
                lastCaptureFile = result.file
            }
        }
        Unit
    }
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Bordered, tappable viewfinder. The parent's graphicsLayer rotation
        // in framebuffer space combined with the user's physical phone
        // rotation cancels out, so the layout aspect equals the perceived
        // aspect: aspectRatio(4f/3f) in layout reads as 4:3 landscape on the
        // rotated cover display, matching the camera frame. FIT_CENTER on
        // the inner PreviewView keeps the full FOV visible; leftover cover
        // display space falls outside the border (top/bottom letterbox in
        // the user's view), not inside it.
        Box(
            modifier = Modifier
                .offset(y = (-18).dp)
                .fillMaxWidth()
                .padding(0.dp)
                .aspectRatio(4f / 3f)
                .border(width = 1.5.dp, color = Color.White, shape = previewShape)
                .clip(previewShape)
                .pointerInput(state.isReady) {
                    if (state.isReady) {
                        detectTapGestures { offset -> onTapFocus(offset) }
                    }
                }
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = previewFactory,
                    modifier = Modifier.matchParentSize()
                )
            }

            if (levelVisible && !state.isCapturing) {
                HorizonLine(rollDegrees = roll, modifier = Modifier.matchParentSize())
            }

            if (!state.isReady) {
                Text(
                    text = "Camera starting…",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (burstActive) {
                val targetAlpha = if (state.isCapturing) 0.45f else 0.22f
                val pulseAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    label = "burst-b-pulse"
                )
                val burstColor = Color(0xFFE53935)
                Text(
                    text = "B",
                    color = burstColor.copy(alpha = pulseAlpha),
                    fontSize = 360.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                LinearProgressIndicator(
                    progress = { burstShotCount / burstTotal.coerceAtLeast(1).toFloat() },
                    color = burstColor,
                    trackColor = burstColor.copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }

            CaptureFlash(
                visible = state.isCapturing && !burstActive,
                modifier = Modifier.matchParentSize()
            )

            FocusRing(offset = focusRingOffset, alpha = focusRingAlpha.value)
        }

        // Permission UI sits at the outer level (the bordered Box is a no-op
        // if there's no permission yet).
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Camera permission required",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant")
                }
            }
        }

        // Thumbnail anchored to the rotated container's TopStart so it sits
        // in the empty cover-display space outside the bordered viewfinder.
        lastCaptureFile?.let { file ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
            ) {
                if (showThumbnail) {
                    ThumbnailPreview(
                        file = file,
                        modifier = Modifier.size(width = 120.dp, height = 160.dp)
                    )
                }
                Text(
                    text = file.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // The remote covers shutter and burst, so the bordered preview itself
        // VF tap is now reserved for tap-to-focus, so CaptureControls renders
        // its dedicated round shutter button alongside the EV +/− pair.
        CaptureControls(
            state = state,
            levelVisible = levelVisible,
            onToggleLevel = { levelVisible = !levelVisible },
            onShutter = onShutterTap,
            onSetZoom = cameraController::setZoom,
            onAdjustEv = cameraController::adjustEv,
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * Small ring overlay that briefly marks where the user tapped to focus.
 * Drawn directly onto a fillMaxSize Canvas so the ring center lands on the
 * tap offset without any layout-modifier gymnastics. When [offset] is null
 * (or alpha is 0) it draws nothing.
 */
@Composable
private fun FocusRing(
    offset: androidx.compose.ui.geometry.Offset?,
    alpha: Float
) {
    if (offset == null || alpha <= 0f) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radiusPx = with(density) { 28.dp.toPx() }
    val strokePx = with(density) { 2.dp.toPx() }
    val color = Color(0xFFFFEB3B).copy(alpha = alpha)
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawCircle(
            color = color,
            radius = radiusPx,
            center = offset,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TentPromptPreview() {
    SquelfTheme {
        TentPrompt(
            onOpenSettings = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
