package com.secondmonday.hodith.notification

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.secondmonday.hodith.ui.voice.Voice

const val ALERTS_CHANNEL_ID = "hodith_alerts"

/**
 * Idempotent — re-creating an existing channel with the same ID updates its name/description
 * (importance is fixed after first creation) rather than duplicating it, so this is safe to call
 * on every app start to keep the channel's name in sync with the active [Voice].
 */
fun ensureNotificationChannel(
    context: Context,
    voice: Voice,
) {
    val channel =
        NotificationChannelCompat
            .Builder(ALERTS_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(voice.notificationChannelName)
            .setDescription(voice.notificationChannelDescription)
            .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}
