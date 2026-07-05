package com.secondmonday.hodit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private lateinit var db: HoditDatabase
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
    fun insertAndGetById() =
        runTest {
            val id = caseDao.insert(testCase(name = "Migraines"))

            val loaded = caseDao.getById(id)

            assertEquals("Migraines", loaded?.name)
        }

    @Test
    fun update_persistsChanges() =
        runTest {
            val id = caseDao.insert(testCase(name = "Migraines"))
            val loaded = caseDao.getById(id)!!

            caseDao.update(loaded.copy(name = "Bad Migraines", pinned = true))

            val updated = caseDao.getById(id)
            assertEquals("Bad Migraines", updated?.name)
            assertEquals(true, updated?.pinned)
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
}
