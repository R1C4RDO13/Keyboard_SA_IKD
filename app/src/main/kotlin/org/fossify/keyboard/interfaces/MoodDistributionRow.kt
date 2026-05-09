package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Phase 8: result row for the per-category count aggregation over
 * `mood_entries` (`MoodDao.getMoodDistribution`). Returns one row per
 * `mood_score` value (1..6) that has at least one entry in the range — scores
 * that have zero entries are absent and rendered as a 0-count row in the
 * Distribution panel by the caller.
 */
data class MoodDistributionRow(
    @ColumnInfo(name = "score") val score: Int,
    @ColumnInfo(name = "entryCount") val entryCount: Int,
)
