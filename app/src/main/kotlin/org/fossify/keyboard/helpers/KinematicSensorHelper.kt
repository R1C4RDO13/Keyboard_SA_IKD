package org.fossify.keyboard.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.models.SensorReadingEvent

class KinematicSensorHelper(
    private val context: Context,
    private val getSessionId: () -> String,  // Updated for Phase 1.1: session ID provider
    private val onSample: (SensorReadingEvent) -> Unit
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val hasGyro: Boolean get() = gyro != null
    val hasAccel: Boolean get() = accel != null

    fun start() {
        val cfg = context.config
        if (cfg.collectGyro) {
            gyro?.let { manager.registerListener(this, it, cfg.sensorSamplingRate) }
        }
        if (cfg.collectAccel) {
            accel?.let { manager.registerListener(this, it, cfg.sensorSamplingRate) }
        }
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
                // Wall-clock so sensor rows share an epoch with SessionRecord.startedAt.
                timestamp = System.currentTimeMillis(),
                sensorType = type,
                x = event.values[0],
                y = event.values[1],
                z = event.values[2]
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
