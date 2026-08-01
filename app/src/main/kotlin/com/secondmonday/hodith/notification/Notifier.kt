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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Posts the (branch 5) minimal notifications: tap opens the app, no deep-link or actions yet —
 * those, plus anti-spam summary collapsing, are `feature/notification-actions` (spec §11).
 */
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
}

class SystemNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
            )
        }

        override fun notifyCheckInDue(
            case: CaseEntity,
            silentDays: Long,
            voice: Voice,
        ) {
            post(
                notificationId = checkInNotificationId(case.id),
                title = voice.checkInDueNotificationTitle(case.name),
                text = voice.checkInDueNotificationBody(silentDays),
            )
        }

        private fun post(
            notificationId: Int,
            title: String,
            text: String,
        ) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val openAppIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, ALERTS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

// Distinct ID ranges so a Trigger and a Case can never collide on the same notification slot.
private const val NOTIFICATION_ID_MODULUS = 100_000
private const val CHECK_IN_NOTIFICATION_ID_BASE = 1_000_000

private fun triggerNotificationId(triggerId: Long): Int = (triggerId % NOTIFICATION_ID_MODULUS).toInt()

private fun checkInNotificationId(caseId: Long): Int = CHECK_IN_NOTIFICATION_ID_BASE + (caseId % NOTIFICATION_ID_MODULUS).toInt()
