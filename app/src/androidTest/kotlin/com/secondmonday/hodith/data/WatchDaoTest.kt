package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchDaoTest {
    private lateinit var db: HodithDatabase
    private lateinit var watchDao: WatchDao
    private var caseId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            watchDao = db.watchDao()
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
            watchDao.insert(testWatch(caseId = caseId, threshold = 3))
            val loaded = watchDao.observeWatchesForCase(caseId).first().single()

            watchDao.update(loaded.copy(threshold = 5))

            assertEquals(
                5,
                watchDao
                    .observeWatchesForCase(caseId)
                    .first()
                    .single()
                    .threshold,
            )
        }

    @Test
    fun delete_removesWatch() =
        runTest {
            watchDao.insert(testWatch(caseId = caseId))
            val loaded = watchDao.observeWatchesForCase(caseId).first().single()

            watchDao.delete(loaded)

            assertTrue(watchDao.observeWatchesForCase(caseId).first().isEmpty())
        }

    @Test
    fun getEnabledWatches_excludesDisabled() =
        runTest {
            watchDao.insert(testWatch(caseId = caseId, enabled = true))
            watchDao.insert(testWatch(caseId = caseId, enabled = false))

            val enabled = watchDao.getEnabledWatches()

            assertEquals(1, enabled.size)
            assertTrue(enabled.all { it.enabled })
        }

    @Test
    fun getById_returnsMatchingWatch() =
        runTest {
            val id = watchDao.insert(testWatch(caseId = caseId, threshold = 9))

            assertEquals(9, watchDao.getById(id)?.threshold)
        }

    @Test
    fun getById_returnsNullWhenMissing() =
        runTest {
            assertEquals(null, watchDao.getById(id = 12345L))
        }

    @Test
    fun getAll_returnsEveryWatchAcrossAllCases() =
        runTest {
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            watchDao.insert(testWatch(caseId = caseId))
            watchDao.insert(testWatch(caseId = otherCaseId))

            val all = watchDao.getAll()

            assertEquals(2, all.size)
        }

    @Test
    fun getWatchesForCase_scopesToCase() =
        runTest {
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            watchDao.insert(testWatch(caseId = caseId))
            watchDao.insert(testWatch(caseId = otherCaseId))

            val forCase = watchDao.getWatchesForCase(caseId)

            assertEquals(1, forCase.size)
            assertTrue(forCase.all { it.caseId == caseId })
        }
}
