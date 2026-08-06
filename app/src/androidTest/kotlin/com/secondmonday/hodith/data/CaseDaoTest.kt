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
class CaseDaoTest {
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
    fun insertAndGetById() =
        runTest {
            val id = caseDao.insert(testCase(name = "Migraines"))

            val loaded = caseDao.getById(id)

            assertEquals("Migraines", loaded?.name)
        }

    @Test
    fun insertAndGetById_persistsOptionalDescription() =
        runTest {
            val id = caseDao.insert(testCase(name = "Migraines", description = "Started after the move"))

            val loaded = caseDao.getById(id)

            assertEquals("Started after the move", loaded?.description)
        }

    @Test
    fun update_persistsChanges() =
        runTest {
            val id = caseDao.insert(testCase(name = "Migraines"))
            val loaded = caseDao.getById(id)!!

            caseDao.update(loaded.copy(name = "Bad Migraines", archived = true))

            val updated = caseDao.getById(id)
            assertEquals("Bad Migraines", updated?.name)
            assertEquals(true, updated?.archived)
        }

    @Test
    fun delete_removesCase() =
        runTest {
            val id = caseDao.insert(testCase())
            val loaded = caseDao.getById(id)!!

            caseDao.delete(loaded)

            assertNull(caseDao.getById(id))
        }

    @Test
    fun observeActiveCases_excludesArchivedAndOrdersBySortOrder() =
        runTest {
            caseDao.insert(testCase(name = "Second", sortOrder = 1))
            caseDao.insert(testCase(name = "First", sortOrder = 0))
            caseDao.insert(testCase(name = "Archived", sortOrder = -1, archived = true))

            val active = caseDao.observeActiveCases().first()

            assertEquals(listOf("First", "Second"), active.map { it.name })
        }

    @Test
    fun getActiveCases_excludesArchivedAndOrdersBySortOrder() =
        runTest {
            caseDao.insert(testCase(name = "Second", sortOrder = 1))
            caseDao.insert(testCase(name = "First", sortOrder = 0))
            caseDao.insert(testCase(name = "Archived", sortOrder = -1, archived = true))

            val active = caseDao.getActiveCases()

            assertEquals(listOf("First", "Second"), active.map { it.name })
        }

    @Test
    fun observeArchivedCasesWithEvents_returnsOnlyArchivedOrderedByNameWithEventCounts() =
        runTest {
            val eventDao = db.eventDao()
            caseDao.insert(testCase(name = "Zebra", archived = true))
            val migraineId = caseDao.insert(testCase(name = "migraines", archived = true))
            caseDao.insert(testCase(name = "Active", archived = false))
            eventDao.insert(testEvent(caseId = migraineId))
            eventDao.insert(testEvent(caseId = migraineId, occurredAt = 1L, loggedAt = 1L))

            val archived = caseDao.observeArchivedCasesWithEvents().first()

            assertEquals(listOf("migraines", "Zebra"), archived.map { it.case.name })
            assertEquals(2, archived.first { it.case.name == "migraines" }.events.size)
            assertEquals(0, archived.first { it.case.name == "Zebra" }.events.size)
        }

    @Test
    fun deletingCase_cascadesToEventsHunchesAndTriggers() =
        runTest {
            val caseId = caseDao.insert(testCase())
            val eventDao = db.eventDao()
            val hunchDao = db.hunchDao()
            val triggerDao = db.triggerDao()
            eventDao.insert(testEvent(caseId = caseId))
            hunchDao.insert(testHunch(caseId = caseId))
            triggerDao.insert(testTrigger(caseId = caseId))

            caseDao.delete(caseDao.getById(caseId)!!)

            assertEquals(emptyList<EventEntity>(), eventDao.observeEventsForCase(caseId).first())
            assertEquals(null, hunchDao.observeActiveHunch(caseId).first())
            assertEquals(emptyList<TriggerEntity>(), triggerDao.observeTriggersForCase(caseId).first())
        }

    @Test
    fun deleteAll_removesEveryCaseAndCascadesToEvents() =
        runTest {
            val eventDao = db.eventDao()
            val firstId = caseDao.insert(testCase(name = "First"))
            val secondId = caseDao.insert(testCase(name = "Second", archived = true))
            eventDao.insert(testEvent(caseId = firstId))
            eventDao.insert(testEvent(caseId = secondId))

            caseDao.deleteAll()

            assertEquals(emptyList<CaseEntity>(), caseDao.observeActiveCases().first())
            assertEquals(emptyList<EventEntity>(), eventDao.observeEventsForCase(firstId).first())
            assertEquals(emptyList<EventEntity>(), eventDao.observeEventsForCase(secondId).first())
        }

    @Test
    fun getAll_returnsEveryCaseRegardlessOfArchivedStatus() =
        runTest {
            caseDao.insert(testCase(name = "Active", archived = false))
            caseDao.insert(testCase(name = "Archived", archived = true))

            val all = caseDao.getAll()

            assertEquals(setOf("Active", "Archived"), all.map { it.name }.toSet())
        }

    @Test
    fun caseDaoDeleteAll_doesNotOnItsOwnRemoveTags() =
        runTest {
            // Documents why RoomHodithRepository.deleteAllData() must also call tagDao.deleteAll():
            // tags aren't scoped to a case, so caseDao.deleteAll() alone leaves them orphaned.
            val tagDao = db.tagDao()
            val eventDao = db.eventDao()
            val caseId = caseDao.insert(testCase())
            val eventId = eventDao.insert(testEvent(caseId = caseId))
            val tagId = tagDao.insert(TagEntity(name = "home"))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

            caseDao.deleteAll()

            assertEquals(1, tagDao.observeAllTags().first().size)
        }
}
