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
            100.0 * SUM(CASE WHEN is_correction THEN 1 ELSE 0 END) / COUNT(*) AS errorRatePct,
            COUNT(*) AS eventCount,
            COUNT(DISTINCT session_id) AS sessionCount
        FROM ikd_events
        WHERE timestamp >= :fromMs AND timestamp < :toMs
        GROUP BY bucket
        ORDER BY bucket
        """
    )
    fun getEventBuckets(bucketFormat: String, fromMs: Long, toMs: Long): List<EventBucketRow>
}
