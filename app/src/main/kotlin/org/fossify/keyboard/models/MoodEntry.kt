package org.fossify.keyboard.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 8: per-session mood annotation.
 *
 * Stores only the integer ordinal valence (1 = Happiness, 6 = Anger) and a
 * timestamp. The `score → emoji / label` mapping lives entirely in the UI
 * layer (`helpers/MoodEmoji.kt`) so the DB stays privacy-clean.
 *
 * `sessionId` is nullable for forward-compatibility (a future journaling
 * surface might want sessionless entries), but the keyboard mood-bar in
 * Phase 8 never produces `NULL` rows — tapping any of the six emotion
 * buttons implicitly disables privacy mode and writes a row keyed on the
 * current capture session.
 *
 * The unique index on `session_id` enforces "at most one mood per session".
 * SQLite treats each NULL as distinct in a UNIQUE index (per the SQL
 * standard), so multiple sessionless rows still succeed even though the
 * index is not declared `WHERE session_id IS NOT NULL`. This sidesteps a
 * Room limitation — `@Index(unique = true)` cannot emit a partial index,
 * so a `WHERE`-clauseed migration index would cause schema-validation to
 * mismatch on first open. Insert collisions are handled with
 * `OnConflictStrategy.REPLACE` via `MoodDao.insertOrReplace`.
 */
@Entity(
    tableName = "mood_entries",
    foreignKeys = [
        ForeignKey(
            entity = SessionRecord::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"], unique = true)
    ]
)
data class MoodEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long?,
    @ColumnInfo(name = "session_id") var sessionId: String?,
    @ColumnInfo(name = "timestamp") var timestamp: Long,
    @ColumnInfo(name = "mood_score") var moodScore: Int,
)
