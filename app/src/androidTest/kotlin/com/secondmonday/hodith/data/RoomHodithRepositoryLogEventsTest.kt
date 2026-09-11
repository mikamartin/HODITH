package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/**
 * [RoomHodithRepository.observeLogEventsForCase] against a real Room database — the DAO-method
 * dispatch by [LogSortOrder], the `durationMode == DurationMode.START_STOP` mapping to the DAO's
 * `isStartStopCase` parameter, and the `limit + 1` fetch-and-trim that computes
 * [LogEventsPage.hasMore] are this repository's own logic, not proven by [EventDaoTest] (which
 * only exercises the raw paged queries with a boolean handed to it directly) or
 * [com.secondmonday.hodith.data.FakeHodithRepositoryTest] (a separate, parallel reimplementation
 * of the same contract, not this code).
 */
@RunWith(AndroidJUnit4::class)
class RoomHodithRepositoryLogEventsTest {
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
                    hunchDao = db.hunchDao(),
                    triggerDao = db.triggerDao(),
                    notificationEvalScheduler = unusedScheduler(),
                )
            caseId = db.caseDao().insert(testCase())
        }

    private fun unusedScheduler() =
        NotificationEvalScheduler(
            scope = CoroutineScope(Dispatchers.Unconfined),
            evaluator = Provider { error("not used by observeLogEventsForCase") },
        )

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun observeLogEventsForCase_capsAtLimitAndReportsHasMore() =
        runTest {
            repeat(5) { i -> db.eventDao().insert(testEvent(caseId = caseId, occurredAt = i.toLong())) }

            val page =
                repository
                    .observeLogEventsForCase(caseId, LogSortOrder.BY_START, limit = 3, durationMode = DurationMode.NONE)
                    .first()

            assertEquals(listOf(4L, 3L, 2L), page.events.map { it.event.occurredAt })
            assertTrue(page.hasMore)
        }

    @Test
    fun observeLogEventsForCase_reportsHasMoreFalse_whenEveryEventIsAlreadyLoaded() =
        runTest {
            repeat(3) { i -> db.eventDao().insert(testEvent(caseId = caseId, occurredAt = i.toLong())) }

            val page =
                repository
                    .observeLogEventsForCase(caseId, LogSortOrder.BY_START, limit = 3, durationMode = DurationMode.NONE)
                    .first()

            assertEquals(3, page.events.size)
            assertFalse(page.hasMore)
        }

    @Test
    fun observeLogEventsForCase_BY_END_dispatchesToThePagedByEndQuery() =
        runTest {
            val running = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val finished = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 250L))

            val page =
                repository
                    .observeLogEventsForCase(caseId, LogSortOrder.BY_END, limit = 10, durationMode = DurationMode.START_STOP)
                    .first()

            assertEquals(listOf(running, finished), page.events.map { it.event.id })
        }

    @Test
    fun observeLogEventsForCase_mapsDurationModeToIsStartStopCase_forTheBY_ENDQuery() =
        runTest {
            // Same shape as the dispatch test above, but MANUAL rather than START_STOP: the
            // end-less event must NOT float. This proves the repository's own `durationMode ==
            // DurationMode.START_STOP` line actually gates the floating behaviour, rather than
            // just trusting EventDaoTest's direct `isStartStopCase` boolean cases.
            val openEnded = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = null))
            val laterFinished = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 200L, endedAt = 250L))

            val page =
                repository
                    .observeLogEventsForCase(caseId, LogSortOrder.BY_END, limit = 10, durationMode = DurationMode.MANUAL)
                    .first()

            assertEquals(listOf(laterFinished, openEnded), page.events.map { it.event.id })
        }
}
