package com.secondmonday.hodith.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB_NAME = "fresh-install-test.db"

/**
 * Baseline the migration guard (DatabaseModule, no `fallbackToDestructiveMigration`) rests on:
 * a database created fresh at the frozen schema version must open and validate against the live
 * entity definitions with zero registered migrations. Also the template the first real
 * Migration(6,7) test extends.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseFreshInstallTest {
    @get:Rule
    val migrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            HodithDatabase::class.java,
        )

    @Smoke
    @Test
    fun freshDatabaseAtFreezePointOpensAndDaosAreQueryable() =
        runTest {
            migrationTestHelper.createDatabase(TEST_DB_NAME, HODITH_DATABASE_VERSION).close()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db =
                Room
                    .databaseBuilder(context, HodithDatabase::class.java, TEST_DB_NAME)
                    .addMigrations(*HodithDatabase.MIGRATIONS)
                    .build()
            migrationTestHelper.closeWhenFinished(db)

            assertEquals(emptyList<CaseEntity>(), db.caseDao().getActiveCases())
        }

    /**
     * v6 → v7 auto-migration ([DropStaleNudgeColumn]): the `events` table loses
     * `staleNudgeDismissedAt` and every existing row survives the table recreate (SQLite `DROP
     * COLUMN` is API 34+, so Room rebuilds the table on API 31-33).
     */
    @Test
    fun migrationFrom6To7_dropsStaleNudgeColumn_andPreservesEventRows() =
        runTest {
            migrationTestHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
                db.execSQL(
                    "INSERT INTO cases (id, name, icon, createdAt, logFlow, durationMode, intensityEnabled, " +
                        "hunchNudgeDismissed, checkInsEnabled, sortOrder, archived) " +
                        "VALUES (1, 'Coffee', '☕', 0, 'ONE_TAP', 'NONE', 0, 0, 1, 0, 0)",
                )
                db.execSQL(
                    "INSERT INTO events (id, caseId, occurredAt, endedAt, intensity, note, loggedAt, staleNudgeDismissedAt) " +
                        "VALUES (1, 1, 100, NULL, NULL, NULL, 100, 5000)",
                )
            }

            migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true).use { db ->
                db.query("SELECT * FROM events WHERE id = 1").use { cursor ->
                    assertTrue("the migrated event row should survive", cursor.moveToFirst())
                    assertEquals(-1, cursor.getColumnIndex("staleNudgeDismissedAt"))
                    assertEquals(100L, cursor.getLong(cursor.getColumnIndexOrThrow("occurredAt")))
                    assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("caseId")))
                }
            }
        }
}
