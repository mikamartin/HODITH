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
class HunchDaoTest {
    private lateinit var db: HodithDatabase
    private lateinit var hunchDao: HunchDao
    private var caseId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            hunchDao = db.hunchDao()
            caseId = db.caseDao().insert(testCase())
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun insertAndUpdate() =
        runTest {
            val id = hunchDao.insert(testHunch(caseId = caseId, expectedCount = 3))
            val loaded = hunchDao.observeActiveHunch(caseId).first()!!

            hunchDao.update(loaded.copy(expectedCount = 5))

            assertEquals(5, hunchDao.observeActiveHunch(caseId).first()?.expectedCount)
            assertEquals(id, loaded.id)
        }

    @Test
    fun delete_removesHunch() =
        runTest {
            hunchDao.insert(testHunch(caseId = caseId))
            val loaded = hunchDao.observeActiveHunch(caseId).first()!!

            hunchDao.delete(loaded)

            assertNull(hunchDao.observeActiveHunch(caseId).first())
        }

    @Test
    fun observeActiveHunch_ignoresResolvedHunches() =
        runTest {
            hunchDao.insert(testHunch(caseId = caseId, createdAt = 0L, resolvedAt = 100L))

            assertNull(hunchDao.observeActiveHunch(caseId).first())
        }

    @Test
    fun getActiveHunch_ignoresResolvedHunches() =
        runTest {
            hunchDao.insert(testHunch(caseId = caseId, createdAt = 0L, resolvedAt = 100L))

            assertNull(hunchDao.getActiveHunch(caseId))
        }

    @Test
    fun getActiveHunch_returnsTheUnresolvedHunch() =
        runTest {
            val id = hunchDao.insert(testHunch(caseId = caseId, expectedCount = 3))

            assertEquals(id, hunchDao.getActiveHunch(caseId)?.id)
        }

    @Test
    fun getAll_returnsEveryHunchRegardlessOfResolvedStatus() =
        runTest {
            hunchDao.insert(testHunch(caseId = caseId, createdAt = 0L, resolvedAt = 50L))
            hunchDao.insert(testHunch(caseId = caseId, createdAt = 100L))

            val all = hunchDao.getAll()

            assertEquals(2, all.size)
        }

    @Test
    fun observeHunchHistory_returnsAllHunchesNewestFirst() =
        runTest {
            hunchDao.insert(testHunch(caseId = caseId, createdAt = 0L, resolvedAt = 50L))
            hunchDao.insert(testHunch(caseId = caseId, createdAt = 100L))

            val history = hunchDao.observeHunchHistory(caseId).first()

            assertEquals(listOf(100L, 0L), history.map { it.createdAt })
        }
}
