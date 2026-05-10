package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.HabitsBucketRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
     *
     * Phase 9.11: `HOURS` for the new TODAY range. The streak KPI on TODAY
     * is rendered specially by the activity ("Today" / "—") rather than as
     * a count + suffix — see `DashboardActivity.renderHabitsSection`.
     */
    enum class StreakUnit { HOURS, DAYS, WEEKS }

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
                // Productive-keystroke denominator (excludes both BACKSPACE
                // and AUTOCORRECT) — matches IkdAggregator post the
                // `569f331b` fix. Without this, the Habits chart would
                // report a different error rate from the global KPI / Trends
                // chart for the same range.
                val errorRate = if (row.productiveKeystrokes > 0) {
                    PCT_MULTIPLIER * row.correctionWeight / row.productiveKeystrokes.toDouble()
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

            val streak = computeLongestStreak(buckets, range)
            val streakUnit = when (range) {
                Range.TODAY -> StreakUnit.HOURS
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
         * Walks the active buckets (ascending key order) and counts maximal
         * runs of *calendar-consecutive* keys. Returns the largest run
         * length, or 0 when no bucket has activity.
         *
         * Phase 9.16 fix: the SQL `getHabitsBuckets` query only emits rows
         * for buckets that have at least one session — empty days are
         * absent from the result set, not present with `sessionCount = 0`.
         * The previous implementation walked rows naively, so three
         * isolated days (e.g. May 1, May 3, May 5) reported a streak of 3.
         * The fix: parse adjacent bucket labels as dates/weeks/hours
         * (per [range]) and only increment when they are calendar-
         * consecutive. Non-consecutive labels reset the run length to 1.
         */
        internal fun computeLongestStreak(buckets: List<HabitsBucket>, range: Range): Int {
            val active = buckets.filter { it.sessionCount > 0 }
            if (active.isEmpty()) return 0

            var longest = 1
            var current = 1
            for (i in 1 until active.size) {
                if (areConsecutive(active[i - 1].label, active[i].label, range)) {
                    current++
                    if (current > longest) longest = current
                } else {
                    current = 1
                }
            }
            return longest
        }

        /**
         * Phase 9.16: returns true when [curr] is exactly one calendar unit
         * (day / week / hour, depending on [range]) after [prev]. Falls
         * back to false on parse failure so a malformed label can't
         * spuriously inflate a streak.
         */
        private fun areConsecutive(prev: String, curr: String, range: Range): Boolean {
            return runCatching {
                when (range) {
                    Range.WEEK, Range.MONTH -> ChronoUnit.DAYS.between(
                        LocalDate.parse(prev),
                        LocalDate.parse(curr),
                    ) == 1L
                    Range.ALL_TIME -> areConsecutiveWeeks(prev, curr)
                    Range.TODAY -> {
                        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH")
                        ChronoUnit.HOURS.between(
                            LocalDateTime.parse(prev, fmt),
                            LocalDateTime.parse(curr, fmt),
                        ) == 1L
                    }
                }
            }.getOrDefault(false)
        }

        /**
         * SQLite `%Y-%W` produces strings like "2026-18" — year + week
         * number (Monday-based, 00 covers days before the first Monday of
         * the year). Convert each to the Monday-of-that-week as a
         * `LocalDate` and check the calendar delta is exactly 7 days.
         * That handles year boundaries correctly: "2026-52" → "2027-00"
         * is consecutive iff the Mondays are 7 days apart.
         */
        private fun areConsecutiveWeeks(prev: String, curr: String): Boolean {
            val prevMonday = mondayOfWeek(prev) ?: return false
            val currMonday = mondayOfWeek(curr) ?: return false
            return ChronoUnit.DAYS.between(prevMonday, currMonday) == 7L
        }

        private fun mondayOfWeek(yearWeekLabel: String): LocalDate? {
            val parts = yearWeekLabel.split("-")
            if (parts.size != 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val week = parts[1].toIntOrNull() ?: return null
            // SQLite %W: Monday-based, week 00 covers the partial first
            // week (any days before the first Monday).
            val jan1 = LocalDate.of(year, 1, 1)
            val firstMonday = jan1.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            return if (week == 0) jan1 else firstMonday.plusWeeks((week - 1).toLong())
        }
    }
}
