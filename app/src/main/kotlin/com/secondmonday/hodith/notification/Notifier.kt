package com.secondmonday.hodith.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.secondmonday.hodith.MainActivity
import com.secondmonday.hodith.R
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.widget.EXTRA_CASE_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface Notifier {
    fun notifyTriggerFired(
        case: CaseEntity,
        trigger: TriggerEntity,
        voice: Voice,
    )

    fun notifyCheckInDue(
        case: CaseEntity,
        silentDays: Long,
        voice: Voice,
    )

    /** Anti-spam collapse for 2+ Cases due for a check-in in the same evaluation pass. */
    fun notifyCheckInsSummary(
        cases: List<CaseEntity>,
        voice: Voice,
    )
}

class SystemNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : Notifier {
        override fun notifyTriggerFired(
            case: CaseEntity,
            trigger: TriggerEntity,
            voice: Voice,
        ) {
            post(
                notificationId = triggerNotificationId(trigger.id),
                title = voice.triggerFiredNotificationTitle(case.name),
                text = voice.triggerSummary(trigger.kind, trigger.threshold, trigger.windowDays),
                caseId = case.id,
            )
        }

        override fun notifyCheckInDue(
            case: CaseEntity,
            silentDays: Long,
            voice: Voice,
        ) {
            val notificationId = checkInNotificationId(case.id)
            post(
                notificationId = notificationId,
                title = voice.checkInDueNotificationTitle(case.name),
                text = voice.checkInDueNotificationBody(silentDays),
                caseId = case.id,
                actions =
                    listOf(
                        action(voice.notificationLogAction, ACTION_LOG, case.id, notificationId),
                        action(voice.notificationAllQuietAction, ACTION_ALL_QUIET, case.id, notificationId),
                    ),
            )
        }

        override fun notifyCheckInsSummary(
            cases: List<CaseEntity>,
            voice: Voice,
        ) {
            post(
                notificationId = CHECK_IN_SUMMARY_NOTIFICATION_ID,
                title = voice.checkInsSummaryNotificationTitle(cases.size),
                text = null,
                caseId = null,
            )
        }

        /** A broadcast targeting [NotificationActionReceiver] for one of [notifyCheckInDue]'s action buttons. */
        private fun action(
            label: String,
            actionName: String,
            caseId: Long,
            notificationId: Int,
        ): NotificationCompat.Action {
            val intent =
                Intent(context, NotificationActionReceiver::class.java).apply {
                    action = actionName
                    putExtra(EXTRA_CASE_ID, caseId)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }
            // requestCode alone would collide across Log/All quiet for the same notificationId,
            // but PendingIntent uniqueness also considers the Intent's action string, which differs.
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            return NotificationCompat.Action.Builder(R.drawable.ic_notification, label, pendingIntent).build()
        }

        private fun post(
            notificationId: Int,
            title: String,
            text: String?,
            caseId: Long?,
            actions: List<NotificationCompat.Action> = emptyList(),
        ) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val openAppIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    if (caseId != null) putExtra(EXTRA_CASE_ID, caseId)
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val builder =
                NotificationCompat
                    .Builder(context, ALERTS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
            if (text != null) builder.setContentText(text)
            actions.forEach { builder.addAction(it) }
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

// Distinct ID ranges so a Trigger, a Case's check-in, and the check-in summary can never collide.
private const val NOTIFICATION_ID_MODULUS = 100_000
private const val CHECK_IN_NOTIFICATION_ID_BASE = 1_000_000
private const val CHECK_IN_SUMMARY_NOTIFICATION_ID = 2_000_000

private fun triggerNotificationId(triggerId: Long): Int = (triggerId % NOTIFICATION_ID_MODULUS).toInt()

private fun checkInNotificationId(caseId: Long): Int = CHECK_IN_NOTIFICATION_ID_BASE + (caseId % NOTIFICATION_ID_MODULUS).toInt()
