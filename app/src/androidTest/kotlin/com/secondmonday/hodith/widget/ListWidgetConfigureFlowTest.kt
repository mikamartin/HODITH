package com.secondmonday.hodith.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.ui.voice.PlainVoice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Real end-to-end regression test: launches the actual [ListWidgetConfigureActivity], interacts
 * with its real Compose picker dialog (finds and taps the real Switch/confirm button), and waits
 * for the real Activity to finish — exactly the path a user goes through adding the widget.
 *
 * An earlier version of this test called `ListWidgetConfigureViewModel`/`Glance` functions
 * directly instead of driving the real Activity + dialog, and it passed even though the real
 * on-device flow was still broken — it proved the underlying data/render pipeline works, but not
 * that the Activity actually reaches that pipeline. This version exists specifically to close that
 * gap: it fails if `finishConfigure()` never runs, if `confirmSelection()`'s write doesn't happen,
 * or if the render pipeline itself is broken.
 *
 * Requires the emulator/device to have pre-granted bind permission:
 * `adb shell appwidget grantbind --package com.secondmonday.hodith --user 0`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ListWidgetConfigureFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var repository: HodithRepository

    private lateinit var context: Context
    private lateinit var host: AppWidgetHost
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val insertedCaseIds = mutableListOf<Long>()

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
            insertedCaseIds.forEach { id -> repository.getCase(id)?.let { repository.deleteCase(it) } }
        }

    @Test
    fun listWidget_showsBothCases_afterRealConfigureFlow() =
        runBlocking {
            val suffix = System.currentTimeMillis()
            val coffeeName = "Coffee $suffix"
            val migraineName = "Migraine $suffix"
            insertedCaseIds += repository.insertCase(testCase(name = coffeeName))
            insertedCaseIds += repository.insertCase(testCase(name = migraineName))

            appWidgetId = host.allocateAppWidgetId()
            val provider = ComponentName(context, ListWidgetReceiver::class.java)
            val bound = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(appWidgetId, provider)
            assertTrue("bindAppWidgetIdIfAllowed failed - is bind permission granted for this package?", bound)

            val intent =
                Intent(context, ListWidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val scenario = ActivityScenario.launch<ListWidgetConfigureActivity>(intent)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(coffeeName, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.clickRowControl(coffeeName, isToggleable())
            composeTestRule.clickRowControl(migraineName, isToggleable())
            composeTestRule.onNodeWithText(PlainVoice.widgetConfigureConfirmAction).performClick()

            var attempts = 0
            while (scenario.state != Lifecycle.State.DESTROYED && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            assertTrue(
                "ListWidgetConfigureActivity never finished after confirming the picker",
                scenario.state == Lifecycle.State.DESTROYED,
            )

            var view = renderedView(context, host, appWidgetId)
            var renderAttempts = 0
            while (!containsListView(view) && renderAttempts < 30) {
                Thread.sleep(200)
                view = renderedView(context, host, appWidgetId)
                renderAttempts++
            }
            assertFalse(
                "Widget still shows the no-Cases-selected empty state after the real configure flow",
                collectText(view).any { it.contains(PlainVoice.widgetNoCasesSelectedMessage) },
            )
            assertTrue("Expected the selected-Cases row list (a ListView) to be present", containsListView(view))
        }

    private fun containsListView(view: View): Boolean =
        view is ListView || (view is ViewGroup && (0 until view.childCount).any { containsListView(view.getChildAt(it)) })

    companion object {
        private const val HOST_ID = 424242
    }
}
