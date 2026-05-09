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
    version = 2
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

        fun getInstance(context: Context): IkdDatabase {
            if (db == null) {
                synchronized(IkdDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context, IkdDatabase::class.java, "ikd.db")
                            .addMigrations(MIGRATION_1_2)
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
