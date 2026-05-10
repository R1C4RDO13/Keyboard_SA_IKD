package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.3: result row for the Habits bucketed aggregation.
 *
 * One row per `strftime` bucket joining session-side counts/durations with
 * event-side averages. Returned in ascending bucket-key order so the
 * Habits aggregator's streak helper can walk the list directly.
 *
 * `avgFlightMs` is null when the bucket has zero events with `flight_time_ms >= 0`
 * (only first-event sentinels). `correctionWeight` is the per-bucket sum of
 * `correction_weight`. `keystrokeCount` excludes AUTOCORRECT rows (matches
 * the WPM denominator). `productiveKeystrokes` excludes both AUTOCORRECT
 * and BACKSPACE rows — used as the error-rate denominator so it matches
 * `IkdAggregator`'s formula post the `569f331b` fix.
 */
data class HabitsBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
    @ColumnInfo(name = "totalDurationMs") val totalDurationMs: Long,
    @ColumnInfo(name = "avgFlightMs") val avgFlightMs: Double?,
    @ColumnInfo(name = "correctionWeight") val correctionWeight: Int,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
    @ColumnInfo(name = "productiveKeystrokes") val productiveKeystrokes: Int,
)
