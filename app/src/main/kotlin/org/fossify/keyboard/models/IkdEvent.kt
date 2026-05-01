package org.fossify.keyboard.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ikd_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionRecord::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["timestamp"])
    ]
)
data class IkdEvent(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "session_id") var sessionId: String,
    @ColumnInfo(name = "timestamp") var timestamp: Long,
    @ColumnInfo(name = "event_category") var eventCategory: String,
    @ColumnInfo(name = "ikd_ms") var ikdMs: Long,
    @ColumnInfo(name = "hold_time_ms") var holdTimeMs: Long,
    @ColumnInfo(name = "flight_time_ms") var flightTimeMs: Long,
    @ColumnInfo(name = "is_correction") var isCorrection: Boolean
)
