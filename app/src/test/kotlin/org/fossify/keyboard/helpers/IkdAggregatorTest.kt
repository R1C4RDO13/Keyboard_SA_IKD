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

    /**
     * Helper that defaults `keystrokeCount` to `eventCount` so tests that
     * predate the AUTOCORRECT-exclusion split don't have to spell out the
     * redundant value on every row.
     *
     * `errorRatePct` is computed in Kotlin from `correctionWeight` over
     * the productive-keystroke denominator (`eventCount - correctionCount`).
     * The helper takes the legacy fixture's `errorRatePct` and reverse-
     * engineers a matching `correctionWeight` and `correctionCount` so
     * existing tests stay numerically valid: weight = round(rate * eventCount
     * / 100) is the historic correction count, and `correctionCount`
     * defaults to that same number (each unit of weight = one BS row).
     * New tests pass `correctionWeight` and `correctionCount` directly.
     */
    private fun eventBucket(
        bucket: String,
        avgIkdMs: Double?,
        errorRatePct: Double = 0.0,
        eventCount: Int,
        sessionCount: Int = 1,
        keystrokeCount: Int = eventCount,
        correctionWeight: Int = ((errorRatePct * eventCount + PCT_HALF) / PCT).toInt(),
        correctionCount: Int = correctionWeight,
    ) = EventBucketRow(
        bucket = bucket,
        avgIkdMs = avgIkdMs,
        eventCount = eventCount,
        keystrokeCount = keystrokeCount,
        correctionCount = correctionCount,
        correctionWeight = correctionWeight,
        sessionCount = sessionCount,
    )

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
        // Bucket A: 100 events of which 5 are corrections (weight 5).
        //   productive = 95; error rate = 5/95 ≈ 5.263%.
        // Bucket B: 50 events of which 5 are corrections (weight 5).
        //   productive = 45; error rate = 5/45 ≈ 11.111%.
        val eventBuckets = listOf(
            eventBucket("2026-04-25", avgIkdMs = 250.0, errorRatePct = 5.0, eventCount = 100),
            eventBucket("2026-04-26", avgIkdMs = 200.0, errorRatePct = 10.0, eventCount = 50),
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
        assertEquals(5.2631, snap.buckets[0].errorRatePct!!, 0.01)

        assertEquals(20.0, snap.buckets[1].wpm!!, 0.01)
        assertEquals(200.0, snap.buckets[1].avgIkdMs!!, 0.01)
        assertEquals(11.1111, snap.buckets[1].errorRatePct!!, 0.01)
    }

    @Test
    fun nullAvgIkd_propagatesAsNull() {
        // A bucket whose only events are -1 sentinels: SQL AVG() filters them
        // out and yields null.
        val eventBuckets = listOf(
            eventBucket("2026-04-25", avgIkdMs = null, errorRatePct = 0.0, eventCount = 5),
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
            eventBucket("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 50),
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
            eventBucket("2026-04-25", avgIkdMs = 200.0, errorRatePct = 5.0, eventCount = 100),
            eventBucket("2026-04-26", avgIkdMs = 200.0, errorRatePct = 10.0, eventCount = 50),
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
            eventBucket("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 100),
            eventBucket("2026-04-26", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 50),
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
            eventBucket("2026-04-26", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 10),
            eventBucket("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 10),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-26", totalDurationMs = 10_000L, sessionCount = 1),
            SessionBucketRow("2026-04-25", totalDurationMs = 10_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals("2026-04-25", snap.buckets[0].label)
        assertEquals("2026-04-26", snap.buckets[1].label)
    }

    // ------------------------------------------------------------------ //
    // Phase 7: AUTOCORRECT exclusion from WPM denominator                 //
    // ------------------------------------------------------------------ //

    @Test
    fun wpm_unchanged_whenSessionHasOnlyKeystrokes() {
        // 100 ALPHA events / 5 = 20 words; 60_000 ms -> 20 wpm.
        // No autocorrects -> keystrokeCount == eventCount; nothing to exclude.
        val eventBuckets = listOf(
            eventBucket("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 100),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(20.0, snap.buckets[0].wpm!!, 0.01)
        assertEquals(20.0, snap.avgWpm!!, 0.01)
    }

    @Test
    fun wpm_excludesAutocorrects_whenSessionMixesAlphaAndAutocorrect() {
        // 100 ALPHA + 5 AUTOCORRECT over 60s. eventCount = 105, but
        // keystrokeCount = 100 (autocorrects excluded). WPM matches the
        // keystroke-only formula (20 wpm), not 21 wpm.
        //
        // Phase 7.1: error rate is now `100 * correctionWeight / keystrokeCount`.
        // Under the legacy backfill (each AUTOCORRECT carries weight 1),
        // correctionWeight = 5 over keystrokeCount = 100 → 5.0%. The pre-7.1
        // formula reported 4.76% (5 corrections / 105 events); the Phase 7
        // plan accepted that small denominator inconsistency, and Phase 7.1
        // resolves it by aligning the denominator with WPM's.
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = 200.0,
                eventCount = 105,
                keystrokeCount = 100,
                correctionWeight = AUTOCORRECT_LEGACY_WEIGHT,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(20.0, snap.buckets[0].wpm!!, 0.01)
        // Overall WPM at the snapshot level uses the same exclusion.
        assertEquals(20.0, snap.avgWpm!!, 0.01)
        // Phase 7.1 weighted error rate: 5 / 100 = 5.0%.
        assertEquals(AUTOCORRECT_LEGACY_ERROR_RATE_PCT, snap.avgErrorRatePct!!, 0.01)
    }

    @Test
    fun wpm_isNull_whenSessionHasOnlyAutocorrects() {
        // 5 AUTOCORRECT events, no real keystrokes. keystrokeCount = 0
        // means computeWpm returns null (no typing happened).
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = 500.0,
                errorRatePct = 100.0,
                eventCount = 5,
                keystrokeCount = 0,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertNull(snap.buckets[0].wpm)
        assertNull(snap.avgWpm)
    }

    @Test
    fun wpm_countsEmojiTowardKeystrokes() {
        // 90 ALPHA + 10 EMOJI = 100 keystrokes (no autocorrect). EMOJI rows
        // are intentional input and stay in the denominator.
        val eventBuckets = listOf(
            eventBucket("2026-04-25", avgIkdMs = 200.0, errorRatePct = 0.0, eventCount = 100),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(20.0, snap.buckets[0].wpm!!, 0.01)
    }

    // ------------------------------------------------------------------ //
    // Phase 7.1: weighted error-rate formula                              //
    // ------------------------------------------------------------------ //

    @Test
    fun errorRate_isWeightedByCorrectionWeight_overKeystrokeCount() {
        // The canonical case: typing `ocasdasda<space>` and the system
        // replaces it with `october`. 9 ALPHA + 1 SPACE + 1
        // AUTOCORRECT(weight=9) → eventCount = 11, correctionCount = 1
        // (the single AUTOCORRECT row), correctionWeight = 9. Productive
        // keystrokes = 11 - 1 = 10. Expected error rate: 90%.
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = 200.0,
                eventCount = OCASDASDA_EVENT_COUNT,
                keystrokeCount = OCASDASDA_KEYSTROKES,
                correctionCount = 1,
                correctionWeight = OCASDASDA_WEIGHT,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(OCASDASDA_ERROR_RATE_PCT, snap.buckets[0].errorRatePct!!, 0.01)
        assertEquals(OCASDASDA_ERROR_RATE_PCT, snap.avgErrorRatePct!!, 0.01)
    }

    @Test
    fun errorRate_isOneHundredPct_whenAllTypedCharsBackspaced() {
        // User-reported case: typing `hello world` (11 productive keystrokes)
        // and then pressing BACKSPACE 11 times to delete every character.
        // eventCount = 22, correctionCount = 11, correctionWeight = 11
        // (each single-char BS contributes weight 1). Productive = 22-11 = 11.
        // Expected error rate: 11/11 = 100%.
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = 200.0,
                eventCount = HELLO_WORLD_EVENTS,
                keystrokeCount = HELLO_WORLD_EVENTS,
                correctionCount = HELLO_WORLD_BS,
                correctionWeight = HELLO_WORLD_BS,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 30_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(HUNDRED_PCT, snap.buckets[0].errorRatePct!!, 0.01)
        assertEquals(HUNDRED_PCT, snap.avgErrorRatePct!!, 0.01)
    }

    @Test
    fun errorRate_isOneHundredPct_whenSelectionDeletedInSingleBackspace() {
        // Typing `hello world` and then pressing BACKSPACE once on a
        // selection of all 11 chars. eventCount = 12, correctionCount = 1,
        // correctionWeight = 11 (the single BS deleted 11 chars). Productive
        // = 12-1 = 11. Expected: 11/11 = 100%.
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = 200.0,
                eventCount = HELLO_WORLD_PRODUCTIVE + 1,
                keystrokeCount = HELLO_WORLD_PRODUCTIVE + 1,
                correctionCount = 1,
                correctionWeight = HELLO_WORLD_PRODUCTIVE,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 30_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(HUNDRED_PCT, snap.buckets[0].errorRatePct!!, 0.01)
    }

    @Test
    fun errorRate_isNull_whenKeystrokeCountIsZero() {
        // Hypothetical AUTOCORRECT-only bucket. The keyboard cannot produce
        // this in real life (every autocorrect must be preceded by a
        // keystroke), but the formula must still be defensively null-safe.
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = null,
                eventCount = 5,
                keystrokeCount = 0,
                correctionWeight = 5,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertNull(snap.buckets[0].errorRatePct)
        assertNull(snap.avgErrorRatePct)
    }

    @Test
    fun errorRate_legacyBackfillSession_remainsConsistent() {
        // Pre-7.1 sessions migrate with weight 1 on every is_correction = 1
        // row. A session of 10 ALPHA + 1 BACKSPACE (legacy weight 1) + 1
        // AUTOCORRECT (legacy backfilled to weight 1):
        //   eventCount = 12, correctionCount = 2, correctionWeight = 2
        // Productive = 12-2 = 10. Error rate = 2/10 = 20%.
        val eventBuckets = listOf(
            eventBucket(
                bucket = "2026-04-25",
                avgIkdMs = 200.0,
                eventCount = LEGACY_EVENT_COUNT,
                keystrokeCount = LEGACY_KEYSTROKES,
                correctionCount = LEGACY_WEIGHT,
                correctionWeight = LEGACY_WEIGHT,
            ),
        )
        val sessionBuckets = listOf(
            SessionBucketRow("2026-04-25", totalDurationMs = 60_000L, sessionCount = 1),
        )

        val snap = IkdAggregator.buildSnapshot(IkdAggregator.Range.WEEK, eventBuckets, sessionBuckets)

        assertEquals(LEGACY_ERROR_RATE_PCT, snap.buckets[0].errorRatePct!!, 0.01)
    }

    companion object {
        // Productive-denominator formula: 10 weight units across
        // (100-5) + (50-5) = 140 productive keystrokes ≈ 7.143%.
        private const val EXPECTED_OVERALL_ERROR_RATE = 7.1428

        // Phase 7.1: 5 weight units / 100 keystrokes = 5.0% under the
        // weighted formula (was 4.76% pre-7.1 because of the 105-event
        // denominator).
        private const val AUTOCORRECT_LEGACY_WEIGHT = 5
        private const val AUTOCORRECT_LEGACY_ERROR_RATE_PCT = 5.0

        // Phase 7.1: the canonical `ocasdasda` case from Phase7.1_Plan.md.
        // 9 ALPHA + 1 SPACE + 1 AUTOCORRECT(weight=9) → 90% weighted error
        // rate (9 weight units / 10 keystrokes). The pre-7.1 formula would
        // have reported 9.1% (1 correction / 11 events) — the magnitude is
        // restored by the new formula.
        private const val OCASDASDA_KEYSTROKES = 10
        private const val OCASDASDA_EVENT_COUNT = 11
        private const val OCASDASDA_WEIGHT = 9
        private const val OCASDASDA_ERROR_RATE_PCT = 90.0

        // Legacy backfill fixture. Mirrors a session captured before the
        // v2 → v3 migration, where each is_correction = 1 row backfilled
        // to weight = 1.
        private const val LEGACY_EVENT_COUNT = 12
        private const val LEGACY_KEYSTROKES = 11
        private const val LEGACY_WEIGHT = 2
        // 2 weight / (12 - 2 corrections) = 2/10 = 20%.
        private const val LEGACY_ERROR_RATE_PCT = 20.0

        // User-reported case: typing `hello world` (11 chars) and pressing
        // BACKSPACE 11 times to delete every char. eventCount = 22,
        // BACKSPACE count = 11, correction count = 11, weight = 11.
        private const val HELLO_WORLD_EVENTS = 22
        private const val HELLO_WORLD_BS = 11
        private const val HELLO_WORLD_PRODUCTIVE = 11
        private const val HUNDRED_PCT = 100.0

        // Helper for `eventBucket(...)` to translate a legacy `errorRatePct`
        // into the equivalent `correctionWeight` so existing fixtures stay
        // numerically valid without restating every literal. Rounds to the
        // nearest integer (`+ PCT_HALF` then `/ PCT`).
        private const val PCT = 100.0
        private const val PCT_HALF = 50.0
    }
}
