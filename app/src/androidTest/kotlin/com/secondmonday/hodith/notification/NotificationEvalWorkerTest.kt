package com.secondmonday.hodith.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Same shape as [com.secondmonday.hodith.widget.WidgetRefreshWorkerTest] — [NotificationEvalWorker]
 * resolves [NotificationEvaluator] via [dagger.hilt.android.EntryPointAccessors] at `doWork()` time,
 * which requires a real, populated Hilt component. Uses the real [NotificationEvaluator]/repository
 * rather than fakes: a `SUCCEEDED` terminal state already proves `doWork()` ran `evaluateAll()`
 * without throwing against an empty database (no triggers, no cases).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationEvalWorkerTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun doWork_resolvesTheRealNotificationEvaluatorAndSucceeds() {
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<NotificationEvalWorker>().build()

        workManager.enqueue(request).result.get()

        var info = workManager.getWorkInfoById(request.id).get()!!
        var attempts = 0
        while (!info.state.isFinished && attempts < 50) {
            Thread.sleep(100)
            info = workManager.getWorkInfoById(request.id).get()!!
            attempts++
        }
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
    }

    @Test
    fun enqueue_registersAUniquePeriodicWorkRequest() {
        NotificationEvalWorker.enqueue(context)

        val workManager = WorkManager.getInstance(context)
        val workId =
            workManager
                .getWorkInfosForUniqueWork(NotificationEvalWorker.WORK_NAME)
                .get()
                .single()
                .id

        // Test scheduler runs eagerly (no initial delay/constraints), so it can still be RUNNING
        // right after enqueue() returns; poll until it cycles back to ENQUEUED for its next interval.
        var info = workManager.getWorkInfoById(workId).get()!!
        var attempts = 0
        while (info.state == WorkInfo.State.RUNNING && attempts < 50) {
            Thread.sleep(100)
            info = workManager.getWorkInfoById(workId).get()!!
            attempts++
        }

        val infos = workManager.getWorkInfosForUniqueWork(NotificationEvalWorker.WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }
}
