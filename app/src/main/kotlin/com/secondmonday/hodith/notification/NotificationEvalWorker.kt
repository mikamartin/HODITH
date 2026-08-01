package com.secondmonday.hodith.notification

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
 * Spec §11: evaluates every enabled Trigger and every active Case's check-in on a ~6h cadence, so
 * `SILENT_FOR` triggers and check-ins can fire without any logging happening. Plain constructor +
 * Hilt [EntryPoint], same pattern as [com.secondmonday.hodith.widget.WidgetRefreshWorker] and for
 * the same reason: a `@HiltWorker`/`HiltWorkerFactory` setup needs `Configuration.Provider` on
 * [com.secondmonday.hodith.HodithApplication], which conflicts with `HiltTestApplication` in
 * instrumented tests.
 */
class NotificationEvalWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationEvaluatorEntryPoint {
        fun notificationEvaluator(): NotificationEvaluator
    }

    override suspend fun doWork(): Result {
        val notificationEvaluator =
            EntryPointAccessors
                .fromApplication(applicationContext, NotificationEvaluatorEntryPoint::class.java)
                .notificationEvaluator()
        notificationEvaluator.evaluateAll()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "notification_eval"
        private const val EVAL_INTERVAL_HOURS = 6L

        fun periodicWorkRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<NotificationEvalWorker>(EVAL_INTERVAL_HOURS, TimeUnit.HOURS).build()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest(),
            )
        }
    }
}
