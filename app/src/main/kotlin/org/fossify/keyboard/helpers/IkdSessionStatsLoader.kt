package org.fossify.keyboard.helpers

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.databases.IkdDatabase
import org.fossify.keyboard.interfaces.SessionStatsRow
import org.fossify.keyboard.models.SessionRecord
import kotlin.system.measureTimeMillis

/**
 * Read-only per-session loader. Sibling of [IkdAggregator] — kept separate so
 * that the dashboard's bucketed read surface stays unchanged when per-session
 * stats are added or refined.
 *
 * Threading: [load] runs entirely on `Dispatchers.IO`. Callers may invoke it
 * from any coroutine context.
 */
class IkdSessionStatsLoader(private val db: IkdDatabase) {

    /**
     * Snapshot of one session's metadata + derived metrics. All metrics are
     * nullable so the UI can render `"—"` for genuinely missing values
     * (in-flight session, no events, sentinel-only IKD/hold/flight rows).
     */
    data class SessionStats(
        val record: SessionRecord,
        val durationMs: Long?,
        val wpm: Double?,
        val errorRatePct: Double?,
        val avgIkdMs: Double?,
        val avgHoldMs: Double?,
        val avgFlightMs: Double?,
    )

    /**
     * Loads one session's stats. Returns `null` when the session row is not
     * found (e.g. it was deleted between the list opening and the user
     * tapping into it).
     */
    suspend fun load(sessionId: String): SessionStats? = withContext(Dispatchers.IO) {
        var result: SessionStats? = null
        val durationMs = measureTimeMillis {
            val record = db.SessionDao().getSession(sessionId) ?: return@measureTimeMillis
            val statsRow = db.IkdEventDao().getSessionStats(sessionId)
            result = Companion.compute(record, statsRow)
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "load(${sessionId.take(LOG_ID_LEN)}) took ${durationMs}ms")
        }
        result
    }

    companion object {
        private const val LOG_TAG = "IkdSessionStatsLoader"
        private const val LOG_ID_LEN = 8

        // WPM and percentage scaling — same convention as IkdAggregator.
        private const val WORD_KEYSTROKES = 5.0
        private const val MS_PER_MINUTE = 60_000.0
        private const val PCT_MULTIPLIER = 100.0

        /**
         * Pure derivation. Lives on the companion so it can be unit-tested
         * without standing up a Room DB.
         *
         * - `durationMs` is `null` when the session is in-flight (no `endedAt`).
         * - `wpm` is `null` when there are not enough events (≤ 1) or the
         *   duration is unknown / non-positive.
         * - `errorRatePct` is `null` when there are no events, otherwise it's
         *   `100 * correctionCount / eventCount`.
         */
        internal fun compute(record: SessionRecord, statsRow: SessionStatsRow): SessionStats {
            val durationMs = record.endedAt?.let { it - record.startedAt }
            val wpm = computeWpm(statsRow.eventCount, durationMs)
            val errorRatePct = computeErrorRate(statsRow.eventCount, statsRow.correctionCount)
            return SessionStats(
                record = record,
                durationMs = durationMs,
                wpm = wpm,
                errorRatePct = errorRatePct,
                avgIkdMs = statsRow.avgIkdMs,
                avgHoldMs = statsRow.avgHoldMs,
                avgFlightMs = statsRow.avgFlightMs,
            )
        }

        private fun computeWpm(eventCount: Int, durationMs: Long?): Double? {
            if (eventCount <= 1) return null
            if (durationMs == null || durationMs <= 0L) return null
            // WPM convention: 5 keystrokes per word.
            return eventCount.toDouble() / WORD_KEYSTROKES *
                MS_PER_MINUTE / durationMs.toDouble()
        }

        private fun computeErrorRate(eventCount: Int, correctionCount: Int): Double? {
            if (eventCount <= 0) return null
            return PCT_MULTIPLIER * correctionCount / eventCount
        }
    }
}
