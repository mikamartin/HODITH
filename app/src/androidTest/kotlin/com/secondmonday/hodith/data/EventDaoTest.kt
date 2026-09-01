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
    fun eventsInWindow_includesAnEventAtExactlyWindowStart() =
        runTest {
            val id = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))

            val inWindow = eventDao.eventsInWindow(caseId, windowStart = 100L, windowEnd = 200L)

            assertEquals(listOf(id), inWindow.map { it.id })
        }

    @Test
    fun eventsInWindow_excludesAnEventAtExactlyWindowEnd() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))

            val inWindow = eventDao.eventsInWindow(caseId, windowStart = 100L, windowEnd = 200L)

            assertEquals(emptyList<Long>(), inWindow.map { it.id })
        }

    @Test
    fun getMostRecentEventForCase_returnsTheLatestByOccurredAt() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val latestId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))

            val mostRecent = eventDao.getMostRecentEventForCase(caseId)

            assertEquals(latestId, mostRecent?.id)
        }

    @Test
    fun getMostRecentEventForCase_returnsNullWithNoEvents() =
        runTest {
            val mostRecent = eventDao.getMostRecentEventForCase(caseId)

            assertNull(mostRecent)
        }

    @Test
    fun getLatestEventEndForCase_takesEndedAtOverALaterStart() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 900L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L, endedAt = 400L))

            assertEquals(900L, eventDao.getLatestEventEndForCase(caseId))
        }

    @Test
    fun getLatestEventEndForCase_fallsBackToStartForAPointOrOpenEvent() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 500L, endedAt = null))

            assertEquals(500L, eventDao.getLatestEventEndForCase(caseId))
        }

    @Test
    fun getLatestEventEndForCase_takesAnOpenEventStartOverAnEarlierClosedEventEnd() =
        runTest {
            // A still-open event started after an earlier one closed — its start is the latest point
            // reached, so an impl that only maxed non-null endedAt values would wrongly return 200.
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 200L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 500L, endedAt = null))

            assertEquals(500L, eventDao.getLatestEventEndForCase(caseId))
        }

    @Test
    fun getLatestEventEndForCase_returnsNullWithNoEvents() =
        runTest {
            assertNull(eventDao.getLatestEventEndForCase(caseId))
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
    fun getAll_returnsEveryEventAcrossAllCases() =
        runTest {
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = otherCaseId, occurredAt = 200L))

            val all = eventDao.getAll()

            assertEquals(setOf(100L, 200L), all.map { it.occurredAt }.toSet())
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
