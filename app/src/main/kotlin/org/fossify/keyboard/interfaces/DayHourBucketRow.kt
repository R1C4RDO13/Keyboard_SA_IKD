package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.6 / 9.9: result row for the per-day-per-hour aggregation.
 *
 * Used for the Hour×Weekday circadian heatmap (folded by SQL `%w` in 9.6) and
 * the Usage Map bubble chart (rendered as one bubble per `(day, hour)` cell
 * in 9.9). AUTOCORRECT rows excluded.
 */
data class DayHourBucketRow(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
)
