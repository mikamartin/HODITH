package com.secondmonday.hodith.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondmonday.hodith.MainActivity
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Real end-to-end tests that drive the actual rendered Single-case widget View via a real
 * [AppWidgetHost] (same pattern as [ListWidgetConfigureFlowTest]): the [QuickLogAction] click-
 * through, and a couple of render assertions ([HomeCaseRow]-derived content that only a real
 * Glance render produces — the icon tap opening Case detail, the "+" glyph's render size).
 *
 * The `ActionCallback` has no unit coverage: it needs a real `Context` with a live Hilt component
 * ([dagger.hilt.android.EntryPointAccessors]) and a real `WorkManager`
 * ([WidgetRefreshWorker.enqueueRefresh]), neither of which exists on the JVM, and per
 * `docs/CLEANUP_LOG.md` this repo doesn't use Robolectric or a mocking library — so an instrumented
 * click-through, not an extracted seam, is the fitting way to cover this one-line-pass-through glue.
 * The render assertions ride the same real-host setup because Glance's output can't be inspected
 * any other way.
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
 * `adb shell appwidget grantbind --package com.secondmonday.hodith --user 0`. Which package needs
 * the grant is emulator-image-dependent (see DEV_PLAYBOOK.md §5) — on a Google Play/GMS-enabled
 * local image, grant `com.secondmonday.hodith.test` instead if this fails with
 * `bindAppWidgetIdIfAllowed failed` despite grantbind reporting success.
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
    private var secondWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
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
            if (secondWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                host.deleteAppWidgetId(secondWidgetId)
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
    fun logTap_startsASecondEventForARunningTrackedCase() =
        runBlocking {
            val caseName = "Migraine ${System.currentTimeMillis()}"
            insertedCaseId =
                repository.insertCase(
                    testCase(name = caseName, tracksDuration = true, logFlow = LogFlow.ONE_TAP),
                )
            repository.insertEvent(testEvent(caseId = insertedCaseId, occurredAt = 0L, endedAt = null))
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)

            // The log button stays put while an event runs (spec §6) — no Stop on the widget.
            val button = waitForClickableWithDescription(PlainVoice.quickLogButtonDescription(caseName))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { button.performClick() }

            val bothRunning =
                waitFor {
                    repository
                        .eventsInWindow(insertedCaseId, 0L, Long.MAX_VALUE)
                        .takeIf { it.size == 2 && it.all { e -> e.endedAt == null } }
                }
            assertNotNull("Expected the log button to start a second concurrent event", bothRunning)
        }

    @Test
    fun logFromOneWidget_refreshesASecondWidgetForTheSameCase() =
        runBlocking {
            val caseName = "Coffee ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, logFlow = LogFlow.ONE_TAP))
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)
            secondWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)
            val secondBefore = collectText(renderedView(context, host, secondWidgetId))

            val button = waitForClickableWithDescription(PlainVoice.quickLogButtonDescription(caseName), appWidgetId)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { button.performClick() }

            assertNotNull(waitFor { repository.getMostRecentEventForCase(insertedCaseId) })
            val secondAfter =
                waitFor { collectText(renderedView(context, host, secondWidgetId)).takeIf { it != secondBefore } }
            assertNotNull("The second widget should re-render after logging from the first", secondAfter)
        }

    @Test
    fun singleCaseWidget_iconTap_opensCaseDetail() {
        runBlocking {
            val caseName = "Noisy neighbours ${System.currentTimeMillis()}"
            val icon = "🐛"
            insertedCaseId = repository.insertCase(testCase(name = caseName, icon = icon))
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)

            // The icon fills its own non-weighted zone at the top of the tile (SingleCaseWidget.kt);
            // tapping it opens the Case's detail screen.
            val caseArea = waitForClickableWithText(icon)

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)
            try {
                instrumentation.runOnMainSync { caseArea.performClick() }
                val activity: Activity? = monitor.waitForActivityWithTimeout(5_000)
                assertNotNull("Expected tapping the icon to open MainActivity", activity)
                assertEquals(insertedCaseId, activity?.intent?.getLongExtra(EXTRA_CASE_ID, -1L))
                activity?.finish()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    // WidgetCaseNameSize has no render-test coverage here: the Single-case widget never shows
    // row.name (only its icon), and the List widget's CaseRow that does render it lives inside a
    // LazyColumn this harness can't populate (see this class's doc comment). The JVM
    // WidgetTokenFidelityTest is the only coverage for that token's fidelity to Plain's titleMedium.

    @Test
    fun singleCaseWidget_plusGlyph_rendersAtWidgetGlyphButtonSize() =
        runBlocking {
            insertedCaseId = repository.insertCase(testCase())
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, insertedCaseId)

            // WidgetGlyphButtonSize has no Home equivalent (Home's log button is a vector icon) —
            // this locks the widget's own value so ListWidget/SingleCaseWidget can't drift apart.
            val plusTextView =
                waitFor { findTextViewWithText(renderedView(context, host, appWidgetId), "+") }
                    ?: throw AssertionError("No '+' TextView rendered")
            assertEquals(spToPx(context, WidgetGlyphButtonSize.value), plusTextView.textSize, 0.5f)
        }

    private suspend fun waitForClickableWithDescription(
        description: String,
        widgetId: Int = appWidgetId,
    ): View =
        waitFor { findClickableAncestorOfDescription(renderedView(context, host, widgetId), description) }
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

    companion object {
        private const val HOST_ID = 424244
    }
}
