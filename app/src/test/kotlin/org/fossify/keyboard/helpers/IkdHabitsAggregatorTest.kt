package org.fossify.keyboard.helpers

import org.fossify.keyboard.helpers.IkdAggregator.Range
import org.fossify.keyboard.helpers.IkdHabitsAggregator.HabitsBucket
import org.fossify.keyboard.helpers.IkdHabitsAggregator.StreakUnit
import org.fossify.keyboard.interfaces.HabitsBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 9.3: pure-math tests for [IkdHabitsAggregator.Companion.buildSnapshot]
 * and [IkdHabitsAggregator.Companion.computeLongestStreak]. SQL is exercised
 * on-device; this suite covers the streak helper and the per-bucket fold.
 */
class IkdHabitsAggregatorTest {

    private fun row(
        bucket: String,
        sessionCount: Int = 0,
        totalDurationMs: Long = 0L,
        avgFlightMs: Double? = null,
        correctionWeight: Int = 0,
        keystrokeCount: Int = 0,
    ) = HabitsBucketRow(
        bucket = bucket,
        sessionCount = sessionCount,
        totalDurationMs = totalDurationMs,
        avgFlightMs = avgFlightMs,
        correctionWeight = correctionWeight,
        keystrokeCount = keystrokeCount,
    )

    private fun bucket(sessionCount: Int) = HabitsBucket(
        label = "x",
        sessionCount = sessionCount,
        avgSessionDurationMs = null,
        avgFlightMs = null,
        errorRatePct = null,
    )

    // ---------- streak ----------

    @Test
    fun streak_emptyList_returnsZero() {
        assertEquals(0, IkdHabitsAggregator.computeLongestStreak(emptyList()))
    }

    @Test
    fun streak_allZeros_returnsZero() {
        val buckets = listOf(bucket(0), bucket(0), bucket(0))
        assertEquals(0, IkdHabitsAggregator.computeLongestStreak(buckets))
    }

    @Test
    fun streak_oneRunOfThreeWithGaps_returnsThree() {
        // [3,0,4,5,0,1] → runs 1, 2, 1 → max 2
        val counts = listOf(3, 0, 4, 5, 0, 1)
        val buckets = counts.map { bucket(it) }
        assertEquals(2, IkdHabitsAggregator.computeLongestStreak(buckets))
    }

    @Test
    fun streak_threeContiguous_returnsThree() {
        val buckets = listOf(bucket(1), bucket(1), bucket(1))
        assertEquals(3, IkdHabitsAggregator.computeLongestStreak(buckets))
    }

    @Test
    fun streak_singleActiveBucket_returnsOne() {
        val buckets = listOf(bucket(0), bucket(7), bucket(0))
        assertEquals(1, IkdHabitsAggregator.computeLongestStreak(buckets))
    }

    // ---------- buildSnapshot ----------

    @Test
    fun buildSnapshot_empty_returnsZeroStateWithDayUnit() {
        val snap = IkdHabitsAggregator.buildSnapshot(Range.WEEK, emptyList())

        assertEquals(0, snap.totalSessions)
        assertEquals(0L, snap.totalTypingTimeMs)
        assertNull(snap.avgSessionDurationMs)
        assertEquals(0, snap.longestStreak)
        assertEquals(StreakUnit.DAYS, snap.streakUnit)
        assertEquals(0, snap.buckets.size)
    }

    @Test
    fun buildSnapshot_allTime_emitsWeekUnit() {
        val snap = IkdHabitsAggregator.buildSnapshot(Range.ALL_TIME, emptyList())
        assertEquals(StreakUnit.WEEKS, snap.streakUnit)
    }

    @Test
    fun buildSnapshot_today_emitsHoursUnit() {
        // Phase 9.11: TODAY range carries StreakUnit.HOURS so the activity
        // can render the streak KPI as "Today" / "—" rather than a count.
        val snap = IkdHabitsAggregator.buildSnapshot(Range.TODAY, emptyList())
        assertEquals(StreakUnit.HOURS, snap.streakUnit)
    }

    @Test
    fun buildSnapshot_perBucketAvgDuration_isComputed() {
        // 3 sessions × 60 s total → 20 s avg.
        val rows = listOf(
            row(
                bucket = "2026-05-01",
                sessionCount = 3,
                totalDurationMs = 60_000L,
                avgFlightMs = 80.0,
                correctionWeight = 5,
                keystrokeCount = 100,
            ),
        )
        val snap = IkdHabitsAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(1, snap.buckets.size)
        val b = snap.buckets[0]
        assertEquals(3, b.sessionCount)
        assertNotNull(b.avgSessionDurationMs)
        assertEquals(20_000.0, b.avgSessionDurationMs!!, 0.001)
        assertEquals(80.0, b.avgFlightMs!!, 0.001)
        assertEquals(5.0, b.errorRatePct!!, 0.001) // 5/100 * 100 = 5%
    }

    @Test
    fun buildSnapshot_zeroSessionsBucket_propagatesNullDuration() {
        val rows = listOf(
            row(bucket = "2026-05-01", sessionCount = 0, totalDurationMs = 0L),
        )
        val snap = IkdHabitsAggregator.buildSnapshot(Range.WEEK, rows)
        assertEquals(1, snap.buckets.size)
        assertNull(snap.buckets[0].avgSessionDurationMs)
        assertNull(snap.buckets[0].errorRatePct)
        assertNull(snap.buckets[0].avgFlightMs)
    }

    @Test
    fun buildSnapshot_zeroKeystrokes_propagatesNullErrorRate() {
        val rows = listOf(
            row(
                bucket = "2026-05-01",
                sessionCount = 1,
                totalDurationMs = 1_000L,
                correctionWeight = 0,
                keystrokeCount = 0,
            ),
        )
        val snap = IkdHabitsAggregator.buildSnapshot(Range.WEEK, rows)
        assertNull(snap.buckets[0].errorRatePct)
    }

    @Test
    fun buildSnapshot_overallTotals_sumAcrossBuckets() {
        val rows = listOf(
            row("2026-05-01", sessionCount = 2, totalDurationMs = 60_000L),
            row("2026-05-02", sessionCount = 1, totalDurationMs = 30_000L),
            row("2026-05-03", sessionCount = 0, totalDurationMs = 0L), // gap
        )
        val snap = IkdHabitsAggregator.buildSnapshot(Range.WEEK, rows)

        assertEquals(3, snap.totalSessions)
        assertEquals(90_000L, snap.totalTypingTimeMs)
        assertEquals(30_000.0, snap.avgSessionDurationMs!!, 0.001)
        // Streak: [2 sess][1 sess][0 sess] → run of 2 → max 2.
        assertEquals(2, snap.longestStreak)
    }

    @Test
    fun buildSnapshot_noFinishedSessions_avgDurationIsNull() {
        val rows = listOf(
            row("2026-05-01", sessionCount = 1, totalDurationMs = 0L),
        )
        val snap = IkdHabitsAggregator.buildSnapshot(Range.WEEK, rows)
        // sessionCount > 0 but totalDuration == 0 (in-flight session) → null
        assertNull(snap.avgSessionDurationMs)
    }
}
