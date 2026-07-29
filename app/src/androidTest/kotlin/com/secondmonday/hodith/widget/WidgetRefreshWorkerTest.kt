package com.secondmonday.hodith.widget

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
 * First Hilt-based instrumented test in the repo — needed here because [WidgetRefreshWorker]
 * resolves [WidgetRefresher] via [dagger.hilt.android.EntryPointAccessors] at `doWork()` time,
 * which requires a real, populated Hilt component. Scoped to this one test class only — no
 * `HiltTestRunner`/shared-infra changes. Uses the real `GlanceWidgetRefresher` rather than a fake:
 * a `SUCCEEDED` terminal state already proves `doWork()` called `refreshListWidget()` without
 * throwing, and `ListWidget().updateAll()` safely no-ops with zero installed widget instances.
 *
 * Split into two tests rather than one: `PeriodicWorkRequest` never settles on a durable
 * `SUCCEEDED` `WorkInfo.State` — after each successful run WorkManager cycles it straight back to
 * `ENQUEUED` to await the next interval — so the only reliable way to prove `doWork()` itself
 * completes is a one-time request built from the same class.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WidgetRefreshWorkerTest {
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
    fun doWork_resolvesTheRealWidgetRefresherAndSucceeds() {
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()

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
        WidgetRefreshWorker.enqueue(context)

        val workManager = WorkManager.getInstance(context)
        val workId =
            workManager
                .getWorkInfosForUniqueWork(WidgetRefreshWorker.WORK_NAME)
                .get()
                .single()
                .id

        // The test scheduler runs the request eagerly (no initial delay/constraints), so it can
        // still be RUNNING the instant after enqueue() returns; poll until it cycles back to
        // ENQUEUED to await its next interval, same quirk as the SUCCEEDED test above.
        var info = workManager.getWorkInfoById(workId).get()!!
        var attempts = 0
        while (info.state == WorkInfo.State.RUNNING && attempts < 50) {
            Thread.sleep(100)
            info = workManager.getWorkInfoById(workId).get()!!
            attempts++
        }

        val infos = workManager.getWorkInfosForUniqueWork(WidgetRefreshWorker.WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }
}
