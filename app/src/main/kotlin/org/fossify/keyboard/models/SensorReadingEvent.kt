package org.fossify.keyboard.models

import kotlin.math.sqrt

data class SensorReadingEvent(
    val sessionId: String,        // Added for Phase 1.1 IME session alignment
    val timestamp: Long,
    val sensorType: String,       // "GYRO" or "ACCEL"
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Vector magnitude (`sqrt(x² + y² + z²)`) — derived in Kotlin per row at read
 * time, never stored. Cheap enough to recompute on every display since the
 * sensor sampling rate is bounded by the hardware (~50 Hz at SENSOR_DELAY_GAME).
 */
fun SensorReadingEvent.magnitude(): Float = sqrt(x * x + y * y + z * z)

