package app.squelf.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

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
                val denom = sqrt(gy * gy + gz * gz)
                roll = Math.toDegrees(atan2(gx.toDouble(), denom.toDouble())).toFloat()
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
fun HorizonLine(rollDegrees: Float, modifier: Modifier = Modifier) {
    val color = if (abs(rollDegrees) < LEVEL_THRESHOLD_DEGREES) LevelColor else OffLevelColor
    Canvas(modifier = modifier.fillMaxSize()) {
        rotate(degrees = rollDegrees, pivot = center) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(-size.width * 0.25f, y),
                end = Offset(size.width * 1.25f, y),
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
