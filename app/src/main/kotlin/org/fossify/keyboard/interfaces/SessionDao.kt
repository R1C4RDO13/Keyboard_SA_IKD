package org.fossify.keyboard.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.fossify.keyboard.models.SessionRecord

@Dao
interface SessionDao {
    @Insert
    fun insertSession(session: SessionRecord)

    @Update
    fun updateSession(session: SessionRecord)

    @Query("SELECT * FROM sessions ORDER BY started_at DESC")
    fun getAllSessions(): List<SessionRecord>

    @Query("SELECT * FROM sessions WHERE started_at >= :fromMs ORDER BY started_at DESC")
    fun getSessionsSince(fromMs: Long): List<SessionRecord>

    @Query("SELECT * FROM sessions WHERE session_id = :id")
    fun getSession(id: String): SessionRecord?

    @Query("DELETE FROM sessions WHERE session_id = :id")
    fun deleteSession(id: String)

    @Query("DELETE FROM sessions WHERE started_at < :cutoff")
    fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM sessions")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM sessions")
    fun count(): Int

    /**
     * Bucketed session aggregation. `totalDurationMs` only sums sessions whose
     * `ended_at` is set; in-flight sessions still bump `sessionCount`.
     *
     * @param bucketFormat strftime pattern ("%Y-%m-%d" / "%Y-%W" / "%Y-%m").
     * @param fromMs inclusive lower bound on `started_at` (epoch millis).
     * @param toMs exclusive upper bound on `started_at` (epoch millis).
     */
    @Query(
        """
        SELECT
            strftime(:bucketFormat, started_at / 1000, 'unixepoch', 'localtime') AS bucket,
            SUM(CASE WHEN ended_at IS NOT NULL THEN ended_at - started_at ELSE 0 END) AS totalDurationMs,
            COUNT(*) AS sessionCount
        FROM sessions
        WHERE started_at >= :fromMs AND started_at < :toMs
          AND session_id IN (
              SELECT session_id FROM ikd_events
              GROUP BY session_id
              HAVING SUM(CASE WHEN event_category NOT IN ('AUTOCORRECT', 'BACKSPACE') THEN 1 ELSE 0 END) >= :minKeystrokes
          )
        GROUP BY bucket
        ORDER BY bucket
        """
    )
    fun getSessionBuckets(
        bucketFormat: String,
        fromMs: Long,
        toMs: Long,
        minKeystrokes: Int,
    ): List<SessionBucketRow>

    /**
     * Phase 9.4: mood-filtered counterpart to [getSessionBuckets]. Two-query
     * pattern preferred for `sessions` over an `IS NULL` parameter —
     * Phase 9 orchestrator Decision #14.
     */
    @Query(
        """
        SELECT
            strftime(:bucketFormat, started_at / 1000, 'unixepoch', 'localtime') AS bucket,
            SUM(CASE WHEN ended_at IS NOT NULL THEN ended_at - started_at ELSE 0 END) AS totalDurationMs,
            COUNT(*) AS sessionCount
        FROM sessions
        WHERE started_at >= :fromMs
          AND started_at <  :toMs
          AND session_id IN (SELECT session_id FROM mood_entries WHERE mood_score = :moodScore)
          AND session_id IN (
              SELECT session_id FROM ikd_events
              GROUP BY session_id
              HAVING SUM(CASE WHEN event_category NOT IN ('AUTOCORRECT', 'BACKSPACE') THEN 1 ELSE 0 END) >= :minKeystrokes
          )
        GROUP BY bucket
        ORDER BY bucket
        """
    )
    fun getSessionBucketsForMood(
        bucketFormat: String,
        fromMs: Long,
        toMs: Long,
        moodScore: Int,
        minKeystrokes: Int,
    ): List<SessionBucketRow>

    /**
     * Phase 14: distinct local-time ISO calendar days that have at least
     * one session, oldest-first. The badge evaluator folds this list with
     * the shared `computeLongestStreak` to drive the Session-streak badge
     * group (group 7). Same `date(...,'unixepoch','localtime')` bucketing
     * the Phase 9 aggregators use, so a streak here lines up with the
     * Habits streak KPI.
     */
    @Query(
        "SELECT DISTINCT date(started_at / 1000, 'unixepoch', 'localtime') " +
            "FROM sessions ORDER BY 1"
    )
    fun getSessionCalendarDays(): List<String>

    /**
     * Insights-only: session ids whose productive-keystroke count
     * (events excluding AUTOCORRECT + BACKSPACE) meets the configured
     * minimum. The Sessions list intersects its query result with this
     * set so sub-threshold sessions are hidden without changing what's
     * stored. `minKeystrokes = 0` matches every session that has at
     * least one event row.
     */
    @Query(
        """
        SELECT session_id FROM ikd_events
        GROUP BY session_id
        HAVING SUM(CASE WHEN event_category NOT IN ('AUTOCORRECT', 'BACKSPACE') THEN 1 ELSE 0 END) >= :minKeystrokes
        """
    )
    fun getSessionIdsWithMinKeystrokes(minKeystrokes: Int): List<String>

    /** Returns null when the sessions table is empty. Used to size the All Time range. */
    @Query("SELECT MIN(started_at) FROM sessions")
    fun getEarliestSessionStart(): Long?

    /** Returns the most recently started session, or null when the table is empty. */
    @Query("SELECT * FROM sessions ORDER BY started_at DESC LIMIT 1")
    fun getMostRecentSession(): SessionRecord?

    /**
     * Phase 9.8: per-orientation session counts + total duration for the
     * dashboard's orientation donut. `:moodScore IS NULL` parameterised so
     * the unfiltered case and a mood-scoped recompute share the same query.
     *
     * @param fromMs inclusive lower bound on `started_at` (epoch millis).
     * @param toMs exclusive upper bound on `started_at` (epoch millis).
     * @param moodScore optional mood-filter scope; null disables filtering.
     */
    @Query(
        """
        SELECT
            device_orientation AS orientation,
            COUNT(*) AS sessionCount,
            SUM(CASE WHEN ended_at IS NOT NULL THEN ended_at - started_at ELSE 0 END) AS totalDurationMs
        FROM sessions
        WHERE started_at >= :fromMs
          AND started_at <  :toMs
          AND (:moodScore IS NULL
               OR session_id IN (SELECT session_id FROM mood_entries WHERE mood_score = :moodScore))
          AND session_id IN (
              SELECT session_id FROM ikd_events
              GROUP BY session_id
              HAVING SUM(CASE WHEN event_category NOT IN ('AUTOCORRECT', 'BACKSPACE') THEN 1 ELSE 0 END) >= :minKeystrokes
          )
        GROUP BY device_orientation
        ORDER BY device_orientation
        """
    )
    fun getOrientationBreakdown(
        fromMs: Long,
        toMs: Long,
        moodScore: Int?,
        minKeystrokes: Int,
    ): List<org.fossify.keyboard.interfaces.OrientationRow>
}
