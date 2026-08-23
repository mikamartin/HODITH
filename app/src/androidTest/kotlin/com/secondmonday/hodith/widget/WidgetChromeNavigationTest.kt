package com.secondmonday.hodith.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondmonday.hodith.MainActivity
import com.secondmonday.hodith.ui.voice.PlainVoice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the widget chrome that lives outside the List widget's `LazyColumn` — its title/header
 * and empty-state message, plus the Single-case widget's "Case is gone" message — all wired to
 * open [MainActivity] with no Case deep link. Unlike the row content *inside* the List widget's
 * `LazyColumn` (see [WidgetActionsFlowTest]'s doc comment on why that can't be driven here), these
 * boxes render directly into the host view and are clickable as soon as
 * `AppWidgetHost.createView()` returns.
 *
 * The Single-case "Case is gone" test binds to a Case id that was never inserted rather than
 * deleting a real one — functionally identical from the widget's point of view (its bound id just
 * doesn't match anything in `observeActiveCasesWithEvents()`), and avoids a delete-then-wait-for-
 * recompose race.
 *
 * Requires the emulator/device to have pre-granted bind permission:
 * `adb shell appwidget grantbind --package com.secondmonday.hodith --user 0`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WidgetChromeNavigationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var host: AppWidgetHost
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        host = AppWidgetHost(context, HOST_ID)
        host.startListening()
    }

    @After
    fun tearDown() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            host.deleteAppWidgetId(appWidgetId)
        }
        host.stopListening()
    }

    @Test
    fun listWidget_titleTap_opensMainActivity() =
        runBlocking {
            bindAndRenderListWidget()
            assertTapOpensMainActivity(PlainVoice.homeHeaderTitle)
        }

    @Test
    fun listWidget_emptyStateTap_opensMainActivity() =
        runBlocking {
            bindAndRenderListWidget()
            assertTapOpensMainActivity(PlainVoice.widgetNoCasesSelectedMessage)
        }

    @Test
    fun singleCaseWidget_missingCaseTap_opensMainActivity() =
        runBlocking {
            appWidgetId = bindAndRenderSingleCaseWidget(context, host, NONEXISTENT_CASE_ID)
            assertTapOpensMainActivity(PlainVoice.widgetCaseNotFoundMessage)
        }

    private fun assertTapOpensMainActivity(targetText: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        try {
            val target = waitForClickableWithText(targetText)
            instrumentation.runOnMainSync { target.performClick() }

            val activity: Activity? = monitor.waitForActivityWithTimeout(5_000)
            assertNotNull("Expected tapping '$targetText' to launch MainActivity", activity)
            activity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun bindAndRenderListWidget() =
        runBlocking {
            appWidgetId = host.allocateAppWidgetId()
            val provider = ComponentName(context, ListWidgetReceiver::class.java)
            val bound = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(appWidgetId, provider)
            assertTrue("bindAppWidgetIdIfAllowed failed - is bind permission granted for this package?", bound)

            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) { prefs -> prefs[CaseIdsKey] = emptySet() }
            ListWidget().update(context, glanceId)
        }

    private fun waitForClickableWithText(
        text: String,
        maxAttempts: Int = 30,
    ): View {
        var attempts = 0
        var found = findClickableAncestorOfText(renderedView(context, host, appWidgetId), text)
        while (found == null && attempts < maxAttempts) {
            Thread.sleep(200)
            found = findClickableAncestorOfText(renderedView(context, host, appWidgetId), text)
            attempts++
        }
        return found ?: throw AssertionError("No clickable ancestor of a TextView with text '$text' rendered")
    }

    companion object {
        private const val HOST_ID = 424245
        private const val NONEXISTENT_CASE_ID = Long.MAX_VALUE - 1
    }
}
