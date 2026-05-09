package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.6: result row for the all-time hour×weekday aggregation.
 *
 * `dow` is the SQLite-native day-of-week (0 = Sunday … 6 = Saturday).
 * `hour` is 0..23 in local time. The heatmap renderer reorders columns
 * to start at Monday for Western convention.
 */
data class HourWeekdayRow(
    @ColumnInfo(name = "dow") val dow: Int,
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
)
