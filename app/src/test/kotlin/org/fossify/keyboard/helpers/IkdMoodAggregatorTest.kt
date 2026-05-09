package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.MoodBucketRow
import org.fossify.keyboard.interfaces.MoodDistributionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math tests for [IkdMoodAggregator.buildSnapshot]. The SQL is
 * exercised on-device by the Phase 8 manual smoke; this suite covers the
 * Kotlin-side zip + counts + average logic so regressions there are
 * caught on the JVM without standing up Room.
 */
class IkdMoodAggregatorTest {

    @Test
    fun emptyInputs_yieldZeroTotalAndNullAverage() {
        val snap = IkdMoodAggregator.buildSnapshot(
            range = Range.WEEK,
            buckets = emptyList(),
            distribution = emptyList(),
        )

        assertEquals(0, snap.total)
        assertNull(snap.averageScore)
        assertTrue(snap.buckets.isEmpty())
        // The counts map always has six entries — even when there's no data.
        assertEquals(6, snap.counts.size)
        snap.counts.values.forEach { assertEquals(0, it) }
    }

    @Test
    fun mixedWeekProducesAverageOverThreeScores() {
        // Three days, one entry each: scores 1 / 3 / 6.
        val buckets = listOf(
            MoodBucketRow(bucket = "2026-05-04", avgScore = 1.0, entryCount = 1),
            MoodBucketRow(bucket = "2026-05-06", avgScore = 3.0, entryCount = 1),
            MoodBucketRow(bucket = "2026-05-08", avgScore = 6.0, entryCount = 1),
        )
        val distribution = listOf(
            MoodDistributionRow(score = 1, entryCount = 1),
            MoodDistributionRow(score = 3, entryCount = 1),
            MoodDistributionRow(score = 6, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildSnapshot(Range.WEEK, buckets, distribution)

        assertEquals(3, snap.total)
        assertNotNull(snap.averageScore)
        assertEquals(EXPECTED_AVG_1_3_6, snap.averageScore!!, FLOAT_EPS)
        assertEquals(3, snap.buckets.size)
        // The three populated scores have count 1; the missing three have 0.
        assertEquals(1, snap.counts[1])
        assertEquals(0, snap.counts[2])
        assertEquals(1, snap.counts[3])
        assertEquals(0, snap.counts[4])
        assertEquals(0, snap.counts[5])
        assertEquals(1, snap.counts[6])
    }

    @Test
    fun bucketsPropagateAvgScoreVerbatim() {
        val buckets = listOf(
            MoodBucketRow(bucket = "2026-05-04", avgScore = 2.5, entryCount = 4),
            MoodBucketRow(bucket = "2026-05-05", avgScore = 4.5, entryCount = 2),
        )
        val distribution = listOf(
            MoodDistributionRow(score = 2, entryCount = 2),
            MoodDistributionRow(score = 3, entryCount = 2),
            MoodDistributionRow(score = 4, entryCount = 1),
            MoodDistributionRow(score = 5, entryCount = 1),
        )

        val snap = IkdMoodAggregator.buildSnapshot(Range.MONTH, buckets, distribution)

        assertEquals(2, snap.buckets.size)
        // Buckets keep `avgScore` from the row exactly — they describe a
        // time bucket, not the overall average.
        assertEquals(2.5, snap.buckets[0].avgScore!!, FLOAT_EPS)
        assertEquals(4.5, snap.buckets[1].avgScore!!, FLOAT_EPS)
    }

    @Test
    fun emptyBucketEntryCountPropagatesToNullAvg() {
        // Row with `entryCount = 0` should never happen in practice (the
        // SQL skips empty buckets entirely), but if a future query change
        // ever produces one, treat it as a missing chart point.
        val buckets = listOf(
            MoodBucketRow(bucket = "2026-05-04", avgScore = 0.0, entryCount = 0),
            MoodBucketRow(bucket = "2026-05-05", avgScore = 2.0, entryCount = 5),
        )
        val distribution = listOf(MoodDistributionRow(score = 2, entryCount = 5))

        val snap = IkdMoodAggregator.buildSnapshot(Range.MONTH, buckets, distribution)

        assertNull(snap.buckets[0].avgScore)
        assertEquals(2.0, snap.buckets[1].avgScore!!, FLOAT_EPS)
        assertEquals(5, snap.total)
    }

    @Test
    fun outOfRangeScoresAreIgnoredFromCountsAndAverage() {
        // A corrupt row with score = 7 (or 0 / -1 / 99) should neither
        // bump a count nor pull the overall average. The Distribution
        // panel iterates 1..6 so the bad row simply never appears.
        val buckets = listOf(
            MoodBucketRow(bucket = "2026-05-04", avgScore = 1.0, entryCount = 2),
        )
        val distribution = listOf(
            MoodDistributionRow(score = 1, entryCount = 2),
            MoodDistributionRow(score = 7, entryCount = 99),
        )

        val snap = IkdMoodAggregator.buildSnapshot(Range.WEEK, buckets, distribution)

        assertEquals(2, snap.total)
        assertEquals(1.0, snap.averageScore!!, FLOAT_EPS)
        assertEquals(2, snap.counts[1])
        assertNull(snap.counts[7])
    }

    @Test
    fun displayOrderIsPreservedInCountsMap() {
        val distribution = listOf(
            MoodDistributionRow(score = 6, entryCount = 1),
            MoodDistributionRow(score = 1, entryCount = 5),
        )

        val snap = IkdMoodAggregator.buildSnapshot(Range.ALL_TIME, emptyList(), distribution)

        // The map is built from `MoodEmoji.displayOrder()` so iteration
        // order is best → worst regardless of how the rows arrived.
        val keys = snap.counts.keys.toList()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), keys)
    }

    @Test
    fun allTimeWithSingleScoreHasThatScoreAsAverage() {
        // Edge case: one user only ever taps Sadness. The average is 4
        // exactly, the chart line is flat, the panel has one bar.
        val buckets = listOf(
            MoodBucketRow(bucket = "2026-W19", avgScore = 4.0, entryCount = 5),
        )
        val distribution = listOf(MoodDistributionRow(score = 4, entryCount = 5))

        val snap = IkdMoodAggregator.buildSnapshot(Range.ALL_TIME, buckets, distribution)

        assertEquals(5, snap.total)
        assertEquals(4.0, snap.averageScore!!, FLOAT_EPS)
        assertEquals(5, snap.counts[4])
    }

    companion object {
        private const val FLOAT_EPS = 0.0001
        // 1 + 3 + 6 over 3 entries = 10/3 ≈ 3.3333…
        private const val EXPECTED_AVG_1_3_6 = 10.0 / 3.0
    }
}
