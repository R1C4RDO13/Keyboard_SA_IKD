package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.OrientationRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9.8: pure-math tests for [IkdOrientationAggregator.Companion.buildSnapshot].
 */
class IkdOrientationAggregatorTest {

    @Test
    fun emptyInput_returnsEmptySliceList() {
        val snap = IkdOrientationAggregator.buildSnapshot(Range.WEEK, emptyList())
        assertEquals(Range.WEEK, snap.range)
        assertTrue(snap.slices.isEmpty())
    }

    @Test
    fun singleOrientation_propagatesAsOneSlice() {
        val rows = listOf(
            OrientationRow(orientation = 1, sessionCount = 5, totalDurationMs = 60_000L),
        )
        val snap = IkdOrientationAggregator.buildSnapshot(Range.MONTH, rows)
        assertEquals(1, snap.slices.size)
        assertEquals(5, snap.slices[0].sessionCount)
        assertEquals(60_000L, snap.slices[0].totalDurationMs)
    }

    @Test
    fun multipleOrientations_arePreservedInSqlOrder() {
        val rows = listOf(
            OrientationRow(orientation = -1, sessionCount = 1, totalDurationMs = 10L),
            OrientationRow(orientation = 1, sessionCount = 10, totalDurationMs = 100L),
            OrientationRow(orientation = 2, sessionCount = 3, totalDurationMs = 30L),
        )
        val snap = IkdOrientationAggregator.buildSnapshot(Range.ALL_TIME, rows)
        assertEquals(3, snap.slices.size)
        assertEquals(listOf(-1, 1, 2), snap.slices.map { it.orientation })
        assertEquals(listOf(1, 10, 3), snap.slices.map { it.sessionCount })
    }

    @Test
    fun sentinelMinusOne_isPreserved() {
        // The Phase 2 sentinel for "capture disabled" must reach the activity
        // intact — the activity decides whether to show the "Not captured"
        // legend row.
        val rows = listOf(OrientationRow(orientation = -1, sessionCount = 4, totalDurationMs = 0L))
        val snap = IkdOrientationAggregator.buildSnapshot(Range.WEEK, rows)
        assertEquals(1, snap.slices.size)
        assertEquals(-1, snap.slices[0].orientation)
        assertEquals(4, snap.slices[0].sessionCount)
    }
}
