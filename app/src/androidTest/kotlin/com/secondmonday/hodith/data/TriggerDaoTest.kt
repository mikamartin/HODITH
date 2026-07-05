package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TriggerDaoTest {
    private lateinit var db: HodithDatabase
    private lateinit var triggerDao: TriggerDao
    private var caseId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            triggerDao = db.triggerDao()
            caseId = db.caseDao().insert(testCase())
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndUpdate() =
        runTest {
            triggerDao.insert(testTrigger(caseId = caseId, threshold = 3))
            val loaded = triggerDao.observeTriggersForCase(caseId).first().single()

            triggerDao.update(loaded.copy(threshold = 5))

            assertEquals(
                5,
                triggerDao
                    .observeTriggersForCase(caseId)
                    .first()
                    .single()
                    .threshold,
            )
        }

    @Test
    fun delete_removesTrigger() =
        runTest {
            triggerDao.insert(testTrigger(caseId = caseId))
            val loaded = triggerDao.observeTriggersForCase(caseId).first().single()

            triggerDao.delete(loaded)

            assertTrue(triggerDao.observeTriggersForCase(caseId).first().isEmpty())
        }

    @Test
    fun getEnabledTriggers_excludesDisabled() =
        runTest {
            triggerDao.insert(testTrigger(caseId = caseId, enabled = true))
            triggerDao.insert(testTrigger(caseId = caseId, enabled = false))

            val enabled = triggerDao.getEnabledTriggers()

            assertEquals(1, enabled.size)
            assertTrue(enabled.all { it.enabled })
        }
}
