package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 8.3: result row for the per-bucket-per-category `mood_entries`
 * aggregation. One row per `(bucket, mood_score)` pair where the bucket
 * has at least one entry; pairs with zero entries are absent from the
 * result set and the aggregator zero-fills them in Kotlin.
 *
 * Powers the new "Mood Mix over Time" stacked-bar chart on
 * `DashboardActivity`. Each bar is one bucket; each stacked segment is
 * the percentage of `entryCount` for one of the six Ekman categories.
 *
 * Worst case row count: 30 daily buckets × 6 categories = 180 rows for
 * Month range; 14 weekly buckets × 6 = 84 rows for All Time. Same shape
 * as the existing `MoodBucketRow` query — no new performance budget.
 */
data class MoodCategoryBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "score") val score: Int,
    @ColumnInfo(name = "entryCount") val entryCount: Int,
)
