package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.DailyBucketRow
import org.fossify.keyboard.interfaces.DayHourBucketRow
import org.fossify.keyboard.interfaces.HourWeekdayRow
import org.fossify.keyboard.interfaces.HourlyBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9.5/9.6/9.9: pure-math tests for [IkdActivityAggregator.Companion.buildSnapshot].
 */
class IkdActivityAggregatorTest {

    @Test
    fun emptyInput_returnsAllEmptyLists() {
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.WEEK,
            daily = emptyList(),
            hourly = emptyList(),
            dayHour = emptyList(),
            circadian = emptyList(),
        )

        assertEquals(Range.WEEK, snap.range)
        assertTrue(snap.dailyBuckets.isEmpty())
        assertTrue(snap.hourlyBuckets.isEmpty())
        assertTrue(snap.dayHourCells.isEmpty())
        assertTrue(snap.circadianCells.isEmpty())
    }

    @Test
    fun singleDay_propagatesAsBucket() {
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.WEEK,
            daily = listOf(DailyBucketRow("2026-05-01", keystrokeCount = 100)),
            hourly = emptyList(),
            dayHour = emptyList(),
            circadian = emptyList(),
        )

        assertEquals(1, snap.dailyBuckets.size)
        assertEquals("2026-05-01", snap.dailyBuckets[0].day)
        assertEquals(100, snap.dailyBuckets[0].keystrokeCount)
    }

    @Test
    fun multiDay_preservesOrderingAndCounts() {
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.WEEK,
            daily = listOf(
                DailyBucketRow("2026-05-01", keystrokeCount = 10),
                DailyBucketRow("2026-05-02", keystrokeCount = 20),
                DailyBucketRow("2026-05-03", keystrokeCount = 30),
            ),
            hourly = emptyList(),
            dayHour = emptyList(),
            circadian = emptyList(),
        )

        assertEquals(3, snap.dailyBuckets.size)
        assertEquals(listOf(10, 20, 30), snap.dailyBuckets.map { it.keystrokeCount })
    }

    @Test
    fun hourlyAndDayHour_propagateUnchanged() {
        // Phase 9.6 + 9.9 fixture.
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.MONTH,
            daily = emptyList(),
            hourly = listOf(
                HourlyBucketRow(hour = 9, keystrokeCount = 50),
                HourlyBucketRow(hour = 17, keystrokeCount = 200),
            ),
            dayHour = listOf(
                DayHourBucketRow(day = "2026-05-01", hour = 9, keystrokeCount = 25),
            ),
            circadian = listOf(
                HourWeekdayRow(dow = 1, hour = 9, keystrokeCount = 25),
            ),
        )

        assertEquals(2, snap.hourlyBuckets.size)
        assertEquals(9, snap.hourlyBuckets[0].hour)
        assertEquals(50, snap.hourlyBuckets[0].keystrokeCount)
        assertEquals(1, snap.dayHourCells.size)
        assertEquals(1, snap.circadianCells.size)
    }
}
