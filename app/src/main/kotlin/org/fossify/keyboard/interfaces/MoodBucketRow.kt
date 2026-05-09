package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 8: result row for the bucketed `mood_entries` aggregation
 * (`MoodDao.getMoodBuckets`). Powers both the "Mood over Time" line chart
 * (consumed via `avgScore`) and the "Avg Mood" KPI on the global dashboard.
 *
 * `avgScore` is non-null because each bucket only exists in the result set
 * when at least one row falls inside it — buckets containing zero entries
 * are absent. The caller may still surface a missing chart point by
 * inserting a `null` value when a bucket key is missing from the result.
 */
data class MoodBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "avgScore") val avgScore: Double,
    @ColumnInfo(name = "entryCount") val entryCount: Int,
)
