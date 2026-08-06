package com.secondmonday.hodith.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondmonday.hodith.R
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.widget.EXTRA_CASE_ID
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * [NotificationActionReceiver] handles the check-in notification's Log/All quiet actions (spec
 * §11) and needs a real, populated Hilt component ([dagger.hilt.android.EntryPointAccessors]) to
 * resolve its repository/clock, same reasoning as [com.secondmonday.hodith.widget.WidgetActionsFlowTest]
 * for the widget's own `ActionCallback`s. Dispatched via a real [Context.sendBroadcast] (an
 * unexported receiver still accepts broadcasts from its own app/UID) rather than calling
 * `onReceive` directly on a bare instance: `onReceive` calls `goAsync()`, which throws unless the
 * system's real broadcast dispatch has set up the receiver's pending-result state first.
 *
 * Instrumented tests run inside [dagger.hilt.android.testing.HiltTestApplication]
 * ([HiltTestRunner]), not the real [com.secondmonday.hodith.HodithApplication] — so the
 * `hodith_alerts` channel that `HodithApplication.onCreate()` normally creates doesn't exist here
 * either, and `setUp` creates it itself.
 *
 * Each test posts its own decoy notification under an arbitrary id and passes that same id as
 * [EXTRA_NOTIFICATION_ID], then asserts it's gone afterward — [NotificationActionReceiver] cancels
 * whatever id it's told to, so this doesn't need to reproduce [SystemNotifier]'s private id scheme.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationActionReceiverTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var repository: HodithRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var clock: Clock

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private var insertedCaseId = 0L
    private var originalTheme = AppTheme.PLAIN

    @Before
    fun setUp() =
        runBlocking {
            hiltRule.inject()
            context = ApplicationProvider.getApplicationContext()
            notificationManager = context.getSystemService(NotificationManager::class.java)
            grantPostNotificationsPermission()
            ensureNotificationChannel(context, PlainVoice)
            originalTheme = settingsRepository.observeTheme().first()
            settingsRepository.setTheme(AppTheme.PLAIN)
        }

    @After
    fun tearDown() =
        runBlocking {
            settingsRepository.setTheme(originalTheme)
            if (insertedCaseId != 0L) {
                repository.getCase(insertedCaseId)?.let { repository.deleteCase(it) }
            }
        }

    @Test
    fun actionLog_oneTapCase_insertsEventAndCancelsTheNotification() =
        runBlocking {
            val caseName = "Coffee ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, logFlow = LogFlow.ONE_TAP))
            val notificationId = postDecoyNotification()

            context.sendBroadcast(logIntent(insertedCaseId, notificationId))

            val event = waitFor { repository.getMostRecentEventForCase(insertedCaseId) }
            assertNotNull("Expected the Log action to insert an event for a ONE_TAP Case", event)
            assertTrue(
                "Expected the notification to be cancelled after the Log action",
                waitForNotificationGone(notificationId),
            )
        }

    @Test
    fun actionLog_detailSheetCase_launchesTrampolineAndSavesThroughIt() =
        runBlocking {
            val caseName = "Migraine ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, logFlow = LogFlow.DETAIL_SHEET))
            val notificationId = postDecoyNotification()

            context.sendBroadcast(logIntent(insertedCaseId, notificationId))

            assertTrue(
                "Expected the notification to be cancelled once the Log action hands off to the trampoline",
                waitForNotificationGone(notificationId),
            )

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(PlainVoice.logSheetSaveButton).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

            val event = waitFor { repository.getMostRecentEventForCase(insertedCaseId) }
            assertNotNull("Expected saving the trampoline sheet to insert an event for the DETAIL_SHEET Case", event)
        }

    @Test
    fun actionAllQuiet_updatesLastCheckInAtWithoutLoggingAnEvent() =
        runBlocking {
            val caseName = "Migraine ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, lastCheckInAt = 0L))
            val notificationId = postDecoyNotification()

            context.sendBroadcast(allQuietIntent(insertedCaseId, notificationId))

            val updatedCase = waitFor { repository.getCase(insertedCaseId)?.takeIf { it.lastCheckInAt != 0L } }
            assertNotNull("Expected the All quiet action to update lastCheckInAt", updatedCase)
            assertNotEquals(0L, updatedCase!!.lastCheckInAt)
            assertNull(
                "Expected the All quiet action not to log an event",
                repository.getMostRecentEventForCase(insertedCaseId),
            )
            assertTrue(
                "Expected the notification to be cancelled after the All quiet action",
                waitForNotificationGone(notificationId),
            )
        }

    private fun logIntent(
        caseId: Long,
        notificationId: Int,
    ) = Intent(ACTION_LOG, null, context, NotificationActionReceiver::class.java)
        .putExtra(EXTRA_CASE_ID, caseId)
        .putExtra(EXTRA_NOTIFICATION_ID, notificationId)

    private fun allQuietIntent(
        caseId: Long,
        notificationId: Int,
    ) = Intent(ACTION_ALL_QUIET, null, context, NotificationActionReceiver::class.java)
        .putExtra(EXTRA_CASE_ID, caseId)
        .putExtra(EXTRA_NOTIFICATION_ID, notificationId)

    private fun postDecoyNotification(): Int {
        val notificationId = nextDecoyId++
        val notification =
            NotificationCompat
                .Builder(context, ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Test notification")
                .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return notificationId
    }

    private suspend fun waitForNotificationGone(notificationId: Int): Boolean =
        waitFor { notificationManager.activeNotifications.none { it.id == notificationId }.takeIf { it } } ?: false

    private fun grantPostNotificationsPermission() {
        val command = "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private suspend fun <T> waitFor(
        maxAttempts: Int = 30,
        poll: suspend () -> T?,
    ): T? {
        var result = poll()
        var attempts = 0
        while (result == null && attempts < maxAttempts) {
            Thread.sleep(200)
            result = poll()
            attempts++
        }
        return result
    }

    companion object {
        private var nextDecoyId = 555_001
    }
}
