package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 9.17: per-`(day, hour, mood_score)` keystroke aggregation. Sibling
 * to [DayHourBucketRow] — issued alongside it on the same aggregator hop.
 *
 * Drives the dominant-mood tinting of the Summary tab's Usage Map bubbles.
 * The DAO emits one row per `(day, hour, sessionMood)` triple where
 * `sessionMood` is `NULL` for sessions without a `mood_entries` row;
 * `IkdActivityAggregator.buildSnapshot` folds the rows by `(day, hour)`
 * and picks the mood with the largest count as the dominant one (cells
 * whose only rows are `null`-mood fall back to `dominantMood = null` and
 * render in the existing primary tone — Decision #7 of
 * `roadmap/Phase9/sub_plans/9.17_mood_colors_distribution_first.md`).
 *
 * Worst case row count: 30 days × 24 hours × 7 categories (6 + null) =
 * 5040 — well under the existing per-cell budget. AUTOCORRECT rows are
 * excluded by the same `event_category != 'AUTOCORRECT'` filter as the
 * sibling [DayHourBucketRow] query.
 */
data class DayHourMoodBucketRow(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "hour") val hour: Int,
    /** `mood_score` (1..6) of the session, or `null` when the session has no mood entry. */
    @ColumnInfo(name = "moodScore") val moodScore: Int?,
    @ColumnInfo(name = "keystrokeCount") val keystrokeCount: Int,
)
