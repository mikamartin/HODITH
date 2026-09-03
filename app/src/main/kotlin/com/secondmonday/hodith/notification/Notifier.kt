package com.secondmonday.hodith.notification

import android.Manifest
import android.app.NotificationManager
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

/** The one Android notification group every HODITH alert joins, so the shade bundles them (spec §11). */
const val NOTIFICATION_GROUP_KEY = "com.secondmonday.hodith.notifications"

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

    /** Withdraw a Case's check-in notification once it's no longer due, and refresh the group summary. */
    fun cancelCheckIn(
        caseId: Long,
        voice: Voice,
    )

    /**
     * Recompute the group summary from HODITH's currently-posted notifications. Called after every
     * post/cancel so the summary tracks the batch; also the hook [NotificationActionReceiver] uses
     * after an action tap, since those cancel a child without going through [notifyCheckInDue].
     */
    fun refreshGroupSummary(voice: Voice)
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
                voice = voice,
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
                voice = voice,
                actions =
                    listOf(
                        action(voice.notificationLogAction, ACTION_LOG, case.id, notificationId),
                        action(voice.notificationAllQuietAction, ACTION_ALL_QUIET, case.id, notificationId),
                    ),
            )
        }

        override fun cancelCheckIn(
            caseId: Long,
            voice: Voice,
        ) {
            NotificationManagerCompat.from(context).cancel(checkInNotificationId(caseId))
            refreshGroupSummary(voice)
        }

        /**
         * Post the group summary when 2+ HODITH notifications are showing, cancel it otherwise. The
         * summary is the only member that alerts for the batch ([NotificationCompat.GROUP_ALERT_SUMMARY]
         * on the children) and it alerts once, so a check-in re-posted on each ~6h pass updates the
         * stack silently. Its lines are the children's own titles — already voiced, no extra key.
         */
        override fun refreshGroupSummary(voice: Voice) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val childTitles = activeGroupChildTitles()
            val manager = NotificationManagerCompat.from(context)
            if (childTitles.size < 2) {
                manager.cancel(GROUP_SUMMARY_NOTIFICATION_ID)
                return
            }
            val inbox = NotificationCompat.InboxStyle()
            childTitles.take(GROUP_SUMMARY_MAX_LINES).forEach(inbox::addLine)
            val summary =
                NotificationCompat
                    .Builder(context, ALERTS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(voice.notificationsGroupSummaryTitle(childTitles.size))
                    .setContentIntent(openAppPendingIntent(GROUP_SUMMARY_NOTIFICATION_ID, caseId = null))
                    .setStyle(inbox)
                    .setGroup(NOTIFICATION_GROUP_KEY)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
            manager.notify(GROUP_SUMMARY_NOTIFICATION_ID, summary)
        }

        /** Titles of HODITH's posted trigger/check-in notifications — the summary excluded. */
        private fun activeGroupChildTitles(): List<String> =
            context
                .getSystemService(NotificationManager::class.java)
                .activeNotifications
                .filter {
                    NotificationCompat.getGroup(it.notification) == NOTIFICATION_GROUP_KEY &&
                        it.id != GROUP_SUMMARY_NOTIFICATION_ID
                }.mapNotNull {
                    it.notification.extras
                        .getCharSequence(NotificationCompat.EXTRA_TITLE)
                        ?.toString()
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
            voice: Voice,
            actions: List<NotificationCompat.Action> = emptyList(),
        ) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val builder =
                NotificationCompat
                    .Builder(context, ALERTS_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentIntent(openAppPendingIntent(notificationId, caseId))
                    .setAutoCancel(true)
                    .setGroup(NOTIFICATION_GROUP_KEY)
                    // Only the group summary sounds for a batch; a check-in re-posted on each ~6h
                    // pass to bump its "$n days" text then updates the stack silently.
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setOnlyAlertOnce(true)
            if (text != null) builder.setContentText(text)
            actions.forEach { builder.addAction(it) }
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            refreshGroupSummary(voice)
        }

        private fun openAppPendingIntent(
            requestCode: Int,
            caseId: Long?,
        ): PendingIntent {
            val openAppIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    if (caseId != null) putExtra(EXTRA_CASE_ID, caseId)
                }
            return PendingIntent.getActivity(
                context,
                requestCode,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

// Distinct ID ranges so a Trigger, a Case's check-in, and the group summary can never collide.
private const val NOTIFICATION_ID_MODULUS = 100_000
private const val CHECK_IN_NOTIFICATION_ID_BASE = 1_000_000
private const val GROUP_SUMMARY_NOTIFICATION_ID = 3_000_000
private const val GROUP_SUMMARY_MAX_LINES = 6

private fun triggerNotificationId(triggerId: Long): Int = (triggerId % NOTIFICATION_ID_MODULUS).toInt()

private fun checkInNotificationId(caseId: Long): Int = CHECK_IN_NOTIFICATION_ID_BASE + (caseId % NOTIFICATION_ID_MODULUS).toInt()
