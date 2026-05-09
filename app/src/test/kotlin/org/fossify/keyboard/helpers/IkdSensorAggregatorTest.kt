package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.helpers.IkdSensorAggregator.Companion.SENSOR_TYPE_ACCEL
import org.fossify.keyboard.helpers.IkdSensorAggregator.Companion.SENSOR_TYPE_GYRO
import org.fossify.keyboard.interfaces.SensorBucketAggregateRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Phase 9.2: pure-math tests for [IkdSensorAggregator.Companion.buildSnapshot].
 * SQL is exercised on-device; this suite covers the partition-by-sensor-type
 * fold and the SQL-side `AVG(x*x+y*y+z*z)` → Kotlin-side `sqrt` split.
 */
class IkdSensorAggregatorTest {

    private fun row(
        bucket: String,
        sensorType: String,
        avgSquaredMagnitude: Double?,
        sampleCount: Int = 1,
    ) = SensorBucketAggregateRow(
        bucket = bucket,
        sensorType = sensorType,
        avgSquaredMagnitude = avgSquaredMagnitude,
        sampleCount = sampleCount,
    )

    @Test
    fun emptyInput_returnsEmptyBuckets() {
        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, emptyList())

        assertEquals(Range.WEEK, snap.range)
        assertTrue(snap.buckets.isEmpty())
    }

    @Test
    fun mixedGyroAndAccel_areMergedPerBucket() {
        val rows = listOf(
            row("2026-05-01", SENSOR_TYPE_ACCEL, avgSquaredMagnitude = 100.0, sampleCount = 5),
            row("2026-05-01", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 25.0, sampleCount = 5),
            row("2026-05-02", SENSOR_TYPE_ACCEL, avgSquaredMagnitude = 9.0, sampleCount = 3),
            row("2026-05-02", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 1.0, sampleCount = 3),
        )

        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(2, snap.buckets.size)
        val day1 = snap.buckets[0]
        assertEquals("2026-05-01", day1.label)
        assertEquals(sqrt(25.0), day1.gyroMag!!, EPSILON)
        assertEquals(sqrt(100.0), day1.accelMag!!, EPSILON)

        val day2 = snap.buckets[1]
        assertEquals("2026-05-02", day2.label)
        assertEquals(sqrt(1.0), day2.gyroMag!!, EPSILON)
        assertEquals(sqrt(9.0), day2.accelMag!!, EPSILON)
    }

    @Test
    fun onlyGyroSession_propagatesNullAccel() {
        val rows = listOf(
            row("2026-05-01", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 4.0),
        )

        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.buckets.size)
        val bucket = snap.buckets[0]
        assertNotNull(bucket.gyroMag)
        assertEquals(sqrt(4.0), bucket.gyroMag!!, EPSILON)
        assertNull(bucket.accelMag)
    }

    @Test
    fun onlyAccelSession_propagatesNullGyro() {
        val rows = listOf(
            row("2026-05-01", SENSOR_TYPE_ACCEL, avgSquaredMagnitude = 16.0),
        )

        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.buckets.size)
        val bucket = snap.buckets[0]
        assertNull(bucket.gyroMag)
        assertEquals(sqrt(16.0), bucket.accelMag!!, EPSILON)
    }

    @Test
    fun nullSquaredMagnitude_propagatesAsNull() {
        // Defensive: SQLite shouldn't really hand back null when the row exists,
        // but if AVG(...) ever returns null on an empty window it must not
        // crash and must propagate as a missing chart point.
        val rows = listOf(
            row("2026-05-01", SENSOR_TYPE_GYRO, avgSquaredMagnitude = null),
            row("2026-05-01", SENSOR_TYPE_ACCEL, avgSquaredMagnitude = 4.0),
        )

        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.buckets.size)
        assertNull(snap.buckets[0].gyroMag)
        assertEquals(sqrt(4.0), snap.buckets[0].accelMag!!, EPSILON)
    }

    @Test
    fun unknownSensorType_isIgnored() {
        // Future-proofing: a foreign sensor type slipping into the DB should
        // not pollute the gyro / accel pair.
        val rows = listOf(
            row("2026-05-01", "MAG", avgSquaredMagnitude = 99.0),
            row("2026-05-01", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 4.0),
        )

        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.buckets.size)
        assertEquals(sqrt(4.0), snap.buckets[0].gyroMag!!, EPSILON)
        assertNull(snap.buckets[0].accelMag)
    }

    @Test
    fun bucketsAreSortedByLabelAscending() {
        // SQL orders by sensor_type, bucket — but the snapshot must end up
        // bucket-ascending for chart X-axis alignment with the IKD charts.
        val rows = listOf(
            row("2026-05-03", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 1.0),
            row("2026-05-01", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 1.0),
            row("2026-05-02", SENSOR_TYPE_GYRO, avgSquaredMagnitude = 1.0),
        )

        val snap = IkdSensorAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(listOf("2026-05-01", "2026-05-02", "2026-05-03"), snap.buckets.map { it.label })
    }

    companion object {
        private const val EPSILON = 0.0001
    }
}
