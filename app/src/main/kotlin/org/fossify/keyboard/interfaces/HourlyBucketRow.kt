package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.6: result row for the 24-hour keystroke aggregation. `hour` is
 * `0..23` in the device's local time. AUTOCORRECT rows excluded.
 */
data class HourlyBucketRow(
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
)
