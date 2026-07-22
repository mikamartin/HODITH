package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDaoTest {
    private lateinit var db: HodithDatabase
    private lateinit var eventDao: EventDao
    private var caseId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            eventDao = db.eventDao()
            caseId = db.caseDao().insert(testCase())
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun insertAndGetById() =
        runTest {
            val id = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))

            val loaded = eventDao.getById(id)

            assertEquals(100L, loaded?.occurredAt)
        }

    @Test
    fun update_persistsChanges() =
        runTest {
            val id = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val loaded = eventDao.getById(id)!!

            eventDao.update(loaded.copy(note = "felt awful"))

            assertEquals("felt awful", eventDao.getById(id)?.note)
        }

    @Test
    fun delete_removesEvent() =
        runTest {
            val id = eventDao.insert(testEvent(caseId = caseId))
            val loaded = eventDao.getById(id)!!

            eventDao.delete(loaded)

            assertNull(eventDao.getById(id))
        }

    @Test
    fun observeEventsForCase_ordersNewestFirst() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))

            val events = eventDao.observeEventsForCase(caseId).first()

            assertEquals(listOf(300L, 200L, 100L), events.map { it.occurredAt })
        }

    @Test
    fun eventsInWindow_excludesEventsOutsideRange() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 50L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 150L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 250L))

            val inWindow = eventDao.eventsInWindow(caseId, windowStart = 100L, windowEnd = 200L)

            assertEquals(listOf(150L), inWindow.map { it.occurredAt })
        }

    @Test
    fun getOngoingEvent_returnsEventWithNullEndedAt() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 200L))
            val ongoingId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L, endedAt = null))

            val ongoing = eventDao.getOngoingEvent(caseId)

            assertEquals(ongoingId, ongoing?.id)
        }

    @Test
    fun observeEventsWithTagsForCase_bundlesEachEventsOwnTagsOnly() =
        runTest {
            val tagDao = db.tagDao()
            val taggedId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val untaggedId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = taggedId, tagId = tagId))

            val events = eventDao.observeEventsWithTagsForCase(caseId).first()

            val tagged = events.single { it.event.id == taggedId }
            val untagged = events.single { it.event.id == untaggedId }
            assertEquals(listOf("at-dinner"), tagged.tags.map { it.name })
            assertEquals(emptyList<TagEntity>(), untagged.tags)
        }

    @Test
    fun observeEventsWithTagsForCase_ordersNewestFirst() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))

            val events = eventDao.observeEventsWithTagsForCase(caseId).first()

            assertEquals(listOf(300L, 200L, 100L), events.map { it.event.occurredAt })
        }
}
