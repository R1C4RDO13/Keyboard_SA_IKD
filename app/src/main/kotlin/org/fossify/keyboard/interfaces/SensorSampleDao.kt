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
}
