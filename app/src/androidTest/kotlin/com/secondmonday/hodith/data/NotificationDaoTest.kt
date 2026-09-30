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
class NotificationDaoTest {
    private lateinit var db: HodithDatabase
    private lateinit var notificationDao: NotificationDao
    private var caseId: Long = 0

    @Before
    fun setUp() =
        runTest {
            db = createInMemoryDatabase()
            notificationDao = db.notificationDao()
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
            notificationDao.insert(testNotification(caseId = caseId, threshold = 3))
            val loaded = notificationDao.observeNotificationsForCase(caseId).first().single()

            notificationDao.update(loaded.copy(threshold = 5))

            assertEquals(
                5,
                notificationDao
                    .observeNotificationsForCase(caseId)
                    .first()
                    .single()
                    .threshold,
            )
        }

    @Test
    fun delete_removesNotification() =
        runTest {
            notificationDao.insert(testNotification(caseId = caseId))
            val loaded = notificationDao.observeNotificationsForCase(caseId).first().single()

            notificationDao.delete(loaded)

            assertTrue(notificationDao.observeNotificationsForCase(caseId).first().isEmpty())
        }

    @Test
    fun getEnabledNotifications_excludesDisabled() =
        runTest {
            notificationDao.insert(testNotification(caseId = caseId, enabled = true))
            notificationDao.insert(testNotification(caseId = caseId, enabled = false))

            val enabled = notificationDao.getEnabledNotifications()

            assertEquals(1, enabled.size)
            assertTrue(enabled.all { it.enabled })
        }

    @Test
    fun getById_returnsMatchingNotification() =
        runTest {
            val id = notificationDao.insert(testNotification(caseId = caseId, threshold = 9))

            assertEquals(9, notificationDao.getById(id)?.threshold)
        }

    @Test
    fun getById_returnsNullWhenMissing() =
        runTest {
            assertEquals(null, notificationDao.getById(id = 12345L))
        }

    @Test
    fun getAll_returnsEveryNotificationAcrossAllCases() =
        runTest {
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            notificationDao.insert(testNotification(caseId = caseId))
            notificationDao.insert(testNotification(caseId = otherCaseId))

            val all = notificationDao.getAll()

            assertEquals(2, all.size)
        }

    @Test
    fun getNotificationsForCase_scopesToCase() =
        runTest {
            val otherCaseId = db.caseDao().insert(testCase(name = "Other"))
            notificationDao.insert(testNotification(caseId = caseId))
            notificationDao.insert(testNotification(caseId = otherCaseId))

            val forCase = notificationDao.getNotificationsForCase(caseId)

            assertEquals(1, forCase.size)
            assertTrue(forCase.all { it.caseId == caseId })
        }
}
