package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.backup.BackupData
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/**
 * [RoomHodithRepository.exportBackupData]/[RoomHodithRepository.importBackupData] against a real
 * Room database — the one place this repository's own logic (not just DAO queries) matters: the
 * FK-safe insert order and the all-in-one-transaction restore. The notification-evaluation side of
 * [RoomHodithRepository] is irrelevant here (import writes via DAOs directly, bypassing the
 * insert/update wrappers that trigger it), so its `Provider<NotificationEvaluator>` is a stand-in
 * that's never invoked.
 */
@RunWith(AndroidJUnit4::class)
class RoomHodithRepositoryBackupTest {
    private lateinit var db: HodithDatabase
    private lateinit var repository: RoomHodithRepository

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository =
            RoomHodithRepository(
                database = db,
                caseDao = db.caseDao(),
                eventDao = db.eventDao(),
                tagDao = db.tagDao(),
                hunchDao = db.hunchDao(),
                triggerDao = db.triggerDao(),
                notificationEvaluator = Provider { error("not used by backup export/import") },
                applicationScope = CoroutineScope(Dispatchers.Unconfined),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun exportThenImport_roundTripsEveryTableIntoAFreshDatabase() =
        runTest {
            val caseId = repository.insertCase(testCase(name = "Migraines"))
            // Via the raw DAO, not repository.insertEvent: that wrapper fires notification
            // evaluation as a fire-and-forget side effect, which would invoke this test's
            // intentionally-throwing NotificationEvaluator stand-in.
            val eventId = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 100L))
            repository.addTagToEvent(eventId, "aura")
            repository.insertHunch(testHunch(caseId = caseId))
            repository.insertTrigger(testTrigger(caseId = caseId))

            val backup = repository.exportBackupData()

            val freshDb = createInMemoryDatabase()
            try {
                val freshRepository =
                    RoomHodithRepository(
                        database = freshDb,
                        caseDao = freshDb.caseDao(),
                        eventDao = freshDb.eventDao(),
                        tagDao = freshDb.tagDao(),
                        hunchDao = freshDb.hunchDao(),
                        triggerDao = freshDb.triggerDao(),
                        notificationEvaluator = Provider { error("not used by backup export/import") },
                        applicationScope = CoroutineScope(Dispatchers.Unconfined),
                    )

                freshRepository.importBackupData(backup)

                assertEquals(backup, freshRepository.exportBackupData())
            } finally {
                freshDb.close()
            }
        }

    @Test
    fun importBackupData_replacesExistingDataRatherThanMerging() =
        runTest {
            repository.insertCase(testCase(name = "Stale"))

            val incoming =
                BackupData(
                    cases = listOf(testCase(id = 5L, name = "Restored")),
                    tags = emptyList(),
                    events = emptyList(),
                    eventTags = emptyList(),
                    hunches = emptyList(),
                    triggers = emptyList(),
                )
            repository.importBackupData(incoming)

            assertEquals(listOf("Restored"), db.caseDao().getAll().map { it.name })
        }

    @Test
    fun importBackupData_insertsAcrossForeignKeysWithoutViolatingConstraints() =
        runTest {
            // Regression guard for the FK-safe ordering in importBackupData: cases/tags before
            // anything referencing them, events before event_tags. A wrong order throws inside
            // the transaction rather than silently reordering, so getting here at all is the assertion.
            val backup =
                BackupData(
                    cases = listOf(testCase(id = 1L, name = "Migraines")),
                    tags = listOf(TagEntity(id = 1L, name = "aura")),
                    events = listOf(testEvent(id = 1L, caseId = 1L, occurredAt = 100L)),
                    eventTags = listOf(EventTagCrossRef(eventId = 1L, tagId = 1L)),
                    hunches = listOf(testHunch(caseId = 1L)),
                    triggers = listOf(testTrigger(caseId = 1L)),
                )

            repository.importBackupData(backup)

            assertTrue(db.tagDao().getAllEventTags().isNotEmpty())
        }

    @Test
    fun importBackupData_rollsBackEverythingWhenAnInsertFails() =
        runTest {
            // The all-or-nothing guarantee (spec §16) depends on the whole restore running inside
            // one Room transaction. An event referencing a case that doesn't exist in the same
            // backup violates the caseId foreign key partway through the insert sequence — the
            // pre-existing "Original" case must still be there afterward, not gone-and-not-replaced.
            // This repository method itself does no validation — that's `validateBackup`'s job,
            // called from `SettingsViewModel.performImport` above this layer. This test proves the
            // transaction rollback holds on its own, as a backstop independent of that layer.
            repository.insertCase(testCase(name = "Original"))
            val invalidBackup =
                BackupData(
                    cases = emptyList(),
                    tags = emptyList(),
                    events = listOf(testEvent(caseId = 999L, occurredAt = 100L)),
                    eventTags = emptyList(),
                    hunches = emptyList(),
                    triggers = emptyList(),
                )

            try {
                repository.importBackupData(invalidBackup)
                fail("Expected a foreign key constraint violation")
            } catch (e: Exception) {
                // expected: the invalid event's caseId has no matching case
            }

            assertEquals(listOf("Original"), db.caseDao().getAll().map { it.name })
        }
}
