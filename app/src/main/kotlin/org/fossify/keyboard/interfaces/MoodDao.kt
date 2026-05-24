package org.fossify.keyboard.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.keyboard.models.MoodEntry

/**
 * Phase 8: read/write surface for the `mood_entries` table.
 *
 * Writes go through `insertOrReplace` (single in-flight `MoodEntry` per
 * session, enforced by the partial unique index on `session_id`) and
 * `clearForSession` (issued by the keyboard 🛡️ slot when the user
 * re-enables privacy mid-session).
 *
 * Reads serve four call sites:
 *  - `getForSession` — Phase 8.2 mood bar (highlight-on-restore) and
 *    Phase 8.3 per-session dashboard.
 *  - `getMoodDistribution` — Phase 8.4 Mood Distribution panel.
 *  - `getMoodBuckets` — Phase 8.4 Mood-over-Time line chart and Avg Mood KPI.
 */
@Dao
interface MoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(entry: MoodEntry)

    @Query("DELETE FROM mood_entries WHERE session_id = :sessionId")
    fun clearForSession(sessionId: String)

    @Query("SELECT * FROM mood_entries WHERE session_id = :sessionId LIMIT 1")
    fun getForSession(sessionId: String): MoodEntry?

    @Query("SELECT COUNT(*) FROM mood_entries")
    fun count(): Int

    /** Phase 9.4: drives the dashboard's Mood Filter chip-row visibility. */
    @Query("SELECT (SELECT COUNT(*) FROM mood_entries) > 0")
    fun hasAnyMoodEntry(): Boolean

    @Query("SELECT * FROM mood_entries ORDER BY session_id, timestamp")
    fun getAllOrderedBySession(): List<MoodEntry>

    /**
     * Phase 14: every intentional mood log's timestamp (epoch millis),
     * oldest-first. The badge evaluator buckets these by local ISO day in
     * Kotlin to derive `todayLogs` (group 3 — daily-reset challenge),
     * `devotionStreak` (group 4), and the last-14-days `recentDayQualified`
     * strip — one query, single pass, ≤ a few thousand rows. Every row in
     * `mood_entries` is one intentional log (the standing rating never
     * writes a row), so the per-day count is deliberate-logging frequency.
     */
    @Query("SELECT timestamp FROM mood_entries ORDER BY timestamp")
    fun getMoodTimestampsOrdered(): List<Long>

    /**
     * Per-category distribution for a time range. Returns one row per
     * `mood_score` value with at least one entry; scores absent from the
     * result have zero entries in the range.
     *
     * @param fromMs inclusive lower bound on `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `timestamp` (epoch millis).
     */
    @Query(
        """
        SELECT
            mood_score AS score,
            COUNT(*)   AS entryCount
        FROM mood_entries
        WHERE timestamp >= :fromMs AND timestamp < :toMs
        GROUP BY mood_score
        ORDER BY mood_score
        """
    )
    fun getMoodDistribution(fromMs: Long, toMs: Long): List<MoodDistributionRow>

    /**
     * Bucketed average + count for a time range. Buckets are produced by
     * SQLite's `strftime` so the function is generic over daily / weekly
     * / monthly granularities.
     *
     * @param bucketFormat strftime pattern ("%Y-%m-%d" daily, "%Y-%W" weekly).
     * @param fromMs inclusive lower bound on `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `timestamp` (epoch millis).
     */
    @Query(
        """
        SELECT
            strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
            AVG(mood_score)                                                     AS avgScore,
            COUNT(*)                                                            AS entryCount
        FROM mood_entries
        WHERE timestamp >= :fromMs AND timestamp < :toMs
        GROUP BY bucket
        ORDER BY bucket
        """
    )
    fun getMoodBuckets(
        bucketFormat: String,
        fromMs: Long,
        toMs: Long,
    ): List<MoodBucketRow>

    /**
     * Phase 8.3: per-bucket-per-category aggregation. Returns one row per
     * `(bucket, mood_score)` pair where the bucket has at least one entry
     * with that score; zero-count pairs are absent and the aggregator
     * zero-fills them in Kotlin. Powers the "Mood Mix over Time" stacked
     * bar chart.
     *
     * @param bucketFormat strftime pattern ("%Y-%m-%d" daily, "%Y-%W" weekly).
     * @param fromMs inclusive lower bound on `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `timestamp` (epoch millis).
     */
    @Query(
        """
        SELECT
            strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
            mood_score                                                          AS score,
            COUNT(*)                                                            AS entryCount
        FROM mood_entries
        WHERE timestamp >= :fromMs AND timestamp < :toMs
        GROUP BY bucket, mood_score
        ORDER BY bucket ASC, mood_score ASC
        """
    )
    fun getMoodCategoryBuckets(
        bucketFormat: String,
        fromMs: Long,
        toMs: Long,
    ): List<MoodCategoryBucketRow>
}
