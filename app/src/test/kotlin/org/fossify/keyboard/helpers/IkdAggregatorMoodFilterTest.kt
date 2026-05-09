package org.fossify.keyboard.helpers

import org.fossify.keyboard.interfaces.EventBucketRow
import org.fossify.keyboard.interfaces.SessionBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Phase 9.4: pure-math tests for the mood-filter scenarios. The DAO does
 * the actual filtering SQL-side; the aggregator's `buildSnapshot`
 * receives already-filtered rows. These tests verify the aggregator's
 * math stays consistent when the input has been narrowed.
 */
class IkdAggregatorMoodFilterTest {

    private fun row(
        bucket: String,
        eventCount: Int,
        sessionCount: Int = 1,
        avgIkdMs: Double? = 200.0,
        correctionCount: Int = 0,
        correctionWeight: Int = 0,
    ) = EventBucketRow(
        bucket = bucket,
        avgIkdMs = avgIkdMs,
        eventCount = eventCount,
        keystrokeCount = eventCount - correctionCount,
        correctionCount = correctionCount,
        correctionWeight = correctionWeight,
        sessionCount = sessionCount,
    )

    @Test
    fun fullFixture_returnsExpectedTotals() {
        // 100 events, 60 s, 1 session — baseline.
        val events = listOf(row("2026-05-01", eventCount = 100, sessionCount = 1))
        val sessions = listOf(SessionBucketRow("2026-05-01", totalDurationMs = 60_000L, sessionCount = 1))
        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, events, sessions)
        assertEquals(1, snap.totalSessions)
        assertEquals(60_000L, snap.totalTypingTimeMs)
    }

    @Test
    fun halfFixture_halvesTotals() {
        // Simulate the mood filter selecting half the data.
        val events = listOf(row("2026-05-01", eventCount = 50, sessionCount = 1))
        val sessions = listOf(SessionBucketRow("2026-05-01", totalDurationMs = 30_000L, sessionCount = 1))
        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, events, sessions)
        assertEquals(1, snap.totalSessions)
        assertEquals(30_000L, snap.totalTypingTimeMs)
        // WPM stays the same because both halve proportionally:
        // 50/5 = 10 words; 30s = 0.5 min → 20 wpm.
        assertNotNull(snap.avgWpm)
        assertEquals(20.0, snap.avgWpm!!, 0.001)
    }

    @Test
    fun emptyFilteredResult_returnsEmptySnapshot() {
        // Mood filter matched no sessions in range — every aggregator
        // sees an empty result; snapshot has zero KPIs.
        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, emptyList(), emptyList())
        assertEquals(0, snap.totalSessions)
        assertEquals(0L, snap.totalTypingTimeMs)
    }
}
