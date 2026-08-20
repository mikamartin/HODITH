package com.secondmonday.hodith.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.HodithDatabase
import com.secondmonday.hodith.data.RoomHodithRepository
import com.secondmonday.hodith.data.createInMemoryDatabase
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.data.testHunch
import com.secondmonday.hodith.data.testTrigger
import com.secondmonday.hodith.testtags.Smoke
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/**
 * Proves the full backup-import chain end to end against a real database: real exported JSON,
 * through [BackupSerializer.peekSchemaVersion]/[BackupSerializer.fromJson]'s version-tolerant
 * parsing, into [RoomHodithRepository.importBackupData]. Unit tests already cover the version
 * logic in isolation (`BackupSerializerTest`, hand-written JSON, no database) and the real-DB
 * restore in isolation (`RoomHodithRepositoryBackupTest`, raw `BackupData` objects, no JSON) - this
 * is the one place both run together, the gap a `SettingsViewModel`-level test with
 * `FakeHodithRepository` can't close.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportIntegrationTest {
    private lateinit var db: HodithDatabase
    private lateinit var repository: RoomHodithRepository
    private val backupSerializer = BackupSerializer(Moshi.Builder().build())

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = repositoryFor(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun exportedJsonImportsThroughTheVersionTolerantPathIntoARealDatabase() =
        runTest {
            val caseId = repository.insertCase(testCase(name = "Migraines"))
            // Via the raw DAO, not repository.insertEvent: that wrapper fires notification
            // evaluation as a fire-and-forget side effect, which this test doesn't need.
            val eventId = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 100L))
            repository.addTagToEvent(eventId, "aura")
            repository.insertHunch(testHunch(caseId = caseId))
            repository.insertTrigger(testTrigger(caseId = caseId))

            val json = backupSerializer.toJson(repository.exportBackupData())

            val freshDb = createInMemoryDatabase()
            try {
                val freshRepository = repositoryFor(freshDb)

                val declaredVersion = backupSerializer.peekSchemaVersion(json)
                checkNotNull(declaredVersion) { "Real exported JSON must always declare a parseable schema version" }
                val restored = backupSerializer.fromJson(json, declaredVersion)
                freshRepository.importBackupData(restored)

                assertEquals(repository.exportBackupData(), freshRepository.exportBackupData())
            } finally {
                freshDb.close()
            }
        }

    private fun repositoryFor(database: HodithDatabase) =
        RoomHodithRepository(
            database = database,
            caseDao = database.caseDao(),
            eventDao = database.eventDao(),
            tagDao = database.tagDao(),
            hunchDao = database.hunchDao(),
            triggerDao = database.triggerDao(),
            notificationEvaluator = Provider { error("not used by backup export/import") },
            applicationScope = CoroutineScope(Dispatchers.Unconfined),
        )
}
