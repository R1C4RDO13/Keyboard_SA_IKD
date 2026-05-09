package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.OrientationRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.system.measureTimeMillis

/**
 * Phase 9.8: read-only aggregator for the dashboard Orientation
 * breakdown donut. Sibling of [IkdAggregator]. Surfaces the existing
 * `SessionRecord.orientation` column for the first time since Phase 2
 * stored it.
 *
 * Threading: [snapshot] runs on `Dispatchers.IO`. Composed alongside
 * the other dashboard aggregators on the same hop.
 *
 * Pure derivation lives on [Companion.buildSnapshot] for JVM unit testing.
 */
class IkdOrientationAggregator(private val db: IkdDatabase) {

    /**
     * One slice on the donut. `orientation` is the raw int from the
     * `SessionRecord.orientation` column; the activity translates that to
     * a label string via `Configuration.ORIENTATION_*` matching.
     */
    data class OrientationSlice(
        val orientation: Int,
        val sessionCount: Int,
        val totalDurationMs: Long,
    )

    data class OrientationSnapshot(
        val range: Range,
        val slices: List<OrientationSlice>,
    )

    suspend fun snapshot(
        range: Range,
        moodFilter: Int? = null,
    ): OrientationSnapshot = withContext(Dispatchers.IO) {
        var result: OrientationSnapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val rows = db.SessionDao().getOrientationBreakdown(fromMs, toMs, moodFilter)
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
        private const val LOG_TAG = "IkdOrientationAggregator"
        private const val MS_PER_DAY = 86_400_000L

        /**
         * Pure aggregation. Folds the SQL rows into a [OrientationSnapshot].
         * Empty input → empty slice list (donut card hidden by activity).
         */
        internal fun buildSnapshot(
            range: Range,
            rows: List<OrientationRow>,
        ): OrientationSnapshot {
            val slices = rows.map {
                OrientationSlice(
                    orientation = it.orientation,
                    sessionCount = it.sessionCount,
                    totalDurationMs = it.totalDurationMs,
                )
            }
            return OrientationSnapshot(range = range, slices = slices)
        }
    }
}
