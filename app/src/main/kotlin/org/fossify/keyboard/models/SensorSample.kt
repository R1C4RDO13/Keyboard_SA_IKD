package org.fossify.keyboard.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sensor_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionRecord::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"])
    ]
)
data class SensorSample(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "session_id") var sessionId: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long,
    @ColumnInfo(name = "sensor_type") var sensorType: String,
    @ColumnInfo(name = "x") var x: Float,
    @ColumnInfo(name = "y") var y: Float,
    @ColumnInfo(name = "z") var z: Float
)
