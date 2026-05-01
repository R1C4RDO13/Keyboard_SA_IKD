package org.fossify.keyboard.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import org.fossify.keyboard.models.SensorReadingEvent

class KinematicSensorHelper(
    context: Context,
    private val getSessionId: () -> String,  // Updated for Phase 1.1: session ID provider
    private val onSample: (SensorReadingEvent) -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasGyro: Boolean get() = gyro != null
    val hasAccel: Boolean get() = accel != null

    fun start() {
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> "GYRO"
            Sensor.TYPE_ACCELEROMETER -> "ACCEL"
            else -> return
        }
        onSample(
            SensorReadingEvent(
                sessionId = getSessionId(),  // Phase 1.1: attach current session ID
                timestamp = SystemClock.uptimeMillis(),
                sensorType = type,
                x = event.values[0],
                y = event.values[1],
                z = event.values[2]
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
