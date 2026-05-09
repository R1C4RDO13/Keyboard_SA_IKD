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
 * `keystrokeCount` excludes `AUTOCORRECT` rows; it is the WPM denominator
 * (autocorrects are corrections, not new typing). `eventCount` stays a
 * `COUNT(*)` of every event so other consumers (KPI strip "events" cell)
 * do not silently drop rows.
 *
 * `correctionCount` is the count of `is_correction = 1` rows (BACKSPACE +
 * AUTOCORRECT). Used as the error-rate denominator subtractor:
 * `productiveKeystrokes = eventCount - correctionCount` is the count of
 * non-correction events (the keystrokes that produced kept text), and the
 * weighted error rate is `100 * correctionWeight / productiveKeystrokes`.
 * Without the BACKSPACE exclusion, deleting all your typing would only
 * report 50% (because BACKSPACE inflated the denominator).
 *
 * `correctionWeight` is the per-bucket sum of the `correction_weight`
 * column. Each `BACKSPACE` row carries the actual deletion count;
 * each `AUTOCORRECT` row carries the replaced span length.
 */
data class EventBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "avgIkdMs") val avgIkdMs: Double?,
    @ColumnInfo(name = "eventCount") val eventCount: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
    @ColumnInfo(name = "correctionCount") val correctionCount: Int,
    @ColumnInfo(name = "correctionWeight") val correctionWeight: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
)
