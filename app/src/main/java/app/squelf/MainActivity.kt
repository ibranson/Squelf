package app.squelf

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
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
import app.squelf.ui.CaptureControls
import app.squelf.ui.CaptureFlash
import app.squelf.ui.HorizonLine
import app.squelf.ui.SettingsScreen
import app.squelf.ui.ThumbnailPreview
import app.squelf.ui.rememberRollDegrees
import app.squelf.ui.theme.SquelfTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(applicationContext)
        val cameraController: CameraController = RealCameraController(applicationContext)
        val postureFlow = hingePostureFlow(applicationContext)

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
            visible = state.isCapturing,
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
