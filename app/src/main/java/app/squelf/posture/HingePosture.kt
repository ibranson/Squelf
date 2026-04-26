package app.squelf.posture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class PostureInfo(val isTented: Boolean)

// Acceptable shooting range — closed (~0°) up through tented (~45°); rejects past tenting.
private const val ACTIVE_MIN_DEG = 0f
private const val ACTIVE_MAX_DEG = 45f

fun hingePostureFlow(context: Context): Flow<PostureInfo> = callbackFlow {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val hinge = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    if (hinge == null) {
        trySend(PostureInfo(isTented = false))
        awaitClose { }
        return@callbackFlow
    }
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val angle = event.values[0]
            trySend(PostureInfo(isTented = angle in ACTIVE_MIN_DEG..ACTIVE_MAX_DEG))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    sensorManager.registerListener(listener, hinge, SensorManager.SENSOR_DELAY_UI)
    awaitClose { sensorManager.unregisterListener(listener) }
}
