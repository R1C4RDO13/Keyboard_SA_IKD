package org.fossify.keyboard.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.keyboard.models.Badge

/**
 * Phase 14: read/write surface for the `badges` table.
 *
 * Writes are idempotent: [upsert] uses `OnConflictStrategy.REPLACE` keyed
 * on the unique `badge_key` index, so re-evaluating an already-unlocked
 * badge is a no-op rather than a duplicate row.
 *
 * Reads serve the evaluator (which keys progress / unlock decisions off
 * [getAllUnlockedKeys]) and the Achievements UI (which renders unlock
 * dates from [getAllUnlocked]).
 */
@Dao
interface BadgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(badges: List<Badge>)

    @Query("SELECT badge_key FROM badges")
    fun getAllUnlockedKeys(): List<String>

    @Query("SELECT * FROM badges ORDER BY unlocked_at DESC")
    fun getAllUnlocked(): List<Badge>
}
