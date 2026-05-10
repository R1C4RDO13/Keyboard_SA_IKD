package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.interfaces.DailyBucketRow
import org.fossify.keyboard.interfaces.DayHourBucketRow
import org.fossify.keyboard.interfaces.DayHourMoodBucketRow
import org.fossify.keyboard.interfaces.HourWeekdayRow
import org.fossify.keyboard.interfaces.HourlyBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun circadian_168Cells_arePreserved() {
        // 7 weekdays × 24 hours = 168 max rows.
        val rows = (0 until 7).flatMap { dow ->
            (0 until 24).map { hour ->
                HourWeekdayRow(dow = dow, hour = hour, keystrokeCount = dow * 100 + hour)
            }
        }
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.ALL_TIME,
            daily = emptyList(),
            hourly = emptyList(),
            dayHour = emptyList(),
            circadian = rows,
        )
        assertEquals(168, snap.circadianCells.size)
        // Spot check one cell: (dow=3, hour=15) → count 315.
        val sample = snap.circadianCells.first { it.dow == 3 && it.hour == 15 }
        assertEquals(315, sample.keystrokeCount)
    }

    @Test
    fun dominantMood_isPickedAsMostFrequentMoodPerCell() {
        // 2026-05-01 hour 9: mood 1 has 50, mood 4 has 10, null-mood has 5 — dominant is 1.
        // 2026-05-02 hour 14: only null-mood rows — dominant remains null.
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.WEEK,
            daily = emptyList(),
            hourly = emptyList(),
            dayHour = listOf(
                DayHourBucketRow("2026-05-01", 9, keystrokeCount = 65),
                DayHourBucketRow("2026-05-02", 14, keystrokeCount = 8),
            ),
            circadian = emptyList(),
            dayHourMood = listOf(
                DayHourMoodBucketRow("2026-05-01", 9, moodScore = 1, keystrokeCount = 50),
                DayHourMoodBucketRow("2026-05-01", 9, moodScore = 4, keystrokeCount = 10),
                DayHourMoodBucketRow("2026-05-01", 9, moodScore = null, keystrokeCount = 5),
                DayHourMoodBucketRow("2026-05-02", 14, moodScore = null, keystrokeCount = 8),
            ),
        )

        val cells = snap.dayHourCells.associateBy { it.day to it.hour }
        assertEquals(1, cells["2026-05-01" to 9]?.dominantMood)
        assertNull(cells["2026-05-02" to 14]?.dominantMood)
    }

    @Test
    fun dominantMood_tieBreaksOnLowerValencedScore() {
        // mood 2 and mood 5 both have 20 keystrokes; lower (better) wins → 2.
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.WEEK,
            daily = emptyList(),
            hourly = emptyList(),
            dayHour = listOf(DayHourBucketRow("2026-05-03", 11, keystrokeCount = 40)),
            circadian = emptyList(),
            dayHourMood = listOf(
                DayHourMoodBucketRow("2026-05-03", 11, moodScore = 5, keystrokeCount = 20),
                DayHourMoodBucketRow("2026-05-03", 11, moodScore = 2, keystrokeCount = 20),
            ),
        )

        val cell = snap.dayHourCells.first()
        assertEquals(2, cell.dominantMood)
    }

    @Test
    fun dominantMood_defaultsToNullWhenNoMoodRowsProvided() {
        // No dayHourMood passed — every cell stays untinted.
        val snap = IkdActivityAggregator.buildSnapshot(
            range = Range.WEEK,
            daily = emptyList(),
            hourly = emptyList(),
            dayHour = listOf(
                DayHourBucketRow("2026-05-04", 10, keystrokeCount = 5),
                DayHourBucketRow("2026-05-04", 11, keystrokeCount = 7),
            ),
            circadian = emptyList(),
        )

        assertTrue(snap.dayHourCells.all { it.dominantMood == null })
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
