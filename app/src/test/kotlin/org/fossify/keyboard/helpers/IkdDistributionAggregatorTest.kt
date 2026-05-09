package org.fossify.keyboard.helpers

import org.fossify.keyboard.interfaces.HistogramRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9.7: pure-math tests for [IkdDistributionAggregator.Companion.buildHistogram].
 */
class IkdDistributionAggregatorTest {

    @Test
    fun emptyInput_returnsTenZeroBucketsAndZeroOutliers() {
        val hist = IkdDistributionAggregator.buildHistogram(emptyList())
        assertEquals(IkdDistributionAggregator.BUCKET_COUNT, hist.buckets.size)
        assertTrue(hist.buckets.all { it == 0 })
        assertEquals(0, hist.outlierCount)
    }

    @Test
    fun allInOneBucket_isPlacedAtThatIndex() {
        val rows = listOf(HistogramRow(bucketIndex = 3, count = 42))
        val hist = IkdDistributionAggregator.buildHistogram(rows)

        assertEquals(42, hist.buckets[3])
        // Every other bucket is zero.
        for (i in 0 until IkdDistributionAggregator.BUCKET_COUNT) {
            if (i != 3) assertEquals("bucket $i", 0, hist.buckets[i])
        }
        assertEquals(0, hist.outlierCount)
    }

    @Test
    fun evenDistribution_propagatesEachIndex() {
        val rows = (0 until IkdDistributionAggregator.BUCKET_COUNT).map { idx ->
            HistogramRow(bucketIndex = idx, count = idx + 1)
        }
        val hist = IkdDistributionAggregator.buildHistogram(rows)

        for (i in 0 until IkdDistributionAggregator.BUCKET_COUNT) {
            assertEquals(i + 1, hist.buckets[i])
        }
        assertEquals(0, hist.outlierCount)
    }

    @Test
    fun outlierIndex_landsInOutlierCount() {
        val rows = listOf(
            HistogramRow(bucketIndex = 0, count = 5),
            HistogramRow(bucketIndex = IkdDistributionAggregator.OUTLIER_BUCKET_INDEX, count = 3),
        )
        val hist = IkdDistributionAggregator.buildHistogram(rows)

        assertEquals(5, hist.buckets[0])
        assertEquals(3, hist.outlierCount)
    }

    @Test
    fun negativeIndex_isDropped() {
        // The -1 sentinel from sub-10 ms rows should be filtered SQL-side
        // already, but the helper drops it defensively.
        val rows = listOf(
            HistogramRow(bucketIndex = -1, count = 100),
            HistogramRow(bucketIndex = 0, count = 5),
        )
        val hist = IkdDistributionAggregator.buildHistogram(rows)

        assertEquals(5, hist.buckets[0])
        assertEquals(0, hist.outlierCount)
    }

    @Test
    fun bucketLabels_areTenAndStartAtTen() {
        val labels = IkdDistributionAggregator.bucketLabels()
        assertEquals(IkdDistributionAggregator.BUCKET_COUNT, labels.size)
        assertEquals("10–20 ms", labels[0])
        assertEquals("5120–10240 ms", labels[IkdDistributionAggregator.BUCKET_COUNT - 1])
    }

    @Test
    fun bucketEdges_haveExpectedSize() {
        // BUCKET_COUNT in-range buckets requires BUCKET_COUNT + 1 edges.
        assertEquals(
            IkdDistributionAggregator.BUCKET_COUNT + 1,
            IkdDistributionAggregator.BUCKET_EDGES_MS.size,
        )
    }
}
