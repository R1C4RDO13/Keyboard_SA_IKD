package org.fossify.keyboard.helpers

import org.fossify.keyboard.interfaces.SessionStatsRow
import org.fossify.keyboard.models.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-math tests for IkdSessionStatsLoader.compute. Mirrors the
 * IkdAggregatorTest pattern: the SQL itself is exercised end-to-end on-device,
 * while this suite covers the Kotlin-side derivation logic on the JVM without
 * standing up Robolectric or an instrumented test runner.
 */
class IkdSessionStatsLoaderTest {

    @Test
    fun emptySession_hasNoMetrics_butReportsDuration() {
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = SESSION_START + ONE_MINUTE_MS,
            eventCount = 0,
            sensorCount = 0,
        )
        val statsRow = statsRow(eventCount = 0, correctionCount = 0)

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertEquals(ONE_MINUTE_MS, stats.durationMs)
        assertNull(stats.wpm)
        assertNull(stats.errorRatePct)
        assertNull(stats.avgIkdMs)
        assertNull(stats.avgHoldMs)
        assertNull(stats.avgFlightMs)
    }

    @Test
    fun inFlightSession_hasNullDuration_andNullWpm() {
        // Session whose endedAt is null (still being recorded). Even though
        // there are events, we cannot compute a duration, so WPM is null.
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = null,
            eventCount = LIVE_EVENT_COUNT,
            sensorCount = 0,
        )
        val statsRow = statsRow(
            eventCount = LIVE_EVENT_COUNT,
            correctionCount = 0,
            firstTimestamp = SESSION_START,
            lastTimestamp = SESSION_START + LIVE_SAMPLE_DURATION_MS,
        )

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertNull(stats.durationMs)
        assertNull(stats.wpm)
        // Error rate still derivable from event/correction counts.
        assertEquals(0.0, stats.errorRatePct!!, 0.01)
    }

    @Test
    fun sentinelOnlyIkd_passesThroughAsNull() {
        // SQL AVG already filtered the -1 sentinel to NULL; compute() must
        // propagate that as null without dividing-by-zero or returning 0.
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = SESSION_START + ONE_MINUTE_MS,
            eventCount = 1,
            sensorCount = 0,
        )
        val statsRow = statsRow(
            eventCount = 1,
            correctionCount = 0,
            avgIkdMs = null,
            avgHoldMs = null,
            avgFlightMs = null,
        )

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertNull(stats.avgIkdMs)
        assertNull(stats.avgHoldMs)
        assertNull(stats.avgFlightMs)
        // Single event => not enough to compute WPM either.
        assertNull(stats.wpm)
    }

    @Test
    fun hundredEventsWithSevenCorrectionsOver60s_yields20WpmAnd7Pct() {
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = SESSION_START + ONE_MINUTE_MS,
            eventCount = HUNDRED,
            sensorCount = 0,
        )
        val statsRow = statsRow(
            eventCount = HUNDRED,
            correctionCount = SEVEN_CORRECTIONS,
            avgIkdMs = AVG_IKD_MS,
            avgHoldMs = AVG_HOLD_MS,
            avgFlightMs = AVG_FLIGHT_MS,
        )

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertEquals(ONE_MINUTE_MS, stats.durationMs)
        // 100 events / 5 = 20 words; 60_000ms = 1 minute => 20 WPM.
        assertEquals(EXPECTED_WPM, stats.wpm!!, 0.01)
        // 7 / 100 * 100 => 7.0 %.
        assertEquals(EXPECTED_ERROR_RATE_PCT, stats.errorRatePct!!, 0.01)
        assertEquals(AVG_IKD_MS, stats.avgIkdMs!!, 0.01)
        assertEquals(AVG_HOLD_MS, stats.avgHoldMs!!, 0.01)
        assertEquals(AVG_FLIGHT_MS, stats.avgFlightMs!!, 0.01)
    }

    // ------------------------------------------------------------------ //
    // Phase 7: AUTOCORRECT exclusion from WPM denominator                 //
    // ------------------------------------------------------------------ //

    @Test
    fun wpm_unchanged_whenSessionHasOnlyAlphaEvents() {
        // 100 ALPHA events, no autocorrects. eventCount == keystrokeCount,
        // so WPM is the same as it was pre-Phase 7.
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = SESSION_START + ONE_MINUTE_MS,
            eventCount = HUNDRED,
            sensorCount = 0,
        )
        val statsRow = statsRow(
            eventCount = HUNDRED,
            keystrokeCount = HUNDRED,
            correctionCount = 0,
        )

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertEquals(EXPECTED_WPM, stats.wpm!!, 0.01)
    }

    @Test
    fun wpm_excludesAutocorrects_whenSessionMixesAlphaAndAutocorrect() {
        // 100 ALPHA + 5 AUTOCORRECT in 60s. eventCount = 105,
        // keystrokeCount = 100 (autocorrects excluded). WPM should be
        // 20.0 (the pre-Phase 7 figure for 100 keystrokes), not 21.0.
        // Error rate uses eventCount + correctionCount: 5/105 ~= 4.76%.
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = SESSION_START + ONE_MINUTE_MS,
            eventCount = HUNDRED + AUTOCORRECT_COUNT,
            sensorCount = 0,
        )
        val statsRow = statsRow(
            eventCount = HUNDRED + AUTOCORRECT_COUNT,
            keystrokeCount = HUNDRED,
            correctionCount = AUTOCORRECT_COUNT,
            avgIkdMs = AVG_IKD_MS,
        )

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertEquals(EXPECTED_WPM, stats.wpm!!, 0.01)
        assertEquals(AUTOCORRECT_ERROR_RATE_PCT, stats.errorRatePct!!, 0.01)
    }

    @Test
    fun wpm_isNull_whenSessionHasOnlyAutocorrectEvents() {
        // 5 AUTOCORRECT events, no real keystrokes. keystrokeCount = 0
        // means computeWpm short-circuits to null even though the session
        // has events and a duration.
        val record = sessionRecord(
            startedAt = SESSION_START,
            endedAt = SESSION_START + ONE_MINUTE_MS,
            eventCount = AUTOCORRECT_COUNT,
            sensorCount = 0,
        )
        val statsRow = statsRow(
            eventCount = AUTOCORRECT_COUNT,
            keystrokeCount = 0,
            correctionCount = AUTOCORRECT_COUNT,
        )

        val stats = IkdSessionStatsLoader.compute(record, statsRow)

        assertNull(stats.wpm)
        // Error rate stays well-defined (5 / 5 = 100%) — autocorrects
        // are corrections from the error-rate perspective.
        assertEquals(100.0, stats.errorRatePct!!, 0.01)
    }

    private fun sessionRecord(
        startedAt: Long,
        endedAt: Long?,
        eventCount: Int,
        sensorCount: Int,
        deviceOrientation: Int = 0,
        locale: String = "en_US",
    ) = SessionRecord(
        sessionId = "test-session-id",
        startedAt = startedAt,
        endedAt = endedAt,
        eventCount = eventCount,
        sensorCount = sensorCount,
        deviceOrientation = deviceOrientation,
        locale = locale,
    )

    private fun statsRow(
        eventCount: Int,
        correctionCount: Int,
        keystrokeCount: Int = eventCount,
        avgIkdMs: Double? = null,
        avgHoldMs: Double? = null,
        avgFlightMs: Double? = null,
        firstTimestamp: Long? = null,
        lastTimestamp: Long? = null,
    ) = SessionStatsRow(
        eventCount = eventCount,
        keystrokeCount = keystrokeCount,
        correctionCount = correctionCount,
        avgIkdMs = avgIkdMs,
        avgHoldMs = avgHoldMs,
        avgFlightMs = avgFlightMs,
        firstTimestamp = firstTimestamp,
        lastTimestamp = lastTimestamp,
    )

    companion object {
        private const val SESSION_START = 1_700_000_000_000L
        private const val ONE_MINUTE_MS = 60_000L
        private const val LIVE_EVENT_COUNT = 50
        private const val LIVE_SAMPLE_DURATION_MS = 30_000L
        private const val HUNDRED = 100
        private const val SEVEN_CORRECTIONS = 7
        private const val EXPECTED_WPM = 20.0
        private const val EXPECTED_ERROR_RATE_PCT = 7.0
        private const val AVG_IKD_MS = 250.0
        private const val AVG_HOLD_MS = 100.0
        private const val AVG_FLIGHT_MS = 150.0

        // Phase 7: 5 autocorrects on top of 100 alphas.
        private const val AUTOCORRECT_COUNT = 5
        // 5 / 105 * 100 ~= 4.7619% (autocorrects count as corrections).
        private const val AUTOCORRECT_ERROR_RATE_PCT = 4.7619
    }
}
