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
    @ColumnInfo(name = "is_correction") var isCorrection: Boolean,
    /**
     * Phase 7.1: per-event "weight" of a correction.
     * - `BACKSPACE` rows: 1 (one keystroke = one correction action).
     * - `AUTOCORRECT` rows: replaced span length (`oldSelEnd - oldSelStart`,
     *   coerced to ≥ 1).
     * - Everything else (ALPHA / DIGIT / SPACE / ENTER / OTHER / EMOJI): 0.
     *
     * Used by the weighted error-rate formula `100 * SUM(correction_weight)
     * / keystrokeCount` (where `keystrokeCount` = `COUNT(*) - COUNT(AUTOCORRECT)`).
     * Sessions with no autocorrects are byte-identical between the row-count
     * formula and this one.
     */
    @ColumnInfo(name = "correction_weight", defaultValue = "0") var correctionWeight: Int = 0
)
