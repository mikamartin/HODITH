package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The lean projections Home / the widgets read instead of the full `CaseWithEvents` `@Relation`.
 * The today/this-week count *math* is JVM-tested in `HomeViewModelMappingTest`; this proves the
 * queries feed it the right rows — the `events JOIN cases` projection carrying `durationMode`, the
 * archived exclusion, and the scalar count.
 */
@RunWith(AndroidJUnit4::class)
class HomeCountQueriesTest {
    private lateinit var db: HodithDatabase
    private lateinit var caseDao: CaseDao
    private lateinit var eventDao: EventDao

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        caseDao = db.caseDao()
        eventDao = db.eventDao()
    }

    @After
    fun tearDown() = db.close()

    @Smoke
    @Test
    fun observeActiveCaseEventSpans_projectsTimingAndTheOwningCasesDurationMode() =
        runTest {
            val tracking = caseDao.insert(testCase(name = "Tracked", durationMode = DurationMode.START_STOP))
            val point = caseDao.insert(testCase(name = "Point", durationMode = DurationMode.NONE))
            eventDao.insert(testEvent(caseId = tracking, occurredAt = 100L, endedAt = 400L))
            eventDao.insert(testEvent(caseId = point, occurredAt = 200L, endedAt = null))

            val spans = eventDao.observeActiveCaseEventSpans().first().sortedBy { it.occurredAt }

            assertEquals(
                listOf(
                    CaseEventSpan(tracking, 100L, 400L, DurationMode.START_STOP),
                    CaseEventSpan(point, 200L, null, DurationMode.NONE),
                ),
                spans,
            )
        }

    @Test
    fun observeActiveCaseEventSpans_excludesArchivedCasesEvents() =
        runTest {
            val active = caseDao.insert(testCase(name = "Active"))
            val archived = caseDao.insert(testCase(name = "Archived", archived = true))
            eventDao.insert(testEvent(caseId = active, occurredAt = 1L))
            eventDao.insert(testEvent(caseId = archived, occurredAt = 2L))

            val spans = eventDao.observeActiveCaseEventSpans().first()

            assertEquals(listOf(active), spans.map { it.caseId })
        }

    @Test
    fun observeOpenEvents_returnsOnlyEndlessEventsEarliestFirstAcrossCases() =
        runTest {
            val a = caseDao.insert(testCase(name = "A"))
            val b = caseDao.insert(testCase(name = "B"))
            eventDao.insert(testEvent(caseId = a, occurredAt = 500L, endedAt = null))
            eventDao.insert(testEvent(caseId = b, occurredAt = 100L, endedAt = null))
            eventDao.insert(testEvent(caseId = a, occurredAt = 300L, endedAt = 900L))

            val open = eventDao.observeOpenEvents().first()

            assertEquals(listOf(100L, 500L), open.map { it.occurredAt })
        }

    @Test
    fun observeArchivedCaseCount_countsArchivedOnlyAndUpdatesReactively() =
        runTest {
            caseDao.insert(testCase(name = "Active"))
            val toArchive = caseDao.insert(testCase(name = "Soon gone"))
            caseDao.insert(testCase(name = "Already gone", archived = true))

            assertEquals(1, caseDao.observeArchivedCaseCount().first())

            caseDao.update(db.caseDao().getById(toArchive)!!.copy(archived = true))

            assertEquals(2, caseDao.observeArchivedCaseCount().first())
        }
}
