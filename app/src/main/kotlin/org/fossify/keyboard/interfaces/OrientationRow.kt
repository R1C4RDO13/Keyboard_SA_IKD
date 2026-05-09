package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.8: result row for the orientation breakdown aggregation.
 * One row per orientation int with at least one session in the range.
 *
 * Orientation int → label mapping lives on the activity side (see
 * `Configuration.ORIENTATION_*` plus the Phase 2 `-1` sentinel for
 * "capture disabled").
 */
data class OrientationRow(
    @ColumnInfo(name = "orientation") val orientation: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
    @ColumnInfo(name = "totalDurationMs") val totalDurationMs: Long,
)
