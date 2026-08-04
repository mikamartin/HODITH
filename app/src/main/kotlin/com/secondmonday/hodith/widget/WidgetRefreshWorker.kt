package com.secondmonday.hodith.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Keeps both widgets' elapsed-time and today's-count fresh even when nothing else triggers a
 * refresh — [ListWidget.provideGlance]/[SingleCaseWidget.provideGlance] compute both once per
 * invocation (Room read via `.first()`, `System.currentTimeMillis()` baked into the label), so
 * without this periodic nudge they go stale until the next tap/log/pin change.
 *
 * Resolves [WidgetRefresher] via [EntryPointAccessors] rather than constructor injection: a
 * `@HiltWorker`/`HiltWorkerFactory` would need `HodithApplication` to implement
 * `Configuration.Provider`, which requires disabling WorkManager's default initializer — but
 * instrumented tests run under `HiltTestApplication` (no `Configuration.Provider`), so that combo
 * crashes `SystemJobService` on test startup. This plain-constructor + entry-point approach keeps
 * the default initializer intact and avoids the conflict.
 */
class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetRefresherEntryPoint {
        fun widgetRefresher(): WidgetRefresher
    }

    override suspend fun doWork(): Result {
        val widgetRefresher =
            EntryPointAccessors
                .fromApplication(applicationContext, WidgetRefresherEntryPoint::class.java)
                .widgetRefresher()
        widgetRefresher.refreshWidgets()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget_refresh"

        // Android's enforced floor for PeriodicWorkRequest; the OS can still defer further under Doze.
        private const val REFRESH_INTERVAL_MINUTES = 15L

        fun periodicWorkRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES).build()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }
    }
}
