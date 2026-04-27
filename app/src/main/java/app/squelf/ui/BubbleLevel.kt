package app.squelf.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.round

private const val LEVEL_THRESHOLD_DEGREES = 2f
private val LevelColor = Color(0xFF00E676)
private val OffLevelColor = Color.White

@Composable
fun rememberRollDegrees(): Float {
    val context = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                // Project gravity onto the screen plane and find the rotation angle from
                // the device's "down" direction. That tells us how the phone is rotated
                // about the camera axis (independent of pitch/forward-tilt).
                val planeDeg = Math.toDegrees(atan2(gx.toDouble(), -gy.toDouble())).toFloat()
                // Snap to the nearest 90° quadrant and report only the residual tilt
                // (in [-45, 45)). Lets the level work in any rotation: hinge up, down,
                // left, right — each is a new quadrant with its own zero.
                val shifted = planeDeg + 45f
                val mod = shifted - 90f * floor(shifted / 90f)
                roll = mod - 45f
                Log.d("SquelfRot", "roll gx=%.2f gy=%.2f gz=%.2f planeDeg=%.1f roll=%.1f".format(gx, gy, gz, planeDeg, roll))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (gravity != null) {
            sensorManager.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return roll
}

@Composable
fun rememberDisplayQuadrant(): Int {
    val context = LocalContext.current
    var quadrant by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                // Convention: quadrant=0 means the device is in the framebuffer's
                // default-portrait orientation. For this device's cover display that
                // is "hinge up" (gy ≈ +g, gx ≈ 0). Going clockwise from there:
                //   hinge-left → 90, hinge-down → 180, hinge-right → 270.
                val angle = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
                // Snap to nearest 90° quadrant in [0, 90, 180, 270]
                val snapped = ((round(angle / 90f).toInt()) % 4 + 4) % 4
                quadrant = snapped * 90
                Log.d("SquelfRot", "quad gx=%.2f gy=%.2f gz=%.2f angle=%.1f quadrant=%d".format(gx, gy, gz, angle, quadrant))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (gravity != null) {
            sensorManager.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }
    return quadrant
}

@Composable
fun HorizonLine(rollDegrees: Float, modifier: Modifier = Modifier) {
    val color = if (abs(rollDegrees) < LEVEL_THRESHOLD_DEGREES) LevelColor else OffLevelColor
    Canvas(modifier = modifier.fillMaxSize()) {
        rotate(degrees = -rollDegrees, pivot = center) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(size.width * 0.125f, y),
                end = Offset(size.width * 0.875f, y),
                strokeWidth = 3.dp.toPx()
            )
            drawCircle(
                color = color,
                radius = 4.dp.toPx(),
                center = Offset(size.width / 2f, y)
            )
        }
    }
}
