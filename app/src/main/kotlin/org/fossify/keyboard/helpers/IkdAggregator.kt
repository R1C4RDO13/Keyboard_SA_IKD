package org.fossify.keyboard.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.interfaces.EventBucketRow
import org.fossify.keyboard.interfaces.SessionBucketRow
import java.util.Calendar
import java.util.TimeZone

/**
 * Read-only aggregator over `ikd.db`. Phase 3 read-side surface — never writes
 * and never holds onto rows. All work is one-shot SQL `GROUP BY` queries that
 * return at most ~52 rows per call (capped by `Range.bucketFormat` and the
 * configured retention window).
 *
 * Thread safety: `snapshot()` runs entirely on `Dispatchers.IO`. Callers may
 * invoke it from any coroutine context.
 */
class IkdAggregator(private val db: IkdDatabase) {

    /**
     * Selectable time range. The `days` field expresses the inclusive lookback
     * window in local-time days; `null` means "from the earliest session in
     * the DB".
     */
    enum class Range(val days: Int?, val bucketFormat: String) {
        WEEK(WEEK_DAYS, "%Y-%m-%d"),
        MONTH(MONTH_DAYS, "%Y-%m-%d"),
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
     * Computes a fresh dashboard snapshot for the given range. Runs on
     * `Dispatchers.IO`. The returned Snapshot is plain data; callers should
     * marshal it back to the main thread for rendering.
     */
    suspend fun snapshot(range: Range): Snapshot = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val (fromMs, toMs) = computeRangeWindow(range, nowMs)

        val eventBuckets = db.IkdEventDao().getEventBuckets(range.bucketFormat, fromMs, toMs)
        val sessionBuckets = db.SessionDao().getSessionBuckets(range.bucketFormat, fromMs, toMs)

        Companion.buildSnapshot(range, eventBuckets, sessionBuckets)
    }

    private fun computeRangeWindow(range: Range, nowMs: Long): Pair<Long, Long> {
        val days = range.days
        val fromMs = if (days != null) {
            // Anchor on the local-day boundary so day buckets line up with the user's clock.
            startOfLocalDayMillis(nowMs) - (days - 1).toLong() * MS_PER_DAY
        } else {
            // ALL_TIME: read from the earliest session, falling back to "everything since
            // the start of recordable time" when the DB is empty.
            db.SessionDao().getEarliestSessionStart() ?: 0L
        }
        // Use now+1ms as the exclusive upper bound to cover events written this millisecond.
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
        // Range windows in days. Named to keep detekt's MagicNumber rule quiet.
        private const val WEEK_DAYS = 7
        private const val MONTH_DAYS = 30

        // Time conversion constants.
        private const val MS_PER_DAY = 86_400_000L
        private const val MS_PER_MINUTE = 60_000L

        // WPM and percentage scaling.
        private const val WORD_KEYSTROKES = 5.0
        private const val PCT_DIVISOR = 100.0
        private const val PCT_MULTIPLIER = 100.0

        /**
         * Pure aggregation. Lives on the companion so it can be unit-tested
         * without standing up a Room DB. No I/O, no time read, no
         * dependency on `IkdAggregator` instance state.
         */
        internal fun buildSnapshot(
            range: Range,
            eventBuckets: List<EventBucketRow>,
            sessionBuckets: List<SessionBucketRow>,
        ): Snapshot {
            val sessionByBucket = sessionBuckets.associateBy { it.bucket }
            val eventByBucket = eventBuckets.associateBy { it.bucket }
            val orderedKeys = (eventByBucket.keys + sessionByBucket.keys).toSortedSet()

            val merged = orderedKeys.map { key ->
                val ev = eventByBucket[key]
                val sess = sessionByBucket[key]
                val wpm = computeWpm(ev?.eventCount ?: 0, sess?.totalDurationMs ?: 0L)
                Bucket(
                    label = key,
                    wpm = wpm,
                    avgIkdMs = ev?.avgIkdMs,
                    errorRatePct = ev?.errorRatePct,
                )
            }

            val totalSessions = sessionBuckets.sumOf { it.sessionCount }
            val totalDurationMs = sessionBuckets.sumOf { it.totalDurationMs }
            val totalEvents = eventBuckets.sumOf { it.eventCount.toLong() }
            val avgWpm = computeWpm(totalEvents.toInt(), totalDurationMs)
            val avgErrorRatePct = computeOverallErrorRate(eventBuckets)

            return Snapshot(
                range = range,
                totalSessions = totalSessions,
                totalTypingTimeMs = totalDurationMs,
                avgWpm = avgWpm,
                avgErrorRatePct = avgErrorRatePct,
                buckets = merged,
            )
        }

        private fun computeOverallErrorRate(eventBuckets: List<EventBucketRow>): Double? {
            if (eventBuckets.isEmpty()) return null
            val totalEvents = eventBuckets.sumOf { it.eventCount.toLong() }
            if (totalEvents == 0L) return null
            // Recover the per-bucket correction count from the percentage so we
            // can sum corrections without re-querying the DB:
            //   pct = 100 * corrections / count  =>  corrections = pct * count / 100
            val totalCorrections = eventBuckets.sumOf {
                (it.errorRatePct * it.eventCount / PCT_DIVISOR)
            }
            return PCT_MULTIPLIER * totalCorrections / totalEvents
        }

        private fun computeWpm(eventCount: Int, totalDurationMs: Long): Double? {
            if (eventCount <= 0 || totalDurationMs <= 0L) return null
            // WPM convention: 5 keystrokes per word.
            return eventCount.toDouble() / WORD_KEYSTROKES *
                MS_PER_MINUTE.toDouble() / totalDurationMs.toDouble()
        }
    }
}
