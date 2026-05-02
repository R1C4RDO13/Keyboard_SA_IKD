package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Result row for the bucketed `sessions` aggregation query.
 *
 * `totalDurationMs` only counts sessions that have been finalised
 * (`ended_at IS NOT NULL`). In-flight sessions still contribute to
 * `sessionCount` but not to the duration sum.
 */
data class SessionBucketRow(
    @ColumnInfo(name = "bucket") val bucket: String,
    @ColumnInfo(name = "totalDurationMs") val totalDurationMs: Long,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
)
