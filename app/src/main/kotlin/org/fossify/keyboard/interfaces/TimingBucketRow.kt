package org.fossify.keyboard.interfaces

import androidx.room.ColumnInfo

/**
 * Result row for the per-session bucketed `ikd_events` aggregation query (Phase 5).
 *
 * `bucketIndex` is the 0-based time-bucket position relative to the session's
 * start, computed as `(timestamp - startMs) / bucketWidthMs`.
 *
 * The averages are nullable because `AVG(CASE WHEN col >= 0 THEN col END)`
 * returns `NULL` when every row in the bucket carries the sentinel `-1`
 * (e.g. a bucket containing only the first event of the session). Callers
 * surface that as a missing chart point — never as zero.
 *
 * `avgHoldMs` / `avgFlightMs` are returned by the same `GROUP BY` for free
 * and are reserved for the Phase 6 multi-series timing chart; v1 ignores
 * them.
 */
data class TimingBucketRow(
    @ColumnInfo(name = "bucketIndex") val bucketIndex: Long,
    @ColumnInfo(name = "avgIkdMs") val avgIkdMs: Double?,
    @ColumnInfo(name = "avgHoldMs") val avgHoldMs: Double?,
    @ColumnInfo(name = "avgFlightMs") val avgFlightMs: Double?,
    @ColumnInfo(name = "eventCount") val eventCount: Int,
)
