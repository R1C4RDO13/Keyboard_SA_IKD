package org.fossify.keyboard.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.keyboard.models.SensorSample

@Dao
interface SensorSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(samples: List<SensorSample>): List<Long>

    @Query("SELECT * FROM sensor_samples WHERE session_id = :sessionId ORDER BY timestamp")
    fun getSamplesForSession(sessionId: String): List<SensorSample>

    @Query("SELECT * FROM sensor_samples ORDER BY session_id, timestamp")
    fun getAllOrderedBySession(): List<SensorSample>

    @Query("SELECT COUNT(*) FROM sensor_samples")
    fun count(): Int

    /**
     * Per-session bucketed sensor aggregation (Phase 5). Each row is one
     * `(bucketIndex, sensorType)` pair — the loader splits gyro / accel
     * post-query.
     *
     * SQLite has no `sqrt`; aggregating `AVG(x*x + y*y + z*z)` SQL-side and
     * taking `Math.sqrt` per row in Kotlin keeps the heavy lifting in SQL
     * and returns ≤ 400 rows per call (200 per sensor type at max bucket
     * width).
     *
     * @param sessionId the session to aggregate.
     * @param startMs the session's start timestamp, used as the bucket-zero anchor.
     * @param bucketWidthMs the integer bucket width in milliseconds.
     */
    @Query(
        """
        SELECT
            ((timestamp - :startMs) / :bucketWidthMs) AS bucketIndex,
            sensor_type                                AS sensorType,
            AVG(x * x + y * y + z * z)                 AS avgSquaredMagnitude,
            AVG(x)                                     AS avgX,
            AVG(y)                                     AS avgY,
            AVG(z)                                     AS avgZ,
            COUNT(*)                                   AS sampleCount
        FROM sensor_samples
        WHERE session_id = :sessionId
        GROUP BY bucketIndex, sensor_type
        ORDER BY sensor_type, bucketIndex
        """
    )
    fun getSessionSensorBuckets(
        sessionId: String,
        startMs: Long,
        bucketWidthMs: Long,
    ): List<SensorBucketRow>

    /**
     * Phase 9.2: bucketed *global* sensor aggregation for the dashboard
     * Trends section. One row per `(bucket, sensorType)` pair.
     *
     * `:moodScore` is `IS NULL`-parameterised so the same query covers both
     * the unfiltered case (`null`) and a mood-scoped recompute. Sensors are a
     * lower-frequency table than `ikd_events`, so the parameterised path is
     * acceptable here — see Phase 9 orchestrator Decision #14.
     *
     * @param bucketFormat strftime pattern ("%Y-%m-%d" daily, "%Y-%W" weekly).
     * @param fromMs inclusive lower bound on `timestamp` (epoch millis).
     * @param toMs exclusive upper bound on `timestamp` (epoch millis).
     * @param moodScore optional mood-filter scope; null disables filtering.
     */
    @Query(
        """
        SELECT
            strftime(:bucketFormat, timestamp / 1000, 'unixepoch', 'localtime') AS bucket,
            sensor_type AS sensorType,
            AVG(x * x + y * y + z * z) AS avgSquaredMagnitude,
            COUNT(*) AS sampleCount
        FROM sensor_samples
        WHERE timestamp >= :fromMs
          AND timestamp <  :toMs
          AND (:moodScore IS NULL
               OR session_id IN (SELECT session_id FROM mood_entries WHERE mood_score = :moodScore))
          AND session_id IN (
              SELECT session_id FROM ikd_events
              GROUP BY session_id
              HAVING SUM(CASE WHEN event_category NOT IN ('AUTOCORRECT', 'BACKSPACE') THEN 1 ELSE 0 END) >= :minKeystrokes
          )
        GROUP BY bucket, sensor_type
        ORDER BY sensor_type, bucket
        """
    )
    fun getSensorBuckets(
        bucketFormat: String,
        fromMs: Long,
        toMs: Long,
        moodScore: Int?,
        minKeystrokes: Int,
    ): List<SensorBucketAggregateRow>
}
