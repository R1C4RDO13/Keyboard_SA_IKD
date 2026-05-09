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
 * `COUNT(*)` of every event so other consumers (KPI strip "events" cell)
 * do not silently drop rows.
 *
 * Phase 7.1: `correctionWeight` is the per-bucket sum of the new
 * `correction_weight` column on `ikd_events`. Used as the new error-rate
 * numerator: `errorRatePct = 100 * correctionWeight / keystrokeCount`.
 * The pre-Phase-7.1 inline `errorRatePct` SQL projection is gone — the
 * percentage is computed in Kotlin so the formula lives in one place.
 */
data class EventBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "avgIkdMs") val avgIkdMs: Double?,
    @ColumnInfo(name = "eventCount") val eventCount: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
    @ColumnInfo(name = "correctionWeight") val correctionWeight: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
)
