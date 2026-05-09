package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.DayQualityRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.system.measureTimeMillis

/**
 * Phase 9.10: read-only aggregator for the Activity Quality scatter chart
 * (backspaces vs. autocorrections per day) inside the Habits section.
 * Sibling of [IkdAggregator].
 *
 * `dayIndex` on each point is the offset from the most-recent day in the
 * range — used by the activity to compute the fade alpha (most-recent =
 * full alpha, oldest = ~20% alpha).
 *
 * Threading: [snapshot] runs on `Dispatchers.IO`. Composed alongside the
 * other dashboard aggregators on the same hop in `loadDashboard()`.
 *
 * Pure derivation lives on [Companion.buildSnapshot] for JVM unit testing.
 */
class IkdQualityAggregator(private val db: IkdDatabase) {

    /** One scatter point. `dayIndex == 0` is the most-recent day in the range. */
    data class DayQualityPoint(
        val day: String,
        val backspaceCount: Int,
        val autocorrectionCount: Int,
        val dayIndex: Int,
    )

    data class QualitySnapshot(
        val range: Range,
        val points: List<DayQualityPoint>,
    )

    suspend fun snapshot(
        range: Range,
        moodFilter: Int? = null,
    ): QualitySnapshot = withContext(Dispatchers.IO) {
        var result: QualitySnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val rows = db.IkdEventDao().getDailyQuality(fromMs, toMs, moodFilter)
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
        private const val LOG_TAG = "IkdQualityAggregator"
        private const val MS_PER_DAY = 86_400_000L

        /**
         * Pure aggregation. SQL returns rows in DESC `day` order (newest
         * first), so `mapIndexed` assigns `dayIndex = 0` to the newest day.
         */
        internal fun buildSnapshot(
            range: Range,
            rows: List<DayQualityRow>,
        ): QualitySnapshot {
            val points = rows.mapIndexed { idx, row ->
                DayQualityPoint(
                    day = row.day,
                    backspaceCount = row.backspaceCount,
                    autocorrectionCount = row.autocorrectionCount,
                    dayIndex = idx,
                )
            }
            return QualitySnapshot(range = range, points = points)
        }
    }
}
