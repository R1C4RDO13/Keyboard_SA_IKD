package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Result row for the bucketed `ikd_events` aggregation query.
 *
 * `avgIkdMs` is null when the bucket has zero events with `ikd_ms >= 0`
 * (i.e., the bucket only contains the `-1` sentinel that marks the first
 * event of a session). Callers must surface that as a missing chart point
 * rather than a zero.
 */
data class EventBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "avgIkdMs") val avgIkdMs: Double?,
    @ColumnInfo(name = "errorRatePct") val errorRatePct: Double,
    @ColumnInfo(name = "eventCount") val eventCount: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
)
