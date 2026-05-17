package org.fossify.keyboard.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 14: a single unlocked badge.
 *
 * Stores only the stable [badgeKey] (the identity transcribed from
 * `Phase14_BadgeCatalog.md`) and the wall-clock millisecond the badge was
 * first earned. The emoji / title / description / criteria all live in
 * `helpers/IkdBadgeCatalog.kt` keyed on [badgeKey] — the DB stays free of
 * any presentation string, same privacy discipline as `MoodEntry` keeping
 * only the integer score.
 *
 * Badges are derived state, not captured data: a row here is only ever
 * written by [org.fossify.keyboard.helpers.IkdBadgeEvaluator] when an
 * already-evaluated criterion first passes. There is no `badges` block in
 * the CSV export — the export contract is frozen and unchanged.
 *
 * The unique index on `badge_key` enforces "at most one row per badge".
 * Inserts go through `OnConflictStrategy.REPLACE` via `BadgeDao.upsert`,
 * so an idempotent re-evaluation never duplicates a row.
 */
@Entity(
    tableName = "badges",
    indices = [
        Index(value = ["badge_key"], unique = true)
    ]
)
data class Badge(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long? = null,
    @ColumnInfo(name = "badge_key") var badgeKey: String,
    @ColumnInfo(name = "unlocked_at") var unlockedAt: Long,
)
