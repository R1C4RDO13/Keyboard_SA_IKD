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
        GROUP BY bucket
        ORDER BY bucket
        """
    )
    fun getSessionBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<SessionBucketRow>

    /** Returns null when the sessions table is empty. Used to size the All Time range. */
    @Query("SELECT MIN(started_at) FROM sessions")
    fun getEarliestSessionStart(): Long?
}
