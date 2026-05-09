package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.10: result row for the per-day backspace/autocorrection scatter.
 *
 * `day` is `YYYY-MM-DD` strftime key in local time. Returned newest-first
 * by the DAO query so the aggregator's `dayIndex` assignment is a single
 * `mapIndexed`. Empty days (zero of both kinds) absent from the result.
 */
data class DayQualityRow(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "backspaceCount") val backspaceCount: Int,
    @ColumnInfo(name = "autocorrectionCount") val autocorrectionCount: Int,
)
