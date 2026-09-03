package com.secondmonday.hodith.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Drives the real [SystemNotifier] (via the [Notifier] binding) against the real
 * [android.app.NotificationManager] — proves a Trigger fired / check-in due notification actually
 * posts with the right [PlainVoice] title/body/actions, and that 2+ notifications bundle under one
 * alert-once group summary, without going through [NotificationEvaluator] first.
 *
 * Deliberately doesn't route through [NotificationEvaluator.evaluateAll]/`evaluateCase`: those run
 * against the real, shared on-device repository, which may hold Cases/Triggers left over from
 * other instrumented tests or manual QA sessions — anything that counts "how many are due" would
 * be flaky here. [NotificationEvaluator]'s own selection logic (posting per due Case, withdrawing
 * the rest) is covered against a fake repository per `TESTING.md`; this layer's job is to prove
 * the real [Notifier] posts real, correctly-worded, correctly-grouped notifications. Doesn't
 * verify the posted notification's tap target: `PendingIntent` doesn't expose its wrapped `Intent`
 * through any public API, so that part of the manual check remains manual.
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
    fun notifyCheckInDue_setsOnlyAlertOnceSoRepeatedPostsDoNotReAlert() =
        runBlocking {
            val case = testCase(id = 505L, name = "Dishes ${System.currentTimeMillis()}")

            notifier.notifyCheckInDue(case, silentDays = 8L, voice = PlainVoice)

            val expectedTitle = PlainVoice.checkInDueNotificationTitle(case.name)
            val posted = waitForNotification { title, _, _ -> title == expectedTitle }
            assertNotNull("Expected a check-in-due notification titled '$expectedTitle'", posted)
            assertTrue(
                "Check-in notifications must set FLAG_ONLY_ALERT_ONCE so the ~6h re-post updates silently",
                posted!!.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            )
        }

    @Test
    fun twoDueCheckIns_shareTheGroup_andGetAnAlertOnceSummary() =
        runBlocking {
            val a = testCase(id = 503L, name = "Coffee ${System.currentTimeMillis()}")
            val b = testCase(id = 504L, name = "Migraine ${System.currentTimeMillis()}")

            notifier.notifyCheckInDue(a, 7L, PlainVoice)
            notifier.notifyCheckInDue(b, 9L, PlainVoice)

            val childA = waitForNotification { title, _, _ -> title == PlainVoice.checkInDueNotificationTitle(a.name) }
            assertNotNull("Expected a's check-in notification", childA)
            assertEquals(NOTIFICATION_GROUP_KEY, NotificationCompat.getGroup(childA!!.notification))
            assertEquals(
                "children route their alert through the summary",
                NotificationCompat.GROUP_ALERT_SUMMARY,
                NotificationCompat.getGroupAlertBehavior(childA.notification),
            )

            val summary = waitForNotification { title, _, _ -> title == PlainVoice.notificationsGroupSummaryTitle(2) }
            assertNotNull("Expected a group summary once 2 check-ins show", summary)
            assertTrue(
                "the summary must be the group summary",
                summary!!.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
            assertTrue(
                "the summary alerts once so silent re-posts don't re-nag",
                summary.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            )
        }

    @Test
    fun cancelCheckIn_droppingToOneChild_removesTheSummary() =
        runBlocking {
            val a = testCase(id = 506L, name = "Coffee ${System.currentTimeMillis()}")
            val b = testCase(id = 507L, name = "Migraine ${System.currentTimeMillis()}")
            notifier.notifyCheckInDue(a, 7L, PlainVoice)
            notifier.notifyCheckInDue(b, 9L, PlainVoice)
            assertNotNull(waitForNotification { title, _, _ -> title == PlainVoice.notificationsGroupSummaryTitle(2) })

            notifier.cancelCheckIn(a.id, PlainVoice)

            assertTrue(
                "a's check-in and the now-redundant summary should both be gone",
                waitUntilGone {
                    findNotification { title, _, _ -> title == PlainVoice.checkInDueNotificationTitle(a.name) } == null &&
                        findNotification { title, _, _ -> title == PlainVoice.notificationsGroupSummaryTitle(2) } == null
                },
            )
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

    private fun waitUntilGone(
        maxAttempts: Int = 30,
        condition: () -> Boolean,
    ): Boolean {
        var attempts = 0
        while (!condition() && attempts < maxAttempts) {
            Thread.sleep(200)
            attempts++
        }
        return condition()
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
