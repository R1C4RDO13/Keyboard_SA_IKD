package org.fossify.keyboard.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey
    @ColumnInfo(name = "session_id") var sessionId: String,
    @ColumnInfo(name = "started_at") var startedAt: Long,
    @ColumnInfo(name = "ended_at") var endedAt: Long?,
    @ColumnInfo(name = "event_count") var eventCount: Int,
    @ColumnInfo(name = "sensor_count") var sensorCount: Int,
    @ColumnInfo(name = "device_orientation") var deviceOrientation: Int,
    @ColumnInfo(name = "locale") var locale: String
)
