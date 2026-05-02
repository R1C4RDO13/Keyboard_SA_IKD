package org.fossify.keyboard.helpers

import org.fossify.keyboard.interfaces.EventBucketRow
import org.fossify.keyboard.interfaces.SessionBucketRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-math tests for IkdAggregator.buildSnapshot. The SQL itself is exercised
 * end-to-end on-device during Phase 3.4 perf validation; this suite covers the
 * Kotlin-side zip + reducer logic so regressions there are caught on the JVM
 * without standing up Robolectric or an instrumented test runner.
 */
class IkdAggregatorTest {

    @Test
    fun emptyBuckets_returnsZeroKpis() {
        val snap = IkdAggregator.buildSnapshot(
            range = IkdAggregator.Range.WEEK,
            eventBuckets = emptyList(),
            sessionBuckets = emptyList(),
        )

        assertEquals(0, snap.totalSessions)
        assertEquals(0L, snap.totalTypingTimeMs)
        assertNull(snap.avgWpm)
        assertNull(snap.avgErrorRatePct)
        assertTrue(snap.buckets.isEmpty())
    }

    @Test
    fun zipsEventsAndSessionsByBucket() {
        val eventBuckets = listOf(
            EventBucketRow("2026-04-25", avgIkdMs = 250.0, errorRatePct = 5.0, eventCount = 100, sessionCount = 1),
            EventBucketRow("2026-04-26", avgIkdMs = 200.0, errorRatePct = 10.0, eventCount = 50, sessionCount = 1),
        )
        val sessionBuckets = listOf(
            // 100 events / 5 = 20 words; 60_000ms duration -> 20 wpm (60_000ms = 1 minute)
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
            // 50 events / 5 = 10 words; 30_000ms duration -> 20 wpm (30_000ms = 0.5 minute)
            SessionBucketRow("2026-04-26", totalDurationMs = 30_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(
            range = IkdAggregator.Range.WEEK,
            eventBuckets = eventBuckets,
            sessionBuckets = sessionBuckets,
        )

        assertEquals(2, snap.buckets.size)
        assertEquals("2026-04-25", snap.buckets[0].label)
        assertEquals(20.0, snap.buckets[0].wpm!!, 0.01)
        assertEquals(250.0, snap.buckets[0].avgIkdMs!!, 0.01)
        assertEquals(5.0, snap.buckets[0].errorRatePct!!, 0.01)

        assertEquals(20.0, snap.buckets[1].wpm!!, 0.01)
        assertEquals(200.0, snap.buckets[1].avgIkdMs!!, 0.01)
        assertEquals(10.0, snap.buckets[1].errorRatePct!!, 0.01)
    }

    @Test
    fun nullAvgIkd_propagatesAsNull() {
        // A bucket whose only events are -1 sentinels: SQL AVG() filters them
        // out and yields null.
        val eventBuckets = listOf(
            EventBucketRow("2026-04-25", avgIkdMs = null, errorRatePct = 0.0, eventCount = 5, sessionCount = 1),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 10_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(1, snap.buckets.size)
        assertNull(snap.buckets[0].avgIkdMs)
        assertNotNull(snap.buckets[0].wpm)
    }

    @Test
    fun bucketWithOnlySessions_hasNullWpm_andNoEventMetrics() {
        // A session that opened a keyboard but produced no events. We still
        // count the session in the KPI strip but leave its bucket's chart
        // points missing (no fake zeros).
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 5_000L, sessionCount = 1),
        )
        val snap = IkdAggregator.buildSnapshot(
            range = IkdAggregator.Range.WEEK,
            eventBuckets = emptyList(),
            sessionBuckets = sessionBuckets,
        )

        assertEquals(1, snap.totalSessions)
        assertEquals(5_000L, snap.totalTypingTimeMs)
        assertEquals(1, snap.buckets.size)
        assertNull(snap.buckets[0].wpm)
        assertNull(snap.buckets[0].avgIkdMs)
        assertNull(snap.buckets[0].errorRatePct)
    }

    @Test
    fun bucketWithEventsButZeroDuration_hasNullWpm() {
        // An in-flight session (no ended_at) contributes 0 to totalDurationMs
        // and therefore null WPM, even though the event stream is non-empty.
        val eventBuckets = listOf(
            EventBucketRow("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 50, sessionCount = 1),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 0L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertNull(snap.buckets[0].wpm)
        assertEquals(200.0, snap.buckets[0].avgIkdMs!!, 0.01)
    }

    @Test
    fun overallErrorRate_isWeightedByEventCount() {
        // Bucket A: 100 events, 5% errors  -> 5 corrections
        // Bucket B:  50 events, 10% errors -> 5 corrections
        // Combined: 10 corrections / 150 events ~= 6.6667%
        val eventBuckets = listOf(
            EventBucketRow("2026-04-25", avgIkdMs = 200.0, errorRatePct = 5.0, eventCount = 100, sessionCount = 1),
            EventBucketRow("2026-04-26", avgIkdMs = 200.0, errorRatePct = 10.0, eventCount = 50, sessionCount = 1),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
            SessionBucketRow("2026-04-26", totalDurationMs = 30_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(EXPECTED_OVERALL_ERROR_RATE, snap.avgErrorRatePct!!, 0.01)
    }

    @Test
    fun overallWpm_usesTotalDuration_notBucketAverages() {
        // 150 events / 5 = 30 words across 90 seconds = 1.5 minutes -> 20 wpm
        val eventBuckets = listOf(
            EventBucketRow("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 100, sessionCount = 1),
            EventBucketRow("2026-04-26", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 50, sessionCount = 1),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
            SessionBucketRow("2026-04-26", totalDurationMs = 30_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(20.0, snap.avgWpm!!, 0.01)
    }

    @Test
    fun bucketKeysSortedAlphabetically() {
        // Buckets returned out-of-order should still come back sorted; the
        // chart's X axis depends on this ordering.
        val eventBuckets = listOf(
            EventBucketRow("2026-04-26", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 10, sessionCount = 1),
            EventBucketRow("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 10, sessionCount = 1),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-26", totalDurationMs = 10_000L, sessionCount = 1),
            SessionBucketRow("2026-04-25", totalDurationMs = 10_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals("2026-04-25", snap.buckets[0].label)
        assertEquals("2026-04-26", snap.buckets[1].label)
    }

    companion object {
        // 10 corrections / 150 events -> 6.666...% overall.
        private const val EXPECTED_OVERALL_ERROR_RATE = 6.6667
    }
}
