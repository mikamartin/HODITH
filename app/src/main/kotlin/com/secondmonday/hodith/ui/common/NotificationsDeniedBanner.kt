package com.secondmonday.hodith.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.secondmonday.hodith.ui.voice.Voice

/**
 * True once notifications have been checked and found off — covers both a denied runtime
 * permission and the app-level "notifications" toggle in system Settings, either of which means an
 * alert won't reach the user. Re-checked on every `ON_RESUME` so returning from system Settings
 * (where [NotificationsDeniedBanner]'s own action sends the user) updates it without a restart.
 */
@Composable
private fun rememberNotificationsDenied(): Boolean {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        denied = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    return denied
}

/**
 * Spec §11/§14: shown on Home once the app has asked for notification permission at least once
 * (first Trigger created or first check-in enabled) and it's currently off — Triggers and check-ins
 * still evaluate either way, this is just the in-app fallback for anyone who won't see the alert.
 */
@Composable
fun NotificationsDeniedBanner(
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    if (!rememberNotificationsDenied()) return

    val context = LocalContext.current
    ActionBanner(message = voice.notificationsDeniedBannerMessage, modifier = modifier) {
        TextButton(onClick = {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        }) {
            Text(voice.notificationsDeniedBannerAction)
        }
    }
}
