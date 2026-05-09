package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Result row for the bucketed `ikd_events` aggregation query.
 *
 * `avgIkdMs` is null when the bucket has zero events with `ikd_ms >= 0`
 * (i.e., the bucket only contains the `-1` sentinel that marks the first
 * event of a session). Callers must surface that as a missing chart point
 * rather than a zero.
 *
 * Phase 7: `keystrokeCount` is a sibling of `eventCount` that excludes
 * `AUTOCORRECT` rows. The WPM formula uses it as the keystroke denominator
 * (autocorrects are corrections, not new typing); `eventCount` stays a
 * `COUNT(*)` of every event so other consumers (KPI strip "events" cell,
 * error-rate weighting) do not silently drop rows.
 */
data class EventBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "avgIkdMs") val avgIkdMs: Double?,
    @ColumnInfo(name = "errorRatePct") val errorRatePct: Double,
    @ColumnInfo(name = "eventCount") val eventCount: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
)
