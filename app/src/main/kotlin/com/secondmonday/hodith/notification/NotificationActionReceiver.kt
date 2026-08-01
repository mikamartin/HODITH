package com.secondmonday.hodith.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.quickLogEvent
import com.secondmonday.hodith.widget.EXTRA_CASE_ID
import com.secondmonday.hodith.widget.WidgetEntryPoint
import com.secondmonday.hodith.widget.WidgetLogTrampolineActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTION_LOG = "com.secondmonday.hodith.notification.ACTION_LOG"
const val ACTION_ALL_QUIET = "com.secondmonday.hodith.notification.ACTION_ALL_QUIET"
const val EXTRA_NOTIFICATION_ID = "com.secondmonday.hodith.notification.EXTRA_NOTIFICATION_ID"

/**
 * Handles the check-in notification's Log/All quiet actions (spec §11). Both are plain
 * broadcasts rather than one `getBroadcast` + one `getActivity` PendingIntent, so notification
 * cancellation — `setAutoCancel` doesn't reliably dismiss on an action-button tap, only on the
 * main content tap — is handled explicitly in one place for every path, including [ACTION_LOG]'s
 * [LogFlow.DETAIL_SHEET] branch, which hands off to [WidgetLogTrampolineActivity] (the same sheet
 * the widget uses) rather than logging directly.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val caseId = intent.getLongExtra(EXTRA_CASE_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (caseId == -1L) return
        val action = intent.action
        val appContext = context.applicationContext

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
                when (action) {
                    ACTION_LOG -> handleLog(appContext, entryPoint, caseId)
                    ACTION_ALL_QUIET -> handleAllQuiet(entryPoint, caseId)
                }
                if (notificationId != -1) {
                    NotificationManagerCompat.from(appContext).cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleLog(
        context: Context,
        entryPoint: WidgetEntryPoint,
        caseId: Long,
    ) {
        val repository = entryPoint.repository()
        val case = repository.getCase(caseId) ?: return
        when (case.logFlow) {
            LogFlow.ONE_TAP -> {
                repository.insertEvent(quickLogEvent(caseId = caseId, now = entryPoint.clock().nowMillis()))
            }
            LogFlow.DETAIL_SHEET -> {
                context.startActivity(
                    Intent(context, WidgetLogTrampolineActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_CASE_ID, caseId),
                )
            }
        }
    }

    private suspend fun handleAllQuiet(
        entryPoint: WidgetEntryPoint,
        caseId: Long,
    ) {
        val repository = entryPoint.repository()
        val case = repository.getCase(caseId) ?: return
        repository.updateCase(case.copy(lastCheckInAt = entryPoint.clock().nowMillis()))
    }
}
