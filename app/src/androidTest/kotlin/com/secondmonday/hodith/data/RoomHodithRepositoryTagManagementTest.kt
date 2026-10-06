package com.secondmonday.hodith.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/**
 * The tag-management writes in [RoomHodithRepository] against a real database. The merge is the
 * one multi-write operation here, so the rollback test relies on the database's FK enforcement.
 * Notification evaluation is irrelevant to tags, so its scheduler is a stand-in that's never invoked.
 */
@RunWith(AndroidJUnit4::class)
class RoomHodithRepositoryTagManagementTest {
    private lateinit var db: HodithDatabase
    private lateinit var repository: RoomHodithRepository
    private var caseId: Long = 0
    private var eventId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            repository =
                RoomHodithRepository(
                    database = db,
                    caseDao = db.caseDao(),
                    eventDao = db.eventDao(),
                    tagDao = db.tagDao(),
                    watchDao = db.watchDao(),
                    notificationEvalScheduler =
                        NotificationEvalScheduler(
                            scope = CoroutineScope(Dispatchers.Unconfined),
                            evaluator = Provider { error("not used by tag management") },
                        ),
                )
            caseId = db.caseDao().insert(testCase())
            eventId = db.eventDao().insert(testEvent(caseId = caseId))
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun tagId(name: String): Long = db.tagDao().getByName(name)!!.id

    @Test
    fun mergeTag_collapsesOverlapAndRemovesTheSource() =
        runTest {
            val otherEventId = db.eventDao().insert(testEvent(caseId = caseId))
            repository.addTagToEvent(eventId, "espresso")
            repository.addTagToEvent(eventId, "coffee")
            repository.addTagToEvent(otherEventId, "espresso")

            repository.mergeTag(sourceId = tagId("espresso"), targetId = tagId("coffee"))

            assertEquals(listOf("coffee"), repository.observeAllTags().first().map { it.name })
            val attachments = db.tagDao().getAllEventTags()
            assertEquals(setOf(eventId, otherEventId), attachments.map { it.eventId }.toSet())
            assertEquals(2, attachments.size)
        }

    @Test
    fun mergeTag_rollsBackWhenTheTargetIsMissing() =
        runTest {
            repository.addTagToEvent(eventId, "espresso")
            val sourceId = tagId("espresso")

            // A target id with no tag row makes the re-pointing insert violate the FK, so the whole merge must undo.
            try {
                repository.mergeTag(sourceId = sourceId, targetId = 9_999L)
                fail("merging into a missing tag should throw")
            } catch (expected: SQLiteConstraintException) {
                // expected: the foreign key rejected the re-pointed attachment
            }

            assertEquals(listOf("espresso"), db.tagDao().getAll().map { it.name })
            assertEquals(listOf(sourceId), db.tagDao().getAllEventTags().map { it.tagId })
        }

    @Test
    fun renameTag_trimsAndWritesTheNewName() =
        runTest {
            repository.addTagToEvent(eventId, "coffe")

            repository.renameTag(tagId("coffe"), "  coffee  ")

            assertEquals(listOf("coffee"), repository.observeAllTags().first().map { it.name })
        }

    @Test
    fun deleteTag_keepsEventsAndTheirOtherTags() =
        runTest {
            repository.addTagToEvent(eventId, "focus")
            repository.addTagToEvent(eventId, "calm")

            repository.deleteTag(tagId("focus"))

            assertEquals(listOf("calm"), repository.observeTagsForEvent(eventId).first().map { it.name })
            assertTrue(db.eventDao().getById(eventId) != null)
        }

    @Test
    fun observeTagEventCounts_reflectsTheMergeAfterItCommits() =
        runTest {
            repository.addTagToEvent(eventId, "espresso")
            repository.addTagToEvent(eventId, "coffee")
            assertEquals(
                1,
                repository
                    .observeTagEventCounts()
                    .first()
                    .single { it.tagId == tagId("espresso") }
                    .eventCount,
            )

            repository.mergeTag(sourceId = tagId("espresso"), targetId = tagId("coffee"))

            assertEquals(listOf(1), repository.observeTagEventCounts().first().map { it.eventCount })
        }

    @Test
    fun findOtherTagByName_isWhatTheRenameCollisionCheckUses() =
        runTest {
            repository.addTagToEvent(eventId, "Coffee")
            repository.addTagToEvent(eventId, "tea")

            assertEquals("Coffee", repository.findOtherTagByName("coffee", excludeId = tagId("tea"))?.name)
            assertNull(repository.findOtherTagByName("Coffee", excludeId = tagId("Coffee")))
        }

    @Test
    fun countEventsWithBoth_matchesWhatTheMergeWillCollapse() =
        runTest {
            repository.addTagToEvent(eventId, "espresso")
            repository.addTagToEvent(eventId, "coffee")

            assertEquals(1, repository.countEventsWithBoth(sourceId = tagId("espresso"), targetId = tagId("coffee")))
        }

    @Test
    fun mergeTag_intoItself_isRejectedAndKeepsTheAttachments() =
        runTest {
            repository.addTagToEvent(eventId, "coffee")
            val id = tagId("coffee")

            try {
                repository.mergeTag(sourceId = id, targetId = id)
                fail("merging a tag into itself should throw")
            } catch (expected: IllegalArgumentException) {
                // expected: the guard runs before any write
            }

            assertEquals(listOf(id), db.tagDao().getAllEventTags().map { it.tagId })
        }

    @Test
    fun renameTag_ontoAnExistingExactName_violatesTheUniqueIndex() =
        runTest {
            repository.addTagToEvent(eventId, "coffee")
            repository.addTagToEvent(eventId, "tea")

            try {
                repository.renameTag(tagId("tea"), "coffee")
                fail("renaming onto an existing name should throw")
            } catch (expected: SQLiteConstraintException) {
                // expected: the ViewModel's collision check exists so this is never reached from the UI
            }

            assertEquals(listOf("coffee", "tea"), repository.observeAllTags().first().map { it.name })
        }
}
