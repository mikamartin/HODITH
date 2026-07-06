package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaseWithEventsTest {
    private lateinit var db: HodithDatabase
    private lateinit var caseDao: CaseDao

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        caseDao = db.caseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeActiveCasesWithEvents_groupsEventsUnderTheirCaseAndOrdersBySortOrder() =
        runTest {
            val eventDao = db.eventDao()
            val firstCaseId = caseDao.insert(testCase(name = "First", sortOrder = 0))
            val secondCaseId = caseDao.insert(testCase(name = "Second", sortOrder = 1))
            eventDao.insert(testEvent(caseId = firstCaseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = firstCaseId, occurredAt = 200L))
            eventDao.insert(testEvent(caseId = secondCaseId, occurredAt = 300L))

            val rows = caseDao.observeActiveCasesWithEvents().first()

            assertEquals(listOf("First", "Second"), rows.map { it.case.name })
            assertEquals(2, rows[0].events.size)
            assertEquals(1, rows[1].events.size)
        }

    @Test
    fun observeActiveCasesWithEvents_excludesArchivedCases() =
        runTest {
            caseDao.insert(testCase(name = "Archived", archived = true))

            val rows = caseDao.observeActiveCasesWithEvents().first()

            assertEquals(emptyList<CaseWithEvents>(), rows)
        }

    @Test
    fun observeActiveCasesWithEvents_includesCasesWithNoEvents() =
        runTest {
            caseDao.insert(testCase(name = "Quiet case"))

            val rows = caseDao.observeActiveCasesWithEvents().first()

            assertEquals(1, rows.size)
            assertEquals(emptyList<EventEntity>(), rows.single().events)
        }
}
