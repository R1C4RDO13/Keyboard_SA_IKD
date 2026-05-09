package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.DayQualityRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9.10: pure-math tests for [IkdQualityAggregator.Companion.buildSnapshot].
 */
class IkdQualityAggregatorTest {

    @Test
    fun emptyInput_returnsEmptyPoints() {
        val snap = IkdQualityAggregator.buildSnapshot(Range.WEEK, emptyList())
        assertTrue(snap.points.isEmpty())
    }

    @Test
    fun singleDay_dayIndexIsZero() {
        val rows = listOf(
            DayQualityRow(day = "2026-05-09", backspaceCount = 5, autocorrectionCount = 2),
        )
        val snap = IkdQualityAggregator.buildSnapshot(Range.WEEK, rows)
        assertEquals(1, snap.points.size)
        assertEquals(0, snap.points[0].dayIndex)
        assertEquals(5, snap.points[0].backspaceCount)
        assertEquals(2, snap.points[0].autocorrectionCount)
    }

    @Test
    fun multiDay_dayIndexAssignedNewestFirst() {
        // SQL returns newest-first (DESC). Aggregator's mapIndexed assigns
        // index 0 to first row (newest).
        val rows = listOf(
            DayQualityRow(day = "2026-05-09", backspaceCount = 1, autocorrectionCount = 1),
            DayQualityRow(day = "2026-05-08", backspaceCount = 2, autocorrectionCount = 2),
            DayQualityRow(day = "2026-05-07", backspaceCount = 3, autocorrectionCount = 3),
        )
        val snap = IkdQualityAggregator.buildSnapshot(Range.WEEK, rows)
        assertEquals(3, snap.points.size)
        assertEquals(0, snap.points[0].dayIndex)
        assertEquals("2026-05-09", snap.points[0].day)
        assertEquals(1, snap.points[1].dayIndex)
        assertEquals("2026-05-08", snap.points[1].day)
        assertEquals(2, snap.points[2].dayIndex)
        assertEquals("2026-05-07", snap.points[2].day)
    }

    @Test
    fun zeroCountsDay_propagates() {
        // A day where every event was a non-error keystroke still shows up
        // as a (0,0) point — the activity may filter those out for
        // visibility, but the aggregator does not.
        val rows = listOf(
            DayQualityRow(day = "2026-05-09", backspaceCount = 0, autocorrectionCount = 0),
        )
        val snap = IkdQualityAggregator.buildSnapshot(Range.WEEK, rows)
        assertEquals(1, snap.points.size)
        assertEquals(0, snap.points[0].backspaceCount)
        assertEquals(0, snap.points[0].autocorrectionCount)
    }
}
