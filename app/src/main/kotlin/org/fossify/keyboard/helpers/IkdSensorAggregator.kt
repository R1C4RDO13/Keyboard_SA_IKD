package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.SensorBucketAggregateRow
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Phase 9.2: read-only aggregator for the dashboard Trends section's gyro
 * and accel magnitude line charts. Sibling of [IkdAggregator] —
 * non-overlapping read surface (sensor samples vs. timing events).
 *
 * Reuses [IkdAggregator.Range] (Week / Month / All Time) and the same
 * `strftime` bucket-format selection so the new sensor charts share the
 * same X axis with the existing IKD/WPM/Error charts.
 *
 * Threading: [snapshot] runs on `Dispatchers.IO`. Composed alongside the
 * other dashboard aggregators on the same hop in `DashboardActivity.loadDashboard()`.
 *
 * Pure derivation lives on [Companion.buildSnapshot] for JVM unit testing.
 *
 * Magnitude derivation matches Phase 5: SQL averages the squared
 * magnitude (`AVG(x*x + y*y + z*z)`) since SQLite has no `sqrt`; this
 * helper takes `Math.sqrt` per row in Kotlin (≤ 60 calls per snapshot).
 */
class IkdSensorAggregator(private val db: IkdDatabase) {

    /**
     * One bucket on either sensor magnitude line chart. `gyroMag` and
     * `accelMag` are nullable because a bucket inside the range may have
     * samples for only one sensor (or neither — e.g., when sensor capture
     * was disabled for all sessions in that bucket).
     */
    data class Bucket(
        val label: String,
        val gyroMag: Double?,
        val accelMag: Double?,
    )

    data class Snapshot(
        val range: Range,
        val buckets: List<Bucket>,
    )

    /**
     * Computes a fresh sensor-trends snapshot for the given range.
     *
     * @param range time-window selection (matches the shared [Range] enum).
     * @param moodFilter when non-null, restrict to sessions tagged with that
     *   mood_score (1..6). Wired through unconditionally in Phase 9.4.
     */
    suspend fun snapshot(
        range: Range,
        moodFilter: Int? = null,
        minKeystrokes: Int = 0,
    ): Snapshot = withContext(Dispatchers.IO) {
        var result: Snapshot? = null
        val durationMs = measureTimeMillis {
            val nowMs = System.currentTimeMillis()
            val (fromMs, toMs) = computeRangeWindow(range, nowMs)

            val rows = db.SensorSampleDao().getSensorBuckets(
                range.bucketFormat,
                fromMs,
                toMs,
                moodFilter,
                minKeystrokes,
            )
            result = Companion.buildSnapshot(range, rows)
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
        private const val LOG_TAG = "IkdSensorAggregator"
        private const val MS_PER_DAY = 86_400_000L

        /** Sensor type strings stored in the DB (matches `KinematicSensorHelper`). */
        const val SENSOR_TYPE_GYRO: String = "GYRO"
        const val SENSOR_TYPE_ACCEL: String = "ACCEL"

        /**
         * Pure aggregation. Lives on the companion so it can be unit-tested
         * without standing up a Room DB. No I/O, no time read.
         *
         * Folds the partition-by-sensor-type SQL output into one [Bucket]
         * per bucket key with both gyro and accel magnitudes alongside.
         * Missing magnitudes propagate as `null` so the chart layer renders
         * line breaks instead of fake zeros.
         */
        internal fun buildSnapshot(
            range: Range,
            rows: List<SensorBucketAggregateRow>,
        ): Snapshot {
            // Partition rows by bucket key, preserving SQL order via LinkedHashMap.
            val grouped = linkedMapOf<String, Pair<Double?, Double?>>()
            for (row in rows) {
                val mag = row.avgSquaredMagnitude?.let { sqrt(it) }
                val existing = grouped[row.bucket] ?: (null to null)
                grouped[row.bucket] = when (row.sensorType) {
                    SENSOR_TYPE_GYRO -> mag to existing.second
                    SENSOR_TYPE_ACCEL -> existing.first to mag
                    else -> existing
                }
            }
            // Sort by bucket key ascending so the chart's X axis order matches
            // the IKD chart group above (`sortedBy` is stable on Strings).
            val buckets = grouped.entries
                .sortedBy { it.key }
                .map { (label, pair) ->
                    Bucket(label = label, gyroMag = pair.first, accelMag = pair.second)
                }
            return Snapshot(range = range, buckets = buckets)
        }
    }
}
