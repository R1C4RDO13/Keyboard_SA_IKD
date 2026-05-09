package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.DailyBucketRow
import org.fossify.keyboard.interfaces.DayHourBucketRow
import org.fossify.keyboard.interfaces.HourWeekdayRow
import org.fossify.keyboard.interfaces.HourlyBucketRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.system.measureTimeMillis

/**
 * Phase 9.5 / 9.6 / 9.9: read-only aggregator powering the dashboard
 * Daily Activity section. One aggregator covers all three sub-phases —
 * the queries share the `(day, hour)` shape and the dashboard renders
 * five widgets from the same snapshot.
 *
 * - **9.5:** [DailyBucket] list → calendar heatmap + daily keypress bar.
 * - **9.6:** [HourlyBucket] list → 24-hour bar chart;
 *   [DayHourCell] (hour × weekday fold) → 24×7 circadian heatmap.
 * - **9.9:** [DayHourCell] list (date × hour) → bubble Usage Map.
 *
 * Threading: [snapshot] runs on `Dispatchers.IO`. Composed alongside the
 * other dashboard aggregators on the same hop in `loadDashboard()`.
 *
 * Pure derivation lives on [Companion.buildSnapshot] for JVM unit testing.
 */
class IkdActivityAggregator(private val db: IkdDatabase) {

    /** One day's keystroke count for the calendar heatmap & daily bar (9.5). */
    data class DailyBucket(val day: String, val keystrokeCount: Int)

    /** One hour-of-day's keystroke count for the 24-hour bar (9.6). */
    data class HourlyBucket(val hour: Int, val keystrokeCount: Int)

    /**
     * One `(day, hour)` activity cell. Used twice:
     *  - 9.6 hour×weekday heatmap (folded by `dayOfWeek(day)` in Kotlin);
     *  - 9.9 Usage Map bubble chart (one bubble per cell).
     */
    data class DayHourCell(val day: String, val hour: Int, val keystrokeCount: Int)

    /**
     * Top-level Daily Activity payload. Empty lists when the range is
     * empty or all queries returned nothing — `DashboardActivity` flips
     * each widget's visibility based on the matching list.
     *
     * `circadianCells` is **always all-time** per Phase 9 orchestrator
     * Decision #18 — single-week / single-month data is too sparse to
     * expose a circadian pattern. The card subtitle documents the
     * static scope.
     */
    data class ActivitySnapshot(
        val range: Range,
        val dailyBuckets: List<DailyBucket>,
        val hourlyBuckets: List<HourlyBucket>,
        val dayHourCells: List<DayHourCell>,
        val circadianCells: List<HourWeekdayRow>,
    )

    /**
     * Computes a fresh activity snapshot for the given range. Runs on
     * `Dispatchers.IO`. The activity-level circadian heatmap is range-
     * independent (always all-time).
     */
    suspend fun snapshot(
        range: Range,
        moodFilter: Int? = null,
    ): ActivitySnapshot = withContext(Dispatchers.IO) {
        var result: ActivitySnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val daily = db.IkdEventDao().getDailyKeystrokes(fromMs, toMs, moodFilter)
            // Phase 9.6 fills in hourly + circadian; 9.9 fills in dayHour.
            // 9.5 ships with empty placeholders — DashboardActivity hides
            // the matching widgets when their list is empty.
            val hourly = emptyList<HourlyBucketRow>()
            val dayHour = emptyList<DayHourBucketRow>()
            val circadian = emptyList<HourWeekdayRow>()

            result = Companion.buildSnapshot(range, daily, hourly, dayHour, circadian)
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
        private const val LOG_TAG = "IkdActivityAggregator"
        private const val MS_PER_DAY = 86_400_000L

        /**
         * Pure aggregation. Lives on the companion so it can be unit-tested
         * without standing up a Room DB. No I/O, no time read.
         */
        internal fun buildSnapshot(
            range: Range,
            daily: List<DailyBucketRow>,
            hourly: List<HourlyBucketRow>,
            dayHour: List<DayHourBucketRow>,
            circadian: List<HourWeekdayRow>,
        ): ActivitySnapshot {
            val dailyBuckets = daily.map { DailyBucket(day = it.day, keystrokeCount = it.keystrokeCount) }
            val hourlyBuckets = hourly.map { HourlyBucket(hour = it.hour, keystrokeCount = it.keystrokeCount) }
            val dayHourCells = dayHour.map {
                DayHourCell(day = it.day, hour = it.hour, keystrokeCount = it.keystrokeCount)
            }
            return ActivitySnapshot(
                range = range,
                dailyBuckets = dailyBuckets,
                hourlyBuckets = hourlyBuckets,
                dayHourCells = dayHourCells,
                circadianCells = circadian,
            )
        }
    }
}
