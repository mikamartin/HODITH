package com.secondmonday.hodith.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondmonday.hodith.MainActivity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.ui.voice.PlainVoice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Real end-to-end regression test for [QuickLogAction]/[StopEventAction] — clicks the actual
 * rendered widget View (via a real [AppWidgetHost]), same pattern as [ListWidgetConfigureFlowTest].
 * These two `ActionCallback`s have no unit coverage: they need a real `Context` with a live Hilt
 * component ([dagger.hilt.android.EntryPointAccessors]) and a real `WorkManager`
 * ([WidgetRefreshWorker.enqueueRefresh]), neither of which exists on the JVM, and per
 * `docs/CLEANUP_LOG.md` this repo doesn't use Robolectric or a mocking library — so an instrumented
 * click-through, not an extracted seam, is the fitting way to cover this one-line-pass-through glue.
 *
 * Drives the Single-case widget rather than the List widget, even though both wire up the exact
 * same callbacks: the List widget's rows live inside a `LazyColumn`, which Glance renders as a
 * `ListView` backed by a `RemoteViewsService` adapter that only populates once its host view is
 * attached to a real window and laid out — `AppWidgetHost.createView()` alone never triggers that,
 * so its rows stay permanently empty in this kind of test (confirmed by manual inspection: the
 * `ListView` had zero children after 10s of polling). The Single-case widget's content isn't
 * behind an adapter — it's rendered directly into the host view — so it's clickable as soon as
 * `createView()` returns real content.
 *
 * Requires the emulator/device to have pre-granted bind permission:
 * `adb shell appwidget grantbind --package com.secondmonday.hodith --user 0`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WidgetActionsFlowTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: HodithRepository

    private lateinit var context: Context
    private lateinit var host: AppWidgetHost
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var insertedCaseId = 0L

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        host = AppWidgetHost(context, HOST_ID)
        host.startListening()
    }

    @After
    fun tearDown() =
        runBlocking {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                host.deleteAppWidgetId(appWidgetId)
            }
            host.stopListening()
            if (insertedCaseId != 0L) {
                repository.getCase(insertedCaseId)?.let { repository.deleteCase(it) }
            }
        }

    @Test
    fun quickLogTap_insertsAnEventForAOneTapCase() =
        runBlocking {
            val caseName = "Coffee ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, logFlow = LogFlow.ONE_TAP))
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)

            val button = waitForClickableWithDescription(PlainVoice.quickLogButtonDescription(caseName))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { button.performClick() }

            val event = waitFor { repository.getMostRecentEventForCase(insertedCaseId) }
            assertNotNull("Expected QuickLogAction to insert an event for the tapped Case", event)
        }

    @Test
    fun stopTap_endsTheOngoingEventForAStartStopCase() =
        runBlocking {
            val caseName = "Migraine ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, durationMode = DurationMode.START_STOP))
            val eventId = repository.insertEvent(testEvent(caseId = insertedCaseId, occurredAt = 0L, endedAt = null))
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)

            val button = waitForClickableWithText(PlainVoice.widgetStopAction)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { button.performClick() }

            val stopped = waitFor { repository.getEvent(eventId)?.takeIf { it.endedAt != null } }
            assertNotNull("Expected StopEventAction to set the ongoing event's endedAt", stopped)
        }

    @Test
    fun multipleRunning_showsCountPill_notStopButton_andOpensCaseDetail() {
        runBlocking {
            val caseName = "Noisy neighbours ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, durationMode = DurationMode.START_STOP))
            repository.insertEvent(testEvent(caseId = insertedCaseId, occurredAt = 0L, endedAt = null))
            repository.insertEvent(testEvent(caseId = insertedCaseId, occurredAt = 1_000L, endedAt = null))
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)

            val pill = waitForClickableWithText(PlainVoice.widgetRunningCount(2))
            assertNull(
                "Expected no Stop button while more than one event runs",
                findClickableAncestorOfText(renderedView(context, host, appWidgetId), PlainVoice.widgetStopAction),
            )

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)
            try {
                instrumentation.runOnMainSync { pill.performClick() }
                val activity: Activity? = monitor.waitForActivityWithTimeout(5_000)
                assertNotNull("Expected the running-count pill to open MainActivity", activity)
                assertEquals(insertedCaseId, activity?.intent?.getLongExtra(EXTRA_CASE_ID, -1L))
                activity?.finish()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private suspend fun waitForClickableWithDescription(description: String): View =
        waitFor { findClickableAncestorOfDescription(renderedView(context, host, appWidgetId), description) }
            ?: throw AssertionError("No clickable ancestor of a View with contentDescription '$description' rendered")

    private suspend fun waitForClickableWithText(text: String): View =
        waitFor { findClickableAncestorOfText(renderedView(context, host, appWidgetId), text) }
            ?: throw AssertionError("No clickable ancestor of a TextView with text '$text' rendered")

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

    // Glance renders .clickable(...).semantics { contentDescription = ... } as two separate
    // nested Views — the OnClickListener lands on the outer clickable wrapper, the
    // contentDescription on an inner one — so this tracks the nearest clickable ancestor while
    // descending and returns it once the matching contentDescription is found.
    private fun findClickableAncestorOfDescription(
        view: View,
        description: String,
        clickableAncestor: View? = if (view.hasOnClickListeners()) view else null,
    ): View? {
        if (view.contentDescription == description) return clickableAncestor
        if (view is ViewGroup) {
            val ancestor = if (view.hasOnClickListeners()) view else clickableAncestor
            for (i in 0 until view.childCount) {
                findClickableAncestorOfDescription(view.getChildAt(i), description, ancestor)?.let { return it }
            }
        }
        return null
    }

    companion object {
        private const val HOST_ID = 424244
    }
}
