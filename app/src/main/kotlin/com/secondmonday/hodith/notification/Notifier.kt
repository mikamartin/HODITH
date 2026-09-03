package com.secondmonday.hodith.notification

import android.Manifest
import android.app.Notification
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
     * Recompute the group summary from HODITH's currently-posted notifications. [notifyCheckInDue] /
     * [cancelCheckIn] already keep it in step; this is the hook [NotificationActionReceiver] uses
     * after a Log / All quiet tap, which cancels a child without going through either.
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
            val id = checkInNotificationId(caseId)
            NotificationManagerCompat.from(context).cancel(id)
            syncGroupSummary(voice, removedId = id)
        }

        override fun refreshGroupSummary(voice: Voice) = syncGroupSummary(voice)

        /**
         * Keep the group summary in step with HODITH's posted trigger/check-in notifications. The
         * summary is the only member that alerts for the batch ([NotificationCompat.GROUP_ALERT_SUMMARY]
         * on the children) and it alerts once, so a check-in re-posted on each ~6h pass updates the
         * stack silently. Its lines are the children's own titles — already voiced, no extra key.
         *
         * [addedId] / [removedId] adjust for a `notify`/`cancel` that may not have reached
         * [android.app.NotificationManager.getActiveNotifications] yet — the batch check-in pass
         * fires several in a row, so a plain read would race itself.
         *
         * A one-child (or empty) group gets no summary: cancelling a `setGroupSummary(true)`
         * notification cascades to the group's remaining children, so tearing it down while one
         * check-in is left would take that check-in with it. Android suppresses a lone-child summary
         * anyway (it shows the child standalone); the explicit cancel only runs at zero children,
         * where nothing can cascade.
         */
        private fun syncGroupSummary(
            voice: Voice,
            addedId: Int? = null,
            removedId: Int? = null,
        ) {
            val children =
                buildMap {
                    putAll(activeGroupChildren())
                    removedId?.let { remove(it) }
                    addedId?.let { putIfAbsent(it, "") }
                }
            when {
                children.isEmpty() -> NotificationManagerCompat.from(context).cancel(GROUP_SUMMARY_NOTIFICATION_ID)
                children.size == 1 -> Unit
                else -> {
                    val lines = children.values.filter { it.isNotEmpty() }.take(GROUP_SUMMARY_MAX_LINES)
                    val inbox = NotificationCompat.InboxStyle()
                    lines.forEach(inbox::addLine)
                    notify(
                        GROUP_SUMMARY_NOTIFICATION_ID,
                        NotificationCompat
                            .Builder(context, ALERTS_CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(voice.notificationsGroupSummaryTitle(children.size))
                            .setContentIntent(openAppPendingIntent(GROUP_SUMMARY_NOTIFICATION_ID, caseId = null))
                            .setStyle(inbox)
                            .setGroup(NOTIFICATION_GROUP_KEY)
                            .setGroupSummary(true)
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(true)
                            .build(),
                    )
                }
            }
        }

        /** id → title for HODITH's posted trigger/check-in notifications, the summary excluded. */
        private fun activeGroupChildren(): Map<Int, String> {
            val manager = context.getSystemService(NotificationManager::class.java)
            val ours =
                manager.activeNotifications.filter { sbn ->
                    NotificationCompat.getGroup(sbn.notification) == NOTIFICATION_GROUP_KEY &&
                        sbn.id != GROUP_SUMMARY_NOTIFICATION_ID
                }
            return ours.associate { sbn ->
                val title = sbn.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)
                sbn.id to title?.toString().orEmpty()
            }
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
            notify(notificationId, builder.build())
            syncGroupSummary(voice, addedId = notificationId)
        }

        /** Post [notification] under [id], or silently no-op when POST_NOTIFICATIONS isn't granted. */
        private fun notify(
            id: Int,
            notification: Notification,
        ) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            NotificationManagerCompat.from(context).notify(id, notification)
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

/** `internal` so `NotificationActionReceiverTest` can pass a Case's real check-in id as `EXTRA_NOTIFICATION_ID`. */
internal fun checkInNotificationId(caseId: Long): Int = CHECK_IN_NOTIFICATION_ID_BASE + (caseId % NOTIFICATION_ID_MODULUS).toInt()
