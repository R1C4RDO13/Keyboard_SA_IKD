package org.fossify.keyboard.databases

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.keyboard.databases.IkdDatabase.Companion.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 8: Room migration test for `IkdDatabase` v1 → v2.
 *
 * This test exercises the only schema bump shipped in Phase 8. It opens the
 * database at version 1, seeds one row in each pre-existing table, runs
 * `MIGRATION_1_2`, and asserts:
 *  1. The new `mood_entries` table exists with the right columns + the
 *     partial unique index on `session_id`.
 *  2. The original three tables retain their seed rows untouched (no data
 *     loss / no schema rewrite).
 *  3. Two `INSERT`s into `mood_entries` with the same non-null `session_id`
 *     fail (partial unique index honoured).
 *  4. Two `INSERT`s into `mood_entries` with `session_id = NULL` succeed
 *     (the partial constraint correctly excludes NULL rows).
 *
 * Runs on a connected device or emulator via
 * `./gradlew connectedCoreDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class IkdDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IkdDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate_1_to_2_preservesExistingTablesAndAddsMoodEntries() {
        helper.createDatabase(TEST_DB, 1).apply {
            // Seed one session, one event, one sample. Use raw SQL to keep
            // the test independent of the Room-generated DAO surface (which
            // ships v2 schemas only after the bump).
            execSQL(
                "INSERT INTO sessions (session_id, started_at, ended_at, " +
                    "event_count, sensor_count, device_orientation, locale) " +
                    "VALUES ('s1', 1000, 2000, 1, 1, 0, 'en-US')"
            )
            execSQL(
                "INSERT INTO ikd_events (session_id, timestamp, event_category, " +
                    "ikd_ms, hold_time_ms, flight_time_ms, is_correction) " +
                    "VALUES ('s1', 1500, 'ALPHA', 100, 80, 20, 0)"
            )
            execSQL(
                "INSERT INTO sensor_samples (session_id, timestamp, sensor_type, " +
                    "x, y, z) VALUES ('s1', 1500, 'GYRO', 0.1, 0.2, 0.3)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name = TEST_DB,
            version = 2,
            validateDroppedTables = true,
            MIGRATION_1_2,
        )

        // Original tables intact.
        migrated.query("SELECT count(*) FROM sessions").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        migrated.query("SELECT count(*) FROM ikd_events").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        migrated.query("SELECT count(*) FROM sensor_samples").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        // New table exists and is empty.
        migrated.query("SELECT count(*) FROM mood_entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrated_v2_db_enforcesPartialUniqueIndexOnSessionId() {
        // Build a fresh v2 DB through Room's open path (covers the
        // production codepath end-to-end) using the v1 → v2 migration.
        helper.createDatabase(TEST_DB, 1).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, IkdDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2)
            .build()

        try {
            // Need a parent session row so the FK is satisfied.
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sessions (session_id, started_at, ended_at, " +
                    "event_count, sensor_count, device_orientation, locale) " +
                    "VALUES ('s1', 1000, 2000, 0, 0, 0, '')"
            )

            val raw = db.openHelper.writableDatabase
            raw.execSQL(
                "INSERT INTO mood_entries (session_id, timestamp, mood_score) " +
                    "VALUES ('s1', 1100, 1)"
            )

            // Second insert for the same session_id must violate the partial
            // unique index. INSERT OR ABORT (Room's default) is captured as
            // an exception.
            try {
                raw.execSQL(
                    "INSERT INTO mood_entries (session_id, timestamp, mood_score) " +
                        "VALUES ('s1', 1200, 6)"
                )
                fail("Expected unique-constraint violation for duplicate session_id")
            } catch (expected: android.database.sqlite.SQLiteConstraintException) {
                // Pass: the partial unique index rejected the duplicate.
            }

            // INSERT OR REPLACE (the production write path uses this) must
            // succeed and leave a single row whose score is the latest one.
            raw.execSQL(
                "INSERT OR REPLACE INTO mood_entries (session_id, timestamp, mood_score) " +
                    "VALUES ('s1', 1300, 6)"
            )
            raw.query("SELECT mood_score FROM mood_entries WHERE session_id = 's1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(6, it.getInt(0))
                assertEquals(1, it.count)
            }

            // Two NULL session_id rows must succeed (the partial index excludes
            // NULL by construction). The keyboard never produces them, but
            // the schema permits them for forward-compat.
            raw.execSQL(
                "INSERT INTO mood_entries (session_id, timestamp, mood_score) " +
                    "VALUES (NULL, 1400, 2)"
            )
            raw.execSQL(
                "INSERT INTO mood_entries (session_id, timestamp, mood_score) " +
                    "VALUES (NULL, 1500, 3)"
            )
            raw.query("SELECT count(*) FROM mood_entries WHERE session_id IS NULL").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
