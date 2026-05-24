package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.HistogramRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.system.measureTimeMillis

/**
 * Phase 9.7: read-only aggregator for the dashboard's Keystroke Dynamics
 * section — three log-scale histograms over IKD, dwell-time, and
 * flight-time. Sibling of [IkdAggregator].
 *
 * Bucket edges are hardcoded on the companion ([BUCKET_EDGES_MS]); the
 * SQL `CASE` ladder lives in `IkdEventDao` and matches them line-for-line.
 *
 * Threading: [snapshot] runs on `Dispatchers.IO`. Composed alongside the
 * other dashboard aggregators on the same hop in `loadDashboard()`.
 *
 * Pure derivation lives on [Companion.buildHistogram] for JVM unit testing.
 */
class IkdDistributionAggregator(private val db: IkdDatabase) {

    /**
     * One histogram. `buckets[i]` is the count for `[BUCKET_EDGES_MS[i],
     * BUCKET_EDGES_MS[i+1])`. `outlierCount` is the count of rows above
     * the topmost edge.
     */
    data class Histogram(
        val buckets: List<Int>,
        val outlierCount: Int,
    )

    data class DistributionSnapshot(
        val range: Range,
        val ikdHistogram: Histogram,
        val holdHistogram: Histogram,
        val flightHistogram: Histogram,
    )

    suspend fun snapshot(
        range: Range,
        moodFilter: Int? = null,
        minKeystrokes: Int = 0,
    ): DistributionSnapshot = withContext(Dispatchers.IO) {
        var result: DistributionSnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val ikdRows = db.IkdEventDao().getIkdHistogram(fromMs, toMs, moodFilter, minKeystrokes)
            val holdRows = db.IkdEventDao().getDwellHistogram(fromMs, toMs, moodFilter, minKeystrokes)
            val flightRows = db.IkdEventDao().getFlightHistogram(fromMs, toMs, moodFilter, minKeystrokes)

            result = DistributionSnapshot(
                range = range,
                ikdHistogram = Companion.buildHistogram(ikdRows),
                holdHistogram = Companion.buildHistogram(holdRows),
                flightHistogram = Companion.buildHistogram(flightRows),
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "snapshot(${range.name}, mood=$moodFilter, min=$minKeystrokes) took ${durationMs}ms")
        }
        result!!
    }

    private fun computeRangeWindow(range: Range, nowMs: Long): Pair<Long, Long> {
        val days = range.days
        val fromMs = if (days != null) {
            startOfLocalDayMillis(nowMs) - (days - 1).toLong() * MS_PER_DAY
        } else {
            db.SessionDao().getEarliestSessionStart() ?: 0L
        }
        return fromMs to (nowMs + 1L)
    }

    private fun startOfLocalDayMillis(epochMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = epochMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val LOG_TAG = "IkdDistributionAggregator"
        private const val MS_PER_DAY = 86_400_000L

        /**
         * Log-scale bucket edges in milliseconds. The SQL `CASE` ladder
         * mirrors this — keep them in sync. Bucket `i` covers
         * `[BUCKET_EDGES_MS[i], BUCKET_EDGES_MS[i+1])`; rows ≥ the topmost
         * edge land in the outlier overflow.
         *
         * Decision #20 of the Phase 9 orchestrator.
         */
        val BUCKET_EDGES_MS: LongArray = longArrayOf(
            10, 20, 40, 80, 160, 320, 640, 1280, 2560, 5120, 10240,
        )

        /** Number of in-range buckets — `BUCKET_EDGES_MS.size - 1`. */
        const val BUCKET_COUNT: Int = 10

        /** SQL `CASE` ladder index used for outlier overflow rows. */
        const val OUTLIER_BUCKET_INDEX: Int = 10

        /**
         * Pure aggregation. No I/O. Folds the SQL `(bucketIndex, count)`
         * rows into a fixed-size [Histogram]. Negative indices (the
         * sub-10 ms `-1` sentinel) are dropped — they should be filtered
         * SQL-side already, but defensive zero-fill is cheap.
         */
        internal fun buildHistogram(rows: List<HistogramRow>): Histogram {
            val buckets = IntArray(BUCKET_COUNT)
            var outlierCount = 0
            for (row in rows) {
                when {
                    row.bucketIndex < 0 -> Unit // drop sub-10 ms / sentinel
                    row.bucketIndex >= BUCKET_COUNT -> outlierCount += row.count
                    else -> buckets[row.bucketIndex] = row.count
                }
            }
            return Histogram(
                buckets = buckets.toList(),
                outlierCount = outlierCount,
            )
        }

        /**
         * Generates the 10 X-axis labels for a histogram chart, formatted
         * as `"low–high ms"` (e.g., `"10–20 ms"`, `"5120–10240 ms"`).
         * Pure helper so the activity doesn't have to repeat the format
         * string per chart.
         */
        fun bucketLabels(): List<String> {
            return (0 until BUCKET_COUNT).map { idx ->
                val low = BUCKET_EDGES_MS[idx]
                val high = BUCKET_EDGES_MS[idx + 1]
                "$low–$high ms"
            }
        }
    }
}
