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
 * The lean projections the Big Picture grid reads instead of the full `CaseWithEventsAndTags`
 * `@Relation` cascade (F2). The grid's mapping logic is JVM-tested in `BigPictureViewModelTest`;
 * this proves the queries feed it the right rows — the `events JOIN cases` timing/intensity/note
 * projection and the flat `event_tags JOIN tags` attachment projection, both excluding archived
 * Cases.
 */
@RunWith(AndroidJUnit4::class)
class BigPictureQueriesTest {
    private lateinit var db: HodithDatabase
    private lateinit var caseDao: CaseDao
    private lateinit var eventDao: EventDao
    private lateinit var tagDao: TagDao

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        caseDao = db.caseDao()
        eventDao = db.eventDao()
        tagDao = db.tagDao()
    }

    @After
    fun tearDown() = db.close()

    @Smoke
    @Test
    fun observeActiveCaseEventDetails_projectsTimingIntensityAndNote() =
        runTest {
            val caseId = caseDao.insert(testCase(name = "Coffee"))
            val eventId =
                eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L, endedAt = 400L, intensity = 3, note = "felt fine"))

            val details = eventDao.observeActiveCaseEventDetails().first()

            assertEquals(
                listOf(
                    CaseEventDetail(id = eventId, caseId = caseId, occurredAt = 100L, endedAt = 400L, intensity = 3, note = "felt fine"),
                ),
                details,
            )
        }

    @Test
    fun observeActiveCaseEventDetails_excludesArchivedCasesEvents() =
        runTest {
            val active = caseDao.insert(testCase(name = "Active"))
            val archived = caseDao.insert(testCase(name = "Archived", archived = true))
            eventDao.insert(testEvent(caseId = active, occurredAt = 1L))
            eventDao.insert(testEvent(caseId = archived, occurredAt = 2L))

            val details = eventDao.observeActiveCaseEventDetails().first()

            assertEquals(listOf(active), details.map { it.caseId })
        }

    @Smoke
    @Test
    fun observeActiveCaseEventTagNames_flatMapsEachAttachmentToEventIdAndName() =
        runTest {
            val caseId = caseDao.insert(testCase(name = "Coffee"))
            val taggedEventId = eventDao.insert(testEvent(caseId = caseId, occurredAt = 100L))
            eventDao.insert(testEvent(caseId = caseId, occurredAt = 200L))
            val workTagId = tagDao.insert(TagEntity(name = "work"))
            val lateTagId = tagDao.insert(TagEntity(name = "late night"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = taggedEventId, tagId = workTagId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = taggedEventId, tagId = lateTagId))

            val tagNames = tagDao.observeActiveCaseEventTagNames().first()

            assertEquals(
                setOf(
                    EventTagName(taggedEventId, "work"),
                    EventTagName(taggedEventId, "late night"),
                ),
                tagNames.toSet(),
            )
        }

    @Test
    fun observeActiveCaseEventTagNames_excludesArchivedCasesEvents() =
        runTest {
            val archived = caseDao.insert(testCase(name = "Archived", archived = true))
            val eventId = eventDao.insert(testEvent(caseId = archived, occurredAt = 1L))
            val tagId = tagDao.insert(TagEntity(name = "work"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

            val tagNames = tagDao.observeActiveCaseEventTagNames().first()

            assertEquals(emptyList<EventTagName>(), tagNames)
        }
}
