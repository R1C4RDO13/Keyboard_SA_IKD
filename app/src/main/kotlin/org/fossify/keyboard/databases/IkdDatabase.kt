package org.fossify.keyboard.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.keyboard.interfaces.IkdEventDao
import org.fossify.keyboard.interfaces.MoodDao
import org.fossify.keyboard.interfaces.SensorSampleDao
import org.fossify.keyboard.interfaces.SessionDao
import org.fossify.keyboard.models.IkdEvent
import org.fossify.keyboard.models.MoodEntry
import org.fossify.keyboard.models.SensorSample
import org.fossify.keyboard.models.SessionRecord

@Database(
    entities = [SessionRecord::class, IkdEvent::class, SensorSample::class, MoodEntry::class],
    version = 3
)
abstract class IkdDatabase : RoomDatabase() {

    abstract fun SessionDao(): SessionDao

    abstract fun IkdEventDao(): IkdEventDao

    abstract fun SensorSampleDao(): SensorSampleDao

    abstract fun MoodDao(): MoodDao

    companion object {
        private var db: IkdDatabase? = null

        /**
         * Phase 8: schema bump 1 → 2.
         *
         * Strictly additive — no existing column or row is rewritten:
         *   1. `CREATE TABLE mood_entries` with FK CASCADE to `sessions`.
         *   2. `CREATE UNIQUE INDEX` on `session_id` enforces "at most one
         *      mood per session". SQLite's UNIQUE-on-NULL semantics treat
         *      each NULL as distinct (per SQL standard), so multiple
         *      sessionless rows are still allowed — the keyboard mood-bar
         *      never produces those, but a future journaling surface
         *      might. The migration matches the Room-generated v2 schema
         *      bytewise so `runMigrationsAndValidate` passes.
         *
         * NB: the Phase 8 plan describes the index as partial
         * (`WHERE session_id IS NOT NULL`). We dropped the WHERE clause
         * because SQLite already gives us the partial-uniqueness behaviour
         * via NULL-distinctness, AND because Room's `@Index(unique = true)`
         * cannot emit a partial index — using a partial index here would
         * mismatch the Room-validated schema and make the migration test
         * fail. The runtime semantics are identical.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `mood_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                        `session_id` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        `mood_score` INTEGER NOT NULL,
                        FOREIGN KEY(`session_id`) REFERENCES `sessions`(`session_id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_entries_session_id`
                        ON `mood_entries`(`session_id`)
                    """.trimIndent()
                )
            }
        }

        /**
         * Phase 7.1: schema bump 2 → 3.
         *
         * Adds a single integer column to `ikd_events` carrying the magnitude
         * of every correction event (`correction_weight`):
         *   1. `ALTER TABLE ikd_events ADD COLUMN correction_weight INTEGER NOT NULL DEFAULT 0`.
         *   2. Backfills `correction_weight = 1` on every existing
         *      `is_correction = 1` row so dashboards over historical data are
         *      continuous across the upgrade. Without the backfill, every
         *      legacy correction would silently drop to weight 0 and prior
         *      error rates would all collapse to 0.
         *
         * After the migration:
         *   - Legacy `BACKSPACE` rows: weight = 1 (matches today).
         *   - Legacy `AUTOCORRECT` rows: weight = 1 (we never knew the real
         *     replaced length on those rows; don't pretend we do).
         *   - Legacy non-correction rows: weight = 0 (column default).
         *   - New `AUTOCORRECT` rows: weight = `oldSelEnd - oldSelStart`,
         *     coerced to ≥ 1, supplied by `recordAutocorrectEvent`.
         *   - New `BACKSPACE` rows: weight = 1, supplied by `onKey`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ikd_events` ADD COLUMN `correction_weight` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE `ikd_events` SET `correction_weight` = 1 WHERE `is_correction` = 1"
                )
            }
        }

        fun getInstance(context: Context): IkdDatabase {
            if (db == null) {
                synchronized(IkdDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context, IkdDatabase::class.java, "ikd.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build()
                        db!!.openHelper.setWriteAheadLoggingEnabled(true)
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }
    }
}
