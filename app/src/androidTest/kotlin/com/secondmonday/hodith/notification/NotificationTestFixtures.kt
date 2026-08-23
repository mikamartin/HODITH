package com.secondmonday.hodith.notification

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

// Shared by NotificationActionReceiverTest/NotifierContentTest — instrumented tests run inside
// HiltTestApplication, which never requests POST_NOTIFICATIONS, so a real notification post would
// otherwise be silently dropped on API 33+.
internal fun Context.grantPostNotificationsPermission() {
    val command = "pm grant $packageName android.permission.POST_NOTIFICATIONS"
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
}
