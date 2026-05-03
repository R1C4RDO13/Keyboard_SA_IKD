package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.interfaces.SensorBucketRow
import org.fossify.keyboard.interfaces.TimingBucketRow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Read-only per-session chart loader (Phase 5). Sibling of
 * [IkdSessionStatsLoader] — kept separate so the KPI strip's data path
 * stays unchanged when chart aggregation is added or refined.
 *
 * Aggregation is SQL-side: each call issues two `GROUP BY` queries that
 * return ≤ 200 rows per chart. No raw event / sample folding in Kotlin —
 * mirrors the [IkdAggregator] discipline established in Phase 3.
 *
 * Threading: [load] runs entirely on `Dispatchers.IO`. Callers may invoke
 * it from any coroutine context.
 */
class IkdSessionChartLoader(private val db: IkdDatabase) {

    /** One bucket of the timing chart (avg IKD per time slot). */
    data class TimingPoint(val label: String, val avgIkdMs: Double?)

    /** One bucket of a sensor chart (avg magnitude per time slot). */
    data class SensorPoint(val label: String, val avgMagnitude: Float?)

    /**
     * Top-level chart payload for a single session. `timing` / `gyro` /
     * `accel` are independent: a session may have timing data but no
     * sensors (privacy-mode-on at sensor level), or vice versa, and each
     * list will be empty if its source table held nothing for the session.
     */
    data class SessionChartData(
        val sessionId: String,
        val durationMs: Long,
        val bucketWidthMs: Long,
        val timing: List<TimingPoint>,
        val gyro: List<SensorPoint>,
        val accel: List<SensorPoint>,
    )

    /**
     * Loads chart-ready data for one session. Returns `null` when the
     * session row is not found (e.g. it was deleted between the list
     * opening and the user tapping into it).
     */
    suspend fun load(sessionId: String): SessionChartData? = withContext(Dispatchers.IO) {
        var result: SessionChartData? = null
        val durationMs = measureTimeMillis {
            val record = db.SessionDao().getSession(sessionId) ?: return@measureTimeMillis
            val sessionDurationMs = computeSessionDurationMs(record.startedAt, record.endedAt)
            val bucketWidthMs = bucketWidthMs(sessionDurationMs)

            val timingRows = db.IkdEventDao().getSessionTimingBuckets(
                sessionId = sessionId,
                startMs = record.startedAt,
                bucketWidthMs = bucketWidthMs,
            )
            val sensorRows = db.SensorSampleDao().getSessionSensorBuckets(
                sessionId = sessionId,
                startMs = record.startedAt,
                bucketWidthMs = bucketWidthMs,
            )

            result = buildChartData(
                sessionId = sessionId,
                durationMs = sessionDurationMs,
                bucketWidthMs = bucketWidthMs,
                timingRows = timingRows,
                sensorRows = sensorRows,
            )
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "load(${sessionId.take(LOG_ID_LEN)}) took ${durationMs}ms")
        }
        result
    }

    private fun computeSessionDurationMs(startedAt: Long, endedAt: Long?): Long {
        if (endedAt != null && endedAt > startedAt) {
            return endedAt - startedAt
        }
        // In-flight or zero-duration session — fall back to the empty
        // window. The aggregator returns no rows because no events have
        // a matching bucket index, and the UI renders empty cards.
        return FALLBACK_DURATION_MS
    }

    companion object {
        private const val LOG_TAG = "IkdSessionChartLoader"
        private const val LOG_ID_LEN = 8

        // Bucket-width formula constants. Named to keep detekt's MagicNumber
        // rule quiet and to surface the cap in one place.
        internal const val MIN_BUCKET_MS = 50L
        internal const val TARGET_BUCKETS = 200L
        private const val FALLBACK_DURATION_MS = 30_000L

        // Time formatting constants for the X-axis labels.
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L

        // Sensor type identifiers — match KinematicSensorHelper's writes.
        private const val SENSOR_TYPE_GYRO = "GYRO"
        private const val SENSOR_TYPE_ACCEL = "ACCEL"

        /**
         * Bucket-width selection: at most [TARGET_BUCKETS] rows per chart,
         * never finer than [MIN_BUCKET_MS]. Pure function — exposed for
         * unit testing.
         */
        internal fun bucketWidthMs(sessionDurationMs: Long): Long {
            if (sessionDurationMs <= 0L) return MIN_BUCKET_MS
            val raw = ceil(sessionDurationMs.toDouble() / TARGET_BUCKETS.toDouble()).toLong()
            return max(MIN_BUCKET_MS, raw)
        }

        /**
         * Pure derivation. Lives on the companion so it can be unit-tested
         * without standing up a Room DB. No I/O, no time read, no
         * dependency on [IkdSessionChartLoader] instance state.
         */
        internal fun buildChartData(
            sessionId: String,
            durationMs: Long,
            bucketWidthMs: Long,
            timingRows: List<TimingBucketRow>,
            sensorRows: List<SensorBucketRow>,
        ): SessionChartData {
            val timing = timingRows.map { row ->
                TimingPoint(
                    label = formatBucketLabel(row.bucketIndex, bucketWidthMs),
                    avgIkdMs = row.avgIkdMs,
                )
            }

            val gyro = sensorRows.asSequence()
                .filter { it.sensorType == SENSOR_TYPE_GYRO }
                .map { toSensorPoint(it, bucketWidthMs) }
                .toList()

            val accel = sensorRows.asSequence()
                .filter { it.sensorType == SENSOR_TYPE_ACCEL }
                .map { toSensorPoint(it, bucketWidthMs) }
                .toList()

            return SessionChartData(
                sessionId = sessionId,
                durationMs = durationMs,
                bucketWidthMs = bucketWidthMs,
                timing = timing,
                gyro = gyro,
                accel = accel,
            )
        }

        private fun toSensorPoint(row: SensorBucketRow, bucketWidthMs: Long): SensorPoint {
            val magnitude = row.avgSquaredMagnitude?.let { sqrt(it).toFloat() }
            return SensorPoint(
                label = formatBucketLabel(row.bucketIndex, bucketWidthMs),
                avgMagnitude = magnitude,
            )
        }

        /**
         * Formats a bucket index back to an `m:ss` X-axis label, where the
         * bucket centre is `bucketIndex * bucketWidthMs` ms after session start.
         * Buckets are rounded to the nearest second for display so adjacent
         * sub-second buckets don't all read `0:00`.
         */
        private fun formatBucketLabel(bucketIndex: Long, bucketWidthMs: Long): String {
            val offsetMs = bucketIndex * bucketWidthMs
            val totalSeconds = offsetMs / MS_PER_SECOND
            val minutes = totalSeconds / SECONDS_PER_MINUTE
            val seconds = totalSeconds % SECONDS_PER_MINUTE
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
