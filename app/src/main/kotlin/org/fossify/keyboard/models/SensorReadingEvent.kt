package org.fossify.keyboard.models

data class SensorReadingEvent(
    val timestamp: Long,
    val sensorType: String,   // "GYRO" or "ACCEL"
    val x: Float,
    val y: Float,
    val z: Float
)
