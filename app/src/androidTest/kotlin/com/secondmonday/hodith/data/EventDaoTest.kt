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

    @Test
    fun observeEventsWithTagsForCasePagedByStart_capsAtLimit() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))

            val page = eventDao.observeEventsWithTagsForCasePagedByStart(caseId, limit = 2).first()

            assertEquals(listOf(300L, 200L), page.map { it.event.occurredAt })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByStart_ordersById_whenOccurredAtTies() =
        runTest {
            val earlierId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val laterId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))

            val page = eventDao.observeEventsWithTagsForCasePagedByStart(caseId, limit = 10).first()

            assertEquals(listOf(laterId, earlierId), page.map { it.event.id })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_floatsARunningStartStopEventAboveAMoreRecentlyStartedFinishedOne() =
        runTest {
            val running = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val finished = eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 250L))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = true, limit = 10).first()

            assertEquals(listOf(running, finished), page.map { it.event.id })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_ignoresRunningState_whenNotAStartStopCase() =
        runTest {
            val openEnded = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val laterFinished = eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 250L))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = false, limit = 10).first()

            // isStartStopCase = false — no event floats regardless of endedAt, order falls back to
            // IFNULL(endedAt, occurredAt) descending: laterFinished ended at 250, openEnded's
            // fallback is its own occurredAt (100).
            assertEquals(listOf(laterFinished, openEnded), page.map { it.event.id })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_fallsBackToOccurredAt_forAnEndlessManualEntry() =
        runTest {
            val earlyButEndless = eventDao.insert(testEvent(caseId = caseId, occurredAt = 500L, endedAt = null))
            val laterAndFinished = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 200L))

            // A MANUAL Case is never "running" (isStartStopCase = false), so the end-less entry
            // orders by its own occurredAt (500), ahead of the other event's endedAt (200).
            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = false, limit = 10).first()

            assertEquals(listOf(earlyButEndless, laterAndFinished), page.map { it.event.id })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_capsAtLimit() =
        runTest {
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L, endedAt = 300L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 200L))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = true, limit = 2).first()

            assertEquals(listOf(300L, 200L), page.map { it.event.occurredAt })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_ordersSeveralRunningEventsByNewestStartFirst() =
        runTest {
            val id1 = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val id2 = eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L, endedAt = null))
            val id3 = eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = null))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = true, limit = 10).first()

            assertEquals(listOf(id2, id3, id1), page.map { it.event.id })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_ordersFinishedEventsByEndedAt_notByStart() =
        runTest {
            // Started last but ended first, vs. started first but ended last — proves the sort key
            // is endedAt, not occurredAt, for two events that are both already finished.
            val startedLaterEndedFirst = eventDao.insert(testEvent(caseId = caseId, occurredAt = 400L, endedAt = 450L))
            val startedFirstEndedLast = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 900L))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = true, limit = 10).first()

            assertEquals(listOf(startedFirstEndedLast, startedLaterEndedFirst), page.map { it.event.id })
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_ordersByOccurredAt_whenEndedAtTies() =
        runTest {
            val id1 = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 500L))
            val id2 = eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 500L))
            val id3 = eventDao.insert(testEvent(caseId = caseId, occurredAt = 150L, endedAt = 500L))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = true, limit = 10).first()

            assertEquals(listOf(id2, id3, id1), page.map { it.event.id })
        }

    @Test
    fun deleteOlderThan_removesOnlyEventsStrictlyBeforeCutoff() =
        runTest {
            val older = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val atCutoff = eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))
            val newer = eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L))

            eventDao.deleteOlderThan(cutoff = 200L)

            assertNull(eventDao.getById(older))
            assertEquals(200L, eventDao.getById(atCutoff)?.occurredAt)
            assertEquals(300L, eventDao.getById(newer)?.occurredAt)
        }

    @Test
    fun deleteOlderThan_cascadesEventTagsForDeletedEventsOnly() =
        runTest {
            val tagDao = db.tagDao()
            val deletedId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val keptId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 300L))
            val sharedTagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = deletedId, tagId = sharedTagId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = keptId, tagId = sharedTagId))

            eventDao.deleteOlderThan(cutoff = 200L)

            val remainingTags = eventDao.observeEventsWithTagsForCase(caseId).first()
            val kept = remainingTags.single { it.event.id == keptId }
            assertEquals(listOf("at-dinner"), kept.tags.map { it.name })
            assertEquals(1, remainingTags.size)
        }

    @Test
    fun deleteOlderThan_leavesAnOrphanedTagRowInPlace() =
        runTest {
            // A tag used only by the deleted event still shouldn't itself be removed from `tags` —
            // "tags untouched" is a documented invariant of this method, same shape as
            // deleteAllData()'s split between caseDao.deleteAll() and the separate tagDao.deleteAll().
            val tagDao = db.tagDao()
            val deletedId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val onlyTagId = tagDao.insert(TagEntity(name = "solo-tag"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = deletedId, tagId = onlyTagId))

            eventDao.deleteOlderThan(cutoff = 200L)

            assertEquals(listOf("solo-tag"), tagDao.getAll().map { it.name })
        }

    @Test
    fun deleteOlderThan_isNotScopedToASingleCase() =
        runTest {
            // deleteEventsOlderThan is a global bulk action (spec §14: "logs before a chosen date"),
            // not per-Case — this pins that it reaches every Case's events, not just the one the
            // Settings screen happens to be showing.
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            val olderInThisCase = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            val olderInOtherCase = eventDao.insert(testEvent(caseId = otherCaseId, occurredAt = 150L))
            val newerInOtherCase = eventDao.insert(testEvent(caseId = otherCaseId, occurredAt = 300L))

            eventDao.deleteOlderThan(cutoff = 200L)

            assertNull(eventDao.getById(olderInThisCase))
            assertNull(eventDao.getById(olderInOtherCase))
            assertEquals(300L, eventDao.getById(newerInOtherCase)?.occurredAt)
        }

    @Test
    fun deleteOlderThan_onAnEmptyTableIsANoOp() =
        runTest {
            eventDao.deleteOlderThan(cutoff = 200L)

            assertEquals(emptyList<EventEntity>(), eventDao.getAll())
        }

    @Test
    fun getCaseIdsWithEventsOlderThan_returnsDistinctAffectedCaseIds() =
        runTest {
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 150L))
            eventDao.insert(testEvent(caseId = otherCaseId, occurredAt = 300L))

            val affected = eventDao.getCaseIdsWithEventsOlderThan(cutoff = 200L)

            assertEquals(listOf(caseId), affected)
        }

    @Test
    fun observeEventsWithTagsForCasePagedByEnd_ordersById_whenOccurredAtAndEndedAtBothTie() =
        runTest {
            val earlierId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 500L))
            val laterId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 500L))

            val page = eventDao.observeEventsWithTagsForCasePagedByEnd(caseId, isStartStopCase = true, limit = 10).first()

            assertEquals(listOf(laterId, earlierId), page.map { it.event.id })
        }
}
