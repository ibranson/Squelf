package app.squelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.squelf.camera.CameraState
import app.squelf.camera.FlashMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

private val ZoomPresets = listOf(1f, 2f, 3f, 5f)
private const val EV_STEP = 0.3f

@Composable
fun CaptureControls(
    state: CameraState,
    levelVisible: Boolean,
    onToggleLevel: () -> Unit,
    onShutter: () -> Unit,
    onSetZoom: (Float) -> Unit,
    onAdjustEv: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showShutter: Boolean = true
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "%.1fx   EV %+.1f".format(state.zoomRatio, state.evStops),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                FlashIndicator(state.flashMode)
            }
            TextButton(onClick = onToggleLevel) {
                Text(
                    text = if (levelVisible) "LEVEL ON" else "LEVEL OFF",
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = ln(state.zoomRatio),
                onValueChange = { onSetZoom(exp(it)) },
                valueRange = ln(state.minZoom)..ln(state.maxZoom),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ZoomPresets.forEach { ratio ->
                    val selected = abs(state.zoomRatio - ratio) < 0.05f
                    FilledTonalButton(
                        onClick = { onSetZoom(ratio) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (selected) Color.White.copy(alpha = 0.4f)
                            else Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("${ratio.toInt()}x")
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AdjustButton("EV−") { onAdjustEv(-EV_STEP) }
                if (showShutter) {
                    ShutterButton(enabled = !state.isCapturing, onClick = onShutter)
                }
                AdjustButton("EV+") { onAdjustEv(EV_STEP) }
            }
        }
    }
}

@Composable
private fun FlashIndicator(mode: FlashMode) {
    val (label, color) = when (mode) {
        FlashMode.OFF -> "⚡ Off" to Color.White.copy(alpha = 0.4f)
        FlashMode.AUTO -> "⚡ Auto" to Color.White
        FlashMode.ON -> "⚡ On" to Color(0xFFFFEB3B)
        FlashMode.TORCH -> "⚡ Torch" to Color(0xFFFFEB3B)
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun AdjustButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White
        )
    ) {
        Text(label)
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.Gray
        )
    ) {}
}

/**
 * Compact controls column for the hinge-up cover-display layout. Drops the
 * round shutter (the camera preview itself acts as the shutter via tap, and
 * the 8BitDo remote covers shutter/burst), and packs the rest of the
 * controls vertically so they fit in the right strip above the lens cutouts.
 */
@Composable
fun HingeUpControls(
    state: CameraState,
    levelVisible: Boolean,
    onToggleLevel: () -> Unit,
    onSetZoom: (Float) -> Unit,
    onAdjustEv: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "%.1fx  EV %+.1f".format(state.zoomRatio, state.evStops),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
            FlashIndicator(state.flashMode)
        }
        TextButton(
            onClick = onToggleLevel,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp, vertical = 4.dp
            )
        ) {
            Text(
                text = if (levelVisible) "LEVEL ON" else "LEVEL OFF",
                color = Color.White
            )
        }
        Slider(
            value = ln(state.zoomRatio),
            onValueChange = { onSetZoom(exp(it)) },
            valueRange = ln(state.minZoom)..ln(state.maxZoom),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        // Two rows of zoom presets so all four fit in the narrow right strip
        // without clipping (the strip is too tight for a single 4-button row).
        ZoomPresets.chunked(2).forEach { rowRatios ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowRatios.forEach { ratio ->
                    val selected = abs(state.zoomRatio - ratio) < 0.05f
                    FilledTonalButton(
                        onClick = { onSetZoom(ratio) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 6.dp, vertical = 2.dp
                        ),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (selected) Color.White.copy(alpha = 0.4f)
                            else Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("${ratio.toInt()}x")
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = { onAdjustEv(-EV_STEP) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 4.dp
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                )
            ) { Text("EV−") }
            FilledTonalButton(
                onClick = { onAdjustEv(EV_STEP) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 4.dp
                ),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                )
            ) { Text("EV+") }
        }
    }
}

@Composable
fun CaptureFlash(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) {
        Box(modifier = modifier.background(Color.White))
    }
}
