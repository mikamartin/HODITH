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

    @Test
    fun getByName_findsInsertedTag() =
        runTest {
            tagDao.insert(TagEntity(name = "at-dinner"))

            assertEquals("at-dinner", tagDao.getByName("at-dinner")?.name)
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
    fun deletingEvent_cascadesToEventTagCrossRefButKeepsTag() =
        runTest {
            val tagId = tagDao.insert(TagEntity(name = "at-dinner"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

            eventDao.delete(eventDao.getById(eventId)!!)

            assertEquals(emptyList<TagEntity>(), tagDao.observeTagsForEvent(eventId).first())
            assertEquals("at-dinner", tagDao.getByName("at-dinner")?.name)
        }
}
