package com.secondmonday.hodith.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps both widgets' elapsed-time and today's-count fresh even when nothing else triggers a
 * refresh — [ListWidget.provideGlance]/[SingleCaseWidget.provideGlance] compute both once per
 * invocation (Room read via `.first()`, `System.currentTimeMillis()` baked into the label), so
 * without this periodic nudge they go stale until the next tap/log/pin change.
 *
 * Also the delivery mechanism for [enqueueRefresh], the one-off request that
 * [QuickLogAction]/[com.secondmonday.hodith.viewmodel.WidgetLogSheetViewModel]/
 * both widgets' configure activities use instead of calling [refreshAllWidgets] inline: a widget
 * instance can't reliably push its own next update from within the same click/configure
 * transaction that's still resolving for that same widget id (observed as the List widget's own
 * `+` never refreshing its own count, and the Single-case widget's configure-time render not
 * sticking) — running the actual Glance update from a separate `CoroutineWorker` execution
 * sidesteps that.
 *
 * Plain constructor rather than `@HiltWorker`: that would need `HodithApplication` to implement
 * `Configuration.Provider`, which requires disabling WorkManager's default initializer — but
 * instrumented tests run under `HiltTestApplication` (no `Configuration.Provider`), so that combo
 * crashes `SystemJobService` on test startup. [refreshAllWidgets] resolves what it needs itself via
 * [dagger.hilt.android.EntryPointAccessors] inside each widget's `provideGlance()`, so `doWork()`
 * doesn't need Hilt access of its own.
 */
class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        refreshAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "widget_refresh"
        const val ONE_OFF_WORK_NAME = "widget_refresh_one_off"

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

        /** Replaces any still-pending one-off refresh rather than queuing another — the refresh
         * itself is idempotent (reads current state), so a rapid string of taps only needs the
         * latest one to actually run. */
        fun enqueueRefresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build(),
            )
        }
    }
}
