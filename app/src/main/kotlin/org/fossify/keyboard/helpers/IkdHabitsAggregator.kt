package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.HabitsBucketRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.system.measureTimeMillis

/**
 * Phase 9.3: read-only aggregator powering the dashboard's Habits section.
 * Sibling of [IkdAggregator] — the Habits surface gets its own aggregator
 * per Phase 9 orchestrator Decision #3 (bloating `IkdAggregator.Snapshot`
 * past test readability is a worse trade-off than parallel snapshots).
 *
 * Threading: [snapshot] runs on `Dispatchers.IO`. Composed alongside the
 * other dashboard aggregators on the same hop in `loadDashboard()`.
 *
 * Pure derivation (`Companion.buildSnapshot` and `Companion.computeLongestStreak`)
 * lives on the companion for JVM unit testing without standing up Room.
 */
class IkdHabitsAggregator(private val db: IkdDatabase) {

    /**
     * KPI streak unit suffix. Carried by [HabitsSnapshot] so the activity's
     * KPI label code is one `when` block.
     */
    enum class StreakUnit { DAYS, WEEKS }

    /**
     * One bucket on the four Habits trend charts. All four metric fields
     * are nullable so the chart layer renders missing buckets as line
     * breaks rather than fake zeros (Phase 3 convention).
     */
    data class HabitsBucket(
        val label: String,
        val sessionCount: Int,
        /** Average session duration in ms; null when `sessionCount == 0`. */
        val avgSessionDurationMs: Double?,
        /** Average flight time in ms; null when no event in bucket has flight ≥ 0. */
        val avgFlightMs: Double?,
        /** Error rate as a percentage (0..100); null when productive keystrokes is zero. */
        val errorRatePct: Double?,
    )

    /**
     * Top-level Habits payload. KPIs on top, per-bucket data underneath.
     * `totalSessions == 0` → activity hides the entire section.
     */
    data class HabitsSnapshot(
        val range: Range,
        val totalSessions: Int,
        val totalTypingTimeMs: Long,
        /** Average session duration across the range (ms); null when no finished sessions. */
        val avgSessionDurationMs: Double?,
        val longestStreak: Int,
        val streakUnit: StreakUnit,
        val buckets: List<HabitsBucket>,
    )

    /**
     * Computes a fresh Habits snapshot for the given range.
     *
     * @param range time-window selection (matches the shared [Range] enum).
     * @param moodFilter when non-null, restrict to sessions tagged with that
     *   mood_score (1..6). Wired through unconditionally in Phase 9.4.
     */
    suspend fun snapshot(
        range: Range,
        moodFilter: Int? = null,
    ): HabitsSnapshot = withContext(Dispatchers.IO) {
        var result: HabitsSnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val rows = db.IkdEventDao().getHabitsBuckets(
                range.bucketFormat,
                fromMs,
                toMs,
                moodFilter,
            )
            result = Companion.buildSnapshot(range, rows)
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "snapshot(${range.name}, mood=$moodFilter) took ${durationMs}ms")
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
        private const val LOG_TAG = "IkdHabitsAggregator"
        private const val MS_PER_DAY = 86_400_000L
        private const val PCT_MULTIPLIER = 100.0

        /**
         * Pure aggregation. No I/O, no time read.
         *
         * - `avgSessionDurationMs` per bucket is null when `sessionCount == 0`
         *   (line break on the chart).
         * - `errorRatePct` per bucket follows the Phase 7.1 productive-
         *   keystroke convention: null when `keystrokeCount == 0`.
         * - The streak helper expects the bucket list in ascending key
         *   order — SQL `ORDER BY bucket` already provides that.
         */
        internal fun buildSnapshot(
            range: Range,
            rows: List<HabitsBucketRow>,
        ): HabitsSnapshot {
            val buckets = rows.map { row ->
                val avgDur = if (row.sessionCount > 0) {
                    row.totalDurationMs.toDouble() / row.sessionCount.toDouble()
                } else {
                    null
                }
                val errorRate = if (row.keystrokeCount > 0) {
                    PCT_MULTIPLIER * row.correctionWeight / row.keystrokeCount.toDouble()
                } else {
                    null
                }
                HabitsBucket(
                    label = row.bucket,
                    sessionCount = row.sessionCount,
                    avgSessionDurationMs = avgDur,
                    avgFlightMs = row.avgFlightMs,
                    errorRatePct = errorRate,
                )
            }

            val totalSessions = rows.sumOf { it.sessionCount }
            val totalDurationMs = rows.sumOf { it.totalDurationMs }
            val avgSessionDurationMs = if (totalSessions > 0 && totalDurationMs > 0L) {
                totalDurationMs.toDouble() / totalSessions.toDouble()
            } else {
                null
            }

            val streak = computeLongestStreak(buckets)
            val streakUnit = when (range) {
                Range.WEEK, Range.MONTH -> StreakUnit.DAYS
                Range.ALL_TIME -> StreakUnit.WEEKS
            }

            return HabitsSnapshot(
                range = range,
                totalSessions = totalSessions,
                totalTypingTimeMs = totalDurationMs,
                avgSessionDurationMs = avgSessionDurationMs,
                longestStreak = streak,
                streakUnit = streakUnit,
                buckets = buckets,
            )
        }

        /**
         * Walks the bucket list (ascending key order) and counts maximal
         * runs of `sessionCount > 0`. Returns the largest run length, or 0
         * when the list is empty / no bucket has activity.
         *
         * No tolerance for gaps — a single zero-bucket breaks the streak.
         */
        internal fun computeLongestStreak(buckets: List<HabitsBucket>): Int {
            var longest = 0
            var current = 0
            for (bucket in buckets) {
                if (bucket.sessionCount > 0) {
                    current++
                    if (current > longest) longest = current
                } else {
                    current = 0
                }
            }
            return longest
        }
    }
}
