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
class TagDaoTest {
    private lateinit var db: HodithDatabase
    private lateinit var tagDao: TagDao
    private lateinit var eventDao: EventDao
    private var caseId: Long = 0
    private var eventId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            tagDao = db.tagDao()
            eventDao = db.eventDao()
            caseId = db.caseDao().insert(testCase())
            eventId = eventDao.insert(testEvent(caseId = caseId))
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun getByName_findsInsertedTag() =
        runTest {
            tagDao.insert(TagEntity(name = "at-dinner"))

            assertEquals("at-dinner", tagDao.getByName("at-dinner")?.name)
        }

    @Test
    fun getByName_matchesRegardlessOfCasing() =
        runTest {
            tagDao.insert(TagEntity(name = "at-dinner"))

            assertEquals("at-dinner", tagDao.getByName("At-Dinner")?.name)
        }

    @Test
    fun observeTagsForEvent_returnsOnlyTagsLinkedToThatEvent() =
        runTest {
            val otherEventId = eventDao.insert(testEvent(caseId = caseId))
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            val otherTagId = tagDao.insert(TagEntity(name = "at-work"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = otherEventId, tagId = otherTagId))

            val tags = tagDao.observeTagsForEvent(eventId).first()

            assertEquals(listOf("at-dinner"), tags.map { it.name })
        }

    @Test
    fun observeTagsForCase_returnsDistinctTagsAcrossAllEventsInCase() =
        runTest {
            val secondEventId = eventDao.insert(testEvent(caseId = caseId))
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = secondEventId, tagId = tagId))

            val tags = tagDao.observeTagsForCase(caseId).first()

            assertEquals(listOf("at-dinner"), tags.map { it.name })
        }

    @Test
    fun getAll_returnsEveryTag() =
        runTest {
            tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insert(TagEntity(name = "at-work"))

            val all = tagDao.getAll()

            assertEquals(listOf("at-dinner", "at-work"), all.map { it.name })
        }

    @Test
    fun getAllEventTags_returnsEveryCrossRef() =
        runTest {
            val otherEventId = eventDao.insert(testEvent(caseId = caseId))
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = otherEventId, tagId = tagId))

            val all = tagDao.getAllEventTags()

            assertEquals(2, all.size)
        }

    @Test
    fun deletingEvent_cascadesToEventTagCrossRefButKeepsTag() =
        runTest {
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

            eventDao.delete(eventDao.getById(eventId)!!)

            assertEquals(emptyList<TagEntity>(), tagDao.observeTagsForEvent(eventId).first())
            assertEquals("at-dinner", tagDao.getByName("at-dinner")?.name)
        }

    @Test
    fun observeTagEventCounts_countsAttachmentsAcrossAllCasesAndOmitsUnattachedTags() =
        runTest {
            val archivedCaseId = db.caseDao().insert(testCase(archived = true))
            val archivedEventId = eventDao.insert(testEvent(caseId = archivedCaseId))
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insert(TagEntity(name = "unused"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = archivedEventId, tagId = tagId))

            val counts = tagDao.observeTagEventCounts().first().associate { it.tagId to it.eventCount }

            assertEquals(mapOf(tagId to 2), counts)
        }

    @Test
    fun countEventsWithBoth_countsOnlyEventsCarryingBothTags() =
        runTest {
            val otherEventId = eventDao.insert(testEvent(caseId = caseId))
            val sourceId = tagDao.insert(TagEntity(name = "espresso"))
            val targetId = tagDao.insert(TagEntity(name = "coffee"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = sourceId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = targetId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = otherEventId, tagId = sourceId))

            assertEquals(1, tagDao.countEventsWithBoth(sourceId, targetId))
        }

    @Test
    fun findOtherTagByName_matchesCaseInsensitivelyAndExcludesTheGivenTag() =
        runTest {
            val coffeeId = tagDao.insert(TagEntity(name = "Coffee"))

            assertNull(tagDao.findOtherTagByName("coffee", excludeId = coffeeId))
            assertEquals("Coffee", tagDao.findOtherTagByName("coffee", excludeId = 0L)?.name)
        }

    @Test
    fun findOtherTagByName_prefersAnExactSpellingWhenSeveralTagsMatchIgnoringCase() =
        runTest {
            tagDao.insert(TagEntity(name = "COFFEE"))
            tagDao.insert(TagEntity(name = "coffee"))

            assertEquals("coffee", tagDao.findOtherTagByName("coffee", excludeId = 0L)?.name)
        }

    @Test
    fun rename_updatesTheNameOnTheSameRow() =
        runTest {
            val tagId = tagDao.insert(TagEntity(name = "coffe"))

            tagDao.rename(tagId, "coffee")

            assertEquals(listOf("coffee"), tagDao.getAll().map { it.name })
            assertEquals(tagId, tagDao.getAll().single().id)
        }

    @Test
    fun reassignEventTags_movesAttachmentsAndSkipsEventsThatAlreadyHaveTheTarget() =
        runTest {
            val otherEventId = eventDao.insert(testEvent(caseId = caseId))
            val sourceId = tagDao.insert(TagEntity(name = "espresso"))
            val targetId = tagDao.insert(TagEntity(name = "coffee"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = sourceId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = targetId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = otherEventId, tagId = sourceId))

            tagDao.reassignEventTags(sourceId = sourceId, targetId = targetId)

            val targetRows = tagDao.getAllEventTags().filter { it.tagId == targetId }
            assertEquals(setOf(eventId, otherEventId), targetRows.map { it.eventId }.toSet())
            assertEquals(2, targetRows.size)
        }

    @Test
    fun deleteById_cascadesEventTagsButKeepsEvents() =
        runTest {
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

            tagDao.deleteById(tagId)

            assertEquals(emptyList<EventTagCrossRef>(), tagDao.getAllEventTags())
            assertEquals(eventId, eventDao.getById(eventId)?.id)
        }

    @Test
    fun deleteById_leavesOtherTagsAttachedToTheSameEvent() =
        runTest {
            val doomedId = tagDao.insert(TagEntity(name = "doomed"))
            val keptId = tagDao.insert(TagEntity(name = "kept"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = doomedId))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = keptId))

            tagDao.deleteById(doomedId)

            assertEquals(listOf("kept"), tagDao.observeTagsForEvent(eventId).first().map { it.name })
        }

    @Test
    fun findOtherTagByName_foldsOnlyAsciiCase_soNonAsciiLettersStayDistinct() =
        runTest {
            tagDao.insert(TagEntity(name = "Ä"))

            assertNull(tagDao.findOtherTagByName("ä", excludeId = 0L))
        }
}
