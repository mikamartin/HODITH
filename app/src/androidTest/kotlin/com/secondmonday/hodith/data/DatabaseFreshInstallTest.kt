package com.secondmonday.hodith.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
