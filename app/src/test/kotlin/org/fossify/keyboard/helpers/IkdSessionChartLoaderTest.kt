package org.fossify.keyboard.helpers

import org.fossify.keyboard.interfaces.SensorBucketRow
import org.fossify.keyboard.interfaces.TimingBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-math tests for [IkdSessionChartLoader.bucketWidthMs] and
 * [IkdSessionChartLoader.buildChartData]. Mirrors the [IkdAggregatorTest]
 * pattern: SQL is exercised end-to-end on-device, while this suite covers
 * the Kotlin-side derivation logic on the JVM without standing up
 * Robolectric or an instrumented test runner.
 */
class IkdSessionChartLoaderTest {

    // ------------------------------------------------------------------ //
    // bucketWidthMs                                                      //
    // ------------------------------------------------------------------ //

    @Test
    fun bucketWidthMs_zeroDuration_returnsMinFloor() {
        assertEquals(MIN_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(0L))
    }

    @Test
    fun bucketWidthMs_negativeDuration_returnsMinFloor() {
        // Defensive: callers shouldn't pass negatives, but the formula
        // shouldn't crash if they do.
        assertEquals(MIN_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(-1L))
    }

    @Test
    fun bucketWidthMs_tenSeconds_returns50ms() {
        // 10_000 / 200 = 50 → exactly the floor
        assertEquals(MIN_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(TEN_SECONDS_MS))
    }

    @Test
    fun bucketWidthMs_thirtySeconds_returns150ms() {
        // 30_000 / 200 = 150
        assertEquals(THIRTY_SECONDS_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(THIRTY_SECONDS_MS))
    }

    @Test
    fun bucketWidthMs_oneMinute_returns300ms() {
        // 60_000 / 200 = 300
        assertEquals(ONE_MINUTE_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(ONE_MINUTE_MS))
    }

    @Test
    fun bucketWidthMs_fiveMinutes_returns1500ms() {
        // 300_000 / 200 = 1500
        assertEquals(FIVE_MINUTES_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(FIVE_MINUTES_MS))
    }

    @Test
    fun bucketWidthMs_oneHour_returns18000ms() {
        // 3_600_000 / 200 = 18_000
        assertEquals(ONE_HOUR_BUCKET_MS, IkdSessionChartLoader.bucketWidthMs(ONE_HOUR_MS))
    }

    // ------------------------------------------------------------------ //
    // buildChartData                                                     //
    // ------------------------------------------------------------------ //

    @Test
    fun buildChartData_emptyRows_returnsEmptyChartLists() {
        val data = IkdSessionChartLoader.buildChartData(
            sessionId = "s1",
            durationMs = ONE_MINUTE_MS,
            bucketWidthMs = ONE_MINUTE_BUCKET_MS,
            timingRows = emptyList(),
            sensorRows = emptyList(),
        )

        assertTrue(data.timing.isEmpty())
        assertTrue(data.gyro.isEmpty())
        assertTrue(data.accel.isEmpty())
        assertEquals("s1", data.sessionId)
        assertEquals(ONE_MINUTE_MS, data.durationMs)
        assertEquals(ONE_MINUTE_BUCKET_MS, data.bucketWidthMs)
    }

    @Test
    fun buildChartData_sentinelOnlyTiming_yieldsNullAvgIkd() {
        // The SQL `AVG(CASE WHEN ikd_ms >= 0 THEN ikd_ms END)` already
        // collapses sentinel-only buckets to NULL. The loader must not
        // reinterpret that as zero — it has to round-trip as `null`.
        val rows = listOf(
            TimingBucketRow(
                bucketIndex = 0L,
                avgIkdMs = null,
                avgHoldMs = null,
                avgFlightMs = null,
                eventCount = 1,
            ),
        )
        val data = IkdSessionChartLoader.buildChartData(
            sessionId = "s1",
            durationMs = ONE_MINUTE_MS,
            bucketWidthMs = ONE_MINUTE_BUCKET_MS,
            timingRows = rows,
            sensorRows = emptyList(),
        )

        assertEquals(1, data.timing.size)
        assertNull(data.timing[0].avgIkdMs)
        assertEquals("0:00", data.timing[0].label)
    }

    @Test
    fun buildChartData_mixedSensors_splitsByTypeAndTakesSqrt() {
        // Bucket 0: gyro avg(x²+y²+z²) = 9.0 → magnitude = 3.0
        // Bucket 1: gyro avg = 16.0 → magnitude = 4.0
        // Bucket 0: accel avg = 4.0 → magnitude = 2.0
        val rows = listOf(
            sensorRow(bucketIndex = 0L, sensorType = "GYRO", avgSq = GYRO_BUCKET0_SQ),
            sensorRow(bucketIndex = 1L, sensorType = "GYRO", avgSq = GYRO_BUCKET1_SQ),
            sensorRow(bucketIndex = 0L, sensorType = "ACCEL", avgSq = ACCEL_BUCKET0_SQ),
        )
        val data = IkdSessionChartLoader.buildChartData(
            sessionId = "s1",
            durationMs = ONE_MINUTE_MS,
            bucketWidthMs = ONE_MINUTE_BUCKET_MS,
            timingRows = emptyList(),
            sensorRows = rows,
        )

        assertEquals(2, data.gyro.size)
        assertEquals(sqrt(GYRO_BUCKET0_SQ).toFloat(), data.gyro[0].avgMagnitude!!, FLOAT_TOLERANCE)
        assertEquals(sqrt(GYRO_BUCKET1_SQ).toFloat(), data.gyro[1].avgMagnitude!!, FLOAT_TOLERANCE)
        assertEquals(1, data.accel.size)
        assertEquals(sqrt(ACCEL_BUCKET0_SQ).toFloat(), data.accel[0].avgMagnitude!!, FLOAT_TOLERANCE)
    }

    @Test
    fun buildChartData_nullSquaredMagnitude_yieldsNullPoint() {
        // Defensive: SQL will only return NULL when every row in the bucket
        // had NULL fields. Nonetheless verify the loader propagates rather
        // than crashing on Math.sqrt(null).
        val rows = listOf(
            sensorRow(bucketIndex = 0L, sensorType = "GYRO", avgSq = null),
        )
        val data = IkdSessionChartLoader.buildChartData(
            sessionId = "s1",
            durationMs = ONE_MINUTE_MS,
            bucketWidthMs = ONE_MINUTE_BUCKET_MS,
            timingRows = emptyList(),
            sensorRows = rows,
        )

        assertEquals(1, data.gyro.size)
        assertNull(data.gyro[0].avgMagnitude)
    }

    @Test
    fun buildChartData_xAxisLabelsFormattedAsMSS() {
        // bucketWidthMs = 1500, so bucket 0 → 0:00, bucket 40 → 1:00,
        // bucket 80 → 2:00. Mirrors a 5-minute session with default
        // bucket width.
        val rows = listOf(
            timingRow(bucketIndex = 0L, avgIkd = AVG_IKD_LOW),
            timingRow(bucketIndex = LABEL_BUCKET_ONE_MIN, avgIkd = AVG_IKD_MID),
            timingRow(bucketIndex = LABEL_BUCKET_TWO_MIN, avgIkd = AVG_IKD_HIGH),
        )
        val data = IkdSessionChartLoader.buildChartData(
            sessionId = "s1",
            durationMs = FIVE_MINUTES_MS,
            bucketWidthMs = FIVE_MINUTES_BUCKET_MS,
            timingRows = rows,
            sensorRows = emptyList(),
        )

        assertEquals(listOf("0:00", "1:00", "2:00"), data.timing.map { it.label })
    }

    // ------------------------------------------------------------------ //
    // helpers + constants                                                //
    // ------------------------------------------------------------------ //

    private fun timingRow(bucketIndex: Long, avgIkd: Double?) = TimingBucketRow(
        bucketIndex = bucketIndex,
        avgIkdMs = avgIkd,
        avgHoldMs = null,
        avgFlightMs = null,
        eventCount = 1,
    )

    private fun sensorRow(
        bucketIndex: Long,
        sensorType: String,
        avgSq: Double?,
    ) = SensorBucketRow(
        bucketIndex = bucketIndex,
        sensorType = sensorType,
        avgSquaredMagnitude = avgSq,
        avgX = null,
        avgY = null,
        avgZ = null,
        sampleCount = 1,
    )

    companion object {
        private const val MIN_BUCKET_MS = 50L
        private const val TEN_SECONDS_MS = 10_000L
        private const val THIRTY_SECONDS_MS = 30_000L
        private const val THIRTY_SECONDS_BUCKET_MS = 150L
        private const val ONE_MINUTE_MS = 60_000L
        private const val ONE_MINUTE_BUCKET_MS = 300L
        private const val FIVE_MINUTES_MS = 300_000L
        private const val FIVE_MINUTES_BUCKET_MS = 1500L
        private const val ONE_HOUR_MS = 3_600_000L
        private const val ONE_HOUR_BUCKET_MS = 18_000L

        private const val GYRO_BUCKET0_SQ = 9.0
        private const val GYRO_BUCKET1_SQ = 16.0
        private const val ACCEL_BUCKET0_SQ = 4.0
        private const val FLOAT_TOLERANCE = 0.001f

        private const val LABEL_BUCKET_ONE_MIN = 40L
        private const val LABEL_BUCKET_TWO_MIN = 80L
        private const val AVG_IKD_LOW = 100.0
        private const val AVG_IKD_MID = 150.0
        private const val AVG_IKD_HIGH = 200.0
    }
}
