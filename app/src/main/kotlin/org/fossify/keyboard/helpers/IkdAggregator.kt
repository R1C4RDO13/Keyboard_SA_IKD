package org.fossify.keyboard.helpers

import org.fossify.keyboard.databases.IkdDatabase

/**
 * Read-only aggregator over `ikd.db`. Phase 3 read-side surface — never writes
 * and never holds onto rows. All work is one-shot SQL `GROUP BY` queries that
 * return at most ~52 rows per call (capped by `Range.bucketFormat` and the
 * configured retention window).
 *
 * Thread safety: the SQL execution is wrapped in `Dispatchers.IO` inside
 * `snapshot()`. Callers may invoke `snapshot()` from any coroutine context.
 *
 * Skeleton (Step 4): contracts only. The real query execution lands in Step 5.
 */
class IkdAggregator(private val db: IkdDatabase) {

    /**
     * Selectable time range. The `days` field expresses the inclusive lookback
     * window in local-time days; `null` means "from the earliest session in
     * the DB".
     */
    enum class Range(val days: Int?, val bucketFormat: String) {
        WEEK(7, "%Y-%m-%d"),
        MONTH(30, "%Y-%m-%d"),
        ALL_TIME(null, "%Y-%W"),
    }

    /**
     * Per-bucket aggregation. `wpm`, `avgIkdMs`, and `errorRatePct` are
     * nullable because a bucket may have no data even when adjacent buckets do
     * (e.g., a no-typing day in a Week range), and the chart layer must
     * render those as missing points rather than zeros.
     */
    data class Bucket(
        val label: String,
        val wpm: Double?,
        val avgIkdMs: Double?,
        val errorRatePct: Double?,
    )

    /**
     * Top-level dashboard payload. KPIs on top, per-bucket data underneath.
     */
    data class Snapshot(
        val range: Range,
        val totalSessions: Int,
        val totalTypingTimeMs: Long,
        val avgWpm: Double?,
        val avgErrorRatePct: Double?,
        val buckets: List<Bucket>,
    )

    /**
     * Computes a fresh dashboard snapshot. Implementation lands in Step 5.
     */
    @Suppress("UnusedPrivateProperty")
    suspend fun snapshot(range: Range): Snapshot {
        // Reference db so the constructor parameter compiles cleanly under
        // 'unused property' checks while the real implementation is queued
        // for Step 5.
        @Suppress("UnusedExpression") db
        return Snapshot(
            range = range,
            totalSessions = 0,
            totalTypingTimeMs = 0L,
            avgWpm = null,
            avgErrorRatePct = null,
            buckets = emptyList(),
        )
    }
}
