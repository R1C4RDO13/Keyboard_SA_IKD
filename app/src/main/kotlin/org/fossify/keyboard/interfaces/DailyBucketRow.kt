package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.5: result row for the daily-keystroke aggregation.
 *
 * `day` is a `YYYY-MM-DD` strftime key in the device's local time. Excludes
 * AUTOCORRECT rows (matches Phase 7 WPM denominator — autocorrects are
 * corrections, not new typing).
 */
data class DailyBucketRow(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
)
