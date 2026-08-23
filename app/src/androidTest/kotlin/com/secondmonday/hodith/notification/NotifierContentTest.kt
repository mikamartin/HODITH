package com.secondmonday.hodith.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testTrigger
import com.secondmonday.hodith.ui.voice.PlainVoice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Drives the real [SystemNotifier] (via the [Notifier] binding) against the real
 * [android.app.NotificationManager] — proves a Trigger fired / check-in due / check-ins summary
 * notification actually posts with the right [PlainVoice] title/body/actions, without going
 * through [NotificationEvaluator]'s data-dependent Trigger/check-in selection first.
 *
 * Deliberately doesn't route through [NotificationEvaluator.evaluateAll]/`evaluateCase`: those run
 * against the real, shared on-device repository, which may hold Cases/Triggers left over from
 * other instrumented tests or manual QA sessions — anything that counts "how many are due" would
 * be flaky here. [NotificationEvaluator]'s own selection logic (single due check-in vs. 2+
 * collapsing into a summary) is already covered against a fake repository per `TESTING.md`; this
 * layer's job is only to prove the real [Notifier] posts real, correctly-worded notifications.
 * Doesn't verify the posted notification's tap target: `PendingIntent` doesn't expose its wrapped
 * `Intent` through any public API, so that part of the manual check remains manual.
 *
 * Instrumented tests run inside [dagger.hilt.android.testing.HiltTestApplication], not the real
 * [com.secondmonday.hodith.HodithApplication] — so the `hodith_alerts` channel that
 * `HodithApplication.onCreate()` normally creates on every real launch doesn't exist here either,
 * and without it `NotificationManagerCompat.notify()` silently drops the notification. `setUp`
 * creates the channel itself to compensate.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotifierContentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var notifier: Notifier

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        context.grantPostNotificationsPermission()
        ensureNotificationChannel(context, PlainVoice)
    }

    @After
    fun tearDown() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun notifyTriggerFired_postsANotificationWithTheVoiceTitleAndBody() =
        runBlocking {
            val case = testCase(id = 501L, name = "Coffee ${System.currentTimeMillis()}")
            val trigger = testTrigger(caseId = case.id, threshold = 3, windowDays = 7)

            notifier.notifyTriggerFired(case, trigger, PlainVoice)

            val expectedTitle = PlainVoice.triggerFiredNotificationTitle(case.name)
            val expectedText = PlainVoice.triggerSummary(trigger.kind, trigger.threshold, trigger.windowDays)
            val posted = waitForNotification { title, text, _ -> title == expectedTitle && text == expectedText }
            assertNotNull("Expected a Trigger-fired notification titled '$expectedTitle'", posted)
        }

    @Test
    fun notifyCheckInDue_postsANotificationWithLogAndAllQuietActions() =
        runBlocking {
            val case = testCase(id = 502L, name = "Migraine ${System.currentTimeMillis()}")
            val silentDays = 10L

            notifier.notifyCheckInDue(case, silentDays, PlainVoice)

            val expectedTitle = PlainVoice.checkInDueNotificationTitle(case.name)
            val expectedText = PlainVoice.checkInDueNotificationBody(silentDays)
            val posted =
                waitForNotification { title, text, actionTitles ->
                    title == expectedTitle &&
                        text == expectedText &&
                        actionTitles == listOf(PlainVoice.notificationLogAction, PlainVoice.notificationAllQuietAction)
                }
            assertNotNull("Expected a check-in-due notification titled '$expectedTitle' with Log/All quiet actions", posted)
        }

    @Test
    fun notifyCheckInsSummary_postsOneCollapsedNotification() =
        runBlocking {
            val cases =
                listOf(
                    testCase(id = 503L, name = "Coffee ${System.currentTimeMillis()}"),
                    testCase(id = 504L, name = "Migraine ${System.currentTimeMillis()}"),
                )

            notifier.notifyCheckInsSummary(cases, PlainVoice)

            val expectedTitle = PlainVoice.checkInsSummaryNotificationTitle(cases.size)
            val posted = waitForNotification { title, _, _ -> title == expectedTitle }
            assertNotNull("Expected a check-ins summary notification titled '$expectedTitle'", posted)
        }

    private suspend fun waitForNotification(
        maxAttempts: Int = 30,
        matches: (title: String?, text: String?, actionTitles: List<String>) -> Boolean,
    ): android.service.notification.StatusBarNotification? {
        var attempts = 0
        var found = findNotification(matches)
        while (found == null && attempts < maxAttempts) {
            Thread.sleep(200)
            found = findNotification(matches)
            attempts++
        }
        return found
    }

    private fun findNotification(matches: (title: String?, text: String?, actionTitles: List<String>) -> Boolean) =
        notificationManager.activeNotifications.firstOrNull { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            val actionTitles =
                sbn.notification.actions
                    ?.map { it.title.toString() }
                    .orEmpty()
            matches(title, text, actionTitles)
        }
}
