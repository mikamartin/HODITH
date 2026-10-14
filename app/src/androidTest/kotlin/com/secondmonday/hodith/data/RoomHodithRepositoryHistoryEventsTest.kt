package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [RoomHodithRepository.observeHistoryEventsForCase] against a real Room database — the DAO-method
 * dispatch by [HistorySortOrder], the `tracksDuration` pass-through to the DAO's own parameter of
 * the same name, and the `limit + 1` fetch-and-trim that computes [HistoryEventsPage.hasMore] are
 * this repository's own logic, not proven by [EventDaoTest] (which only exercises the raw paged
 * queries with a boolean handed to it directly) or
 * [com.secondmonday.hodith.data.FakeHodithRepositoryTest] (a separate, parallel reimplementation
 * of the same contract, not this code).
 */
@RunWith(AndroidJUnit4::class)
class RoomHodithRepositoryHistoryEventsTest {
    private lateinit var db: HodithDatabase
    private lateinit var repository: RoomHodithRepository
    private var caseId: Long = 0

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
                    notificationEvalScheduler = unusedScheduler("not used by observeHistoryEventsForCase"),
                )
            caseId = db.caseDao().insert(testCase())
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun observeHistoryEventsForCase_capsAtLimitAndReportsHasMore() =
        runTest {
            repeat(5) { i -> db.eventDao().insert(testEvent(caseId = caseId, occurredAt = i.toLong())) }

            val page =
                repository
                    .observeHistoryEventsForCase(
                        caseId,
                        HistorySortOrder.BY_START,
                        limit = 3,
                        tracksDuration = false,
                        dateFrom = null,
                        dateTo = null,
                    ).first()

            assertEquals(listOf(4L, 3L, 2L), page.events.map { it.event.occurredAt })
            assertTrue(page.hasMore)
        }

    @Test
    fun observeHistoryEventsForCase_reportsHasMoreFalse_whenEveryEventIsAlreadyLoaded() =
        runTest {
            repeat(3) { i -> db.eventDao().insert(testEvent(caseId = caseId, occurredAt = i.toLong())) }

            val page =
                repository
                    .observeHistoryEventsForCase(
                        caseId,
                        HistorySortOrder.BY_START,
                        limit = 3,
                        tracksDuration = false,
                        dateFrom = null,
                        dateTo = null,
                    ).first()

            assertEquals(3, page.events.size)
            assertFalse(page.hasMore)
        }

    @Test
    fun observeHistoryEventsForCase_BY_END_dispatchesToThePagedByEndQuery() =
        runTest {
            val running = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val finished = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 250L))

            val page =
                repository
                    .observeHistoryEventsForCase(
                        caseId,
                        HistorySortOrder.BY_END,
                        limit = 10,
                        tracksDuration = true,
                        dateFrom = null,
                        dateTo = null,
                    ).first()

            assertEquals(listOf(running, finished), page.events.map { it.event.id })
        }

    @Test
    fun observeHistoryEventsForCase_neverFloatsAnOpenEndedEvent_forACaseThatDoesNotTrackDuration() =
        runTest {
            // Same shape as the dispatch test above, but tracksDuration = false: the end-less
            // event must NOT float. This proves the repository's own `tracksDuration` pass-through
            // actually gates the floating behaviour, rather than just trusting EventDaoTest's
            // direct boolean cases.
            val openEnded = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val laterFinished = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 250L))

            val page =
                repository
                    .observeHistoryEventsForCase(
                        caseId,
                        HistorySortOrder.BY_END,
                        limit = 10,
                        tracksDuration = false,
                        dateFrom = null,
                        dateTo = null,
                    ).first()

            assertEquals(listOf(laterFinished, openEnded), page.events.map { it.event.id })
        }

    @Test
    fun observeHistoryEventsForCase_narrowsToAnInclusiveDateFromDateToRange_unboundedOnEitherNullSide() =
        runTest {
            repeat(5) { i -> db.eventDao().insert(testEvent(caseId = caseId, occurredAt = i.toLong())) }

            val narrowed =
                repository
                    .observeHistoryEventsForCase(
                        caseId,
                        HistorySortOrder.BY_START,
                        limit = 10,
                        tracksDuration = false,
                        dateFrom = 1L,
                        dateTo = 3L,
                    ).first()
            assertEquals(listOf(3L, 2L, 1L), narrowed.events.map { it.event.occurredAt })
            assertFalse(narrowed.hasMore)

            val unboundedTo =
                repository
                    .observeHistoryEventsForCase(
                        caseId,
                        HistorySortOrder.BY_START,
                        limit = 10,
                        tracksDuration = false,
                        dateFrom = 3L,
                        dateTo = null,
                    ).first()
            assertEquals(listOf(4L, 3L), unboundedTo.events.map { it.event.occurredAt })
        }
}
