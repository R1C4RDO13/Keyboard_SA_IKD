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
}
