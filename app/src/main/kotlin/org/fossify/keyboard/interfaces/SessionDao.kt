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
}
