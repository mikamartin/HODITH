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

@RunWith(AndroidJUnit4::class)
class CaseWithEventsAndTagsTest {
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

    @Smoke
    @Test
    fun observeActiveCasesWithEventsAndTags_nestsTagsUnderEachEvent() =
        runTest {
            val eventDao = db.eventDao()
            val tagDao = db.tagDao()
            val caseId = caseDao.insert(testCase(name = "Coffee"))
            val eventId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))
            val tagId = tagDao.insert(TagEntity(name = "late night"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

            val rows = caseDao.observeActiveCasesWithEventsAndTags().first()

            assertEquals(1, rows.size)
            val events = rows.single().events.sortedBy { it.event.occurredAt }
            assertEquals(listOf("late night"), events[0].tags.map { it.name })
            assertEquals(emptyList<TagEntity>(), events[1].tags)
        }

    @Test
    fun observeActiveCasesWithEventsAndTags_excludesArchivedCases() =
        runTest {
            caseDao.insert(testCase(name = "Archived", archived = true))

            val rows = caseDao.observeActiveCasesWithEventsAndTags().first()

            assertEquals(emptyList<CaseWithEventsAndTags>(), rows)
        }
}
