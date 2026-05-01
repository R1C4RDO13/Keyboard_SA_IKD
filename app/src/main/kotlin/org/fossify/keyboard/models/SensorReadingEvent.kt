package org.fossify.keyboard.models

data class SensorReadingEvent(
    val sessionId: String,        // Added for Phase 1.1 IME session alignment
    val timestamp: Long,
    val sensorType: String,       // "GYRO" or "ACCEL"
    val x: Float,
    val y: Float,
    val z: Float
)
