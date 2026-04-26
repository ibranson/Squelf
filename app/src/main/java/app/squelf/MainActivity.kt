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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.squelf.ui.HorizonLine
import app.squelf.ui.SettingsScreen
import app.squelf.ui.ThumbnailPreview
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding).fillMaxSize()
                    when {
                        posture.isTented -> ViewfinderScreen(
                            cameraController = cameraController,
                            settingsRepository = settingsRepository,
                            remoteEvents = remoteEvents,
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
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Fold the phone into a tent with the rear cameras facing the subject.",
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
    onBindPreview: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
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

    Box(modifier = modifier.background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also(onBindPreview)
                },
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
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant")
                }
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
            val burstColor = Color(0xFFE53935) // red 600
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

        lastCaptureFile?.let { file ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 12.dp)
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

        CaptureControls(
            state = state,
            levelVisible = levelVisible,
            onToggleLevel = { levelVisible = !levelVisible },
            onShutter = {
                scope.launch {
                    val result = cameraController.capture()
                    if (result is CaptureResult.Success) {
                        lastCaptureFile = result.file
                    }
                }
            },
            onSetZoom = cameraController::setZoom,
            onAdjustEv = cameraController::adjustEv,
            modifier = Modifier.matchParentSize()
        )

        CaptureFlash(
            visible = state.isCapturing && !burstActive,
            modifier = Modifier.matchParentSize()
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
