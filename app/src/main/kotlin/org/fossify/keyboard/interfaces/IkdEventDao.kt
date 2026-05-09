package org.fossify.keyboard.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.keyboard.models.IkdEvent

@Dao
interface IkdEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(events: List<IkdEvent>): List<Long>

    @Query("SELECT * FROM ikd_events WHERE session_id = :sessionId ORDER BY timestamp")
    fun getEventsForSession(sessionId: String): List<IkdEvent>

    @Query("SELECT * FROM ikd_events ORDER BY session_id, timestamp")
    fun getAllOrderedBySession(): List<IkdEvent>

    @Query("SELECT COUNT(*) FROM ikd_events")
    fun count(): Int

    /**
     * Bucketed aggregation. Returns at most ~52 rows for ALL_TIME at default
     * retention. The `avgIkdMs` column is NULL when the bucket has no events
     * with ikd_ms >= 0 (the -1 sentinel marks the first event of a session
     * and is excluded from the average).
     *
     * @param bucketFormat strftime pattern ("%Y-%m-%d" for daily, "%Y-%W"
     *   for weekly, "%Y-%m" for monthly).
     * @param fromMs inclusive lower bound on `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `timestamp` (epoch millis).
     */
    @Query(
        """
        SELECT
            strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
            AVG(CASE WHEN ikd_ms >= 0 THEN ikd_ms END) AS avgIkdMs,
            COUNT(*) AS eventCount,
            SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END) AS keystrokeCount,
            SUM(CASE WHEN is_correction THEN 1 ELSE 0 END) AS correctionCount,
            SUM(correction_weight) AS correctionWeight,
            COUNT(DISTINCT session_id) AS sessionCount
        FROM ikd_events
        WHERE timestamp >= :fromMs AND timestamp < :toMs
        GROUP BY bucket
        ORDER BY bucket
        """
    )
    fun getEventBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<EventBucketRow>

    /**
     * Per-session aggregation. Returns exactly one row, even for sessions with
     * no events (`eventCount` will be 0 and the averages / timestamps NULL).
     * Sentinel `-1` rows for `ikd_ms` / `hold_time_ms` / `flight_time_ms`
     * are excluded from the corresponding averages so the first event of a
     * session does not poison the means.
     */
    @Query(
        """
        SELECT
            COUNT(*)                                                    AS eventCount,
            SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END) AS keystrokeCount,
            SUM(CASE WHEN is_correction THEN 1 ELSE 0 END)              AS correctionCount,
            SUM(correction_weight)                                      AS correctionWeight,
            AVG(CASE WHEN ikd_ms         >= 0 THEN ikd_ms         END)  AS avgIkdMs,
            AVG(CASE WHEN hold_time_ms   >= 0 THEN hold_time_ms   END)  AS avgHoldMs,
            AVG(CASE WHEN flight_time_ms >= 0 THEN flight_time_ms END)  AS avgFlightMs,
            MIN(timestamp)                                              AS firstTimestamp,
            MAX(timestamp)                                              AS lastTimestamp
        FROM ikd_events
        WHERE session_id = :sessionId
        """
    )
    fun getSessionStats(sessionId: String): SessionStatsRow

    /**
     * Per-session bucketed timing aggregation (Phase 5). Each row is one
     * time bucket relative to the session's start, ≤ ~200 rows per call
     * (capped by the loader's bucket-width formula).
     *
     * `avgIkdMs` / `avgHoldMs` / `avgFlightMs` are NULL when every row in
     * the bucket carries the sentinel `-1` (the first event of a session).
     * The loader surfaces that as a missing chart point.
     *
     * @param sessionId the session to aggregate.
     * @param startMs the session's start timestamp, used as the bucket-zero anchor.
     * @param bucketWidthMs the integer bucket width in milliseconds.
     */
    @Query(
        """
        SELECT
            ((timestamp - :startMs) / :bucketWidthMs)                   AS bucketIndex,
            AVG(CASE WHEN ikd_ms         >= 0 THEN ikd_ms         END)  AS avgIkdMs,
            AVG(CASE WHEN hold_time_ms   >= 0 THEN hold_time_ms   END)  AS avgHoldMs,
            AVG(CASE WHEN flight_time_ms >= 0 THEN flight_time_ms END)  AS avgFlightMs,
            COUNT(*)                                                    AS eventCount
        FROM ikd_events
        WHERE session_id = :sessionId
        GROUP BY bucketIndex
        ORDER BY bucketIndex
        """
    )
    fun getSessionTimingBuckets(
        sessionId: String,
        startMs: Long,
        bucketWidthMs: Long,
    ): List<TimingBucketRow>

    /**
     * Phase 9.3: bucketed Habits aggregation. One row per `strftime` bucket,
     * joining session-side counts / total duration with event-side
     * keystroke / correction counts and average flight time. Single
     * round-trip via two SQLite subqueries `LEFT JOIN`-ed on the bucket key.
     *
     * `:moodScore` `IS NULL` parameterised so the same query covers the
     * unfiltered case and a mood-scoped recompute (Phase 9.4 wires the
     * non-null path).
     *
     * @param bucketFormat strftime pattern ("%Y-%m-%d" daily, "%Y-%W" weekly).
     * @param fromMs inclusive lower bound on `started_at` / `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `started_at` / `timestamp` (epoch millis).
     * @param moodScore optional mood-filter scope; null disables filtering.
     */
    @Query(
        """
        SELECT
            COALESCE(s.bucket, e.bucket) AS bucket,
            COALESCE(s.sessionCount, 0) AS sessionCount,
            COALESCE(s.totalDurationMs, 0) AS totalDurationMs,
            e.avgFlightMs AS avgFlightMs,
            COALESCE(e.correctionWeight, 0) AS correctionWeight,
            COALESCE(e.keystrokeCount, 0) AS keystrokeCount
        FROM (
            SELECT
                strftime(:bucketFormat, started_at / 1000, 'unixepoch', 'localtime') AS bucket,
                COUNT(*) AS sessionCount,
                SUM(CASE WHEN ended_at IS NOT NULL THEN ended_at - started_at ELSE 0 END) AS totalDurationMs
            FROM sessions
            WHERE started_at >= :fromMs
              AND started_at <  :toMs
              AND (:moodScore IS NULL
                   OR session_id IN (SELECT session_id FROM mood_entries WHERE mood_score = :moodScore))
            GROUP BY bucket
        ) AS s
        LEFT JOIN (
            SELECT
                strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
                AVG(CASE WHEN flight_time_ms >= 0 THEN flight_time_ms END) AS avgFlightMs,
                SUM(correction_weight) AS correctionWeight,
                SUM(CASE WHEN event_category != 'AUTOCORRECT' THEN 1 ELSE 0 END) AS keystrokeCount
            FROM ikd_events
            WHERE timestamp >= :fromMs
              AND timestamp <  :toMs
              AND (:moodScore IS NULL
                   OR session_id IN (SELECT session_id FROM mood_entries WHERE mood_score = :moodScore))
            GROUP BY bucket
        ) AS e
        ON s.bucket = e.bucket
        ORDER BY bucket
        """
    )
    fun getHabitsBuckets(
        bucketFormat: String,
        fromMs: Long,
        toMs: Long,
        moodScore: Int?,
    ): List<HabitsBucketRow>

    /**
     * Phase 9.5: daily keystroke counts for the calendar heatmap and the
     * daily keypress bar chart. AUTOCORRECT rows are excluded — matches
     * the Phase 7 WPM denominator (autocorrects are corrections, not new
     * typing).
     *
     * @param fromMs inclusive lower bound on `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `timestamp` (epoch millis).
     * @param moodScore optional mood-filter scope; null disables filtering.
     */
    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
            COUNT(*) AS keystrokeCount
        FROM ikd_events
        WHERE event_category != 'AUTOCORRECT'
          AND timestamp >= :fromMs
          AND timestamp <  :toMs
          AND (:moodScore IS NULL
               OR session_id IN (SELECT session_id FROM mood_entries WHERE mood_score = :moodScore))
        GROUP BY day
        ORDER BY day
        """
    )
    fun getDailyKeystrokes(
        fromMs: Long,
        toMs: Long,
        moodScore: Int?,
    ): List<DailyBucketRow>
}
