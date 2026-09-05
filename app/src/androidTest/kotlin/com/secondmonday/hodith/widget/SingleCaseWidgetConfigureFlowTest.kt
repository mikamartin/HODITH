package com.secondmonday.hodith.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.isSelectable
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
 * Real end-to-end regression test: launches the actual [SingleCaseWidgetConfigureActivity],
 * interacts with its real Compose picker dialog (finds and taps the real RadioButton/confirm
 * button), and waits for the real Activity to finish — exactly the path a user goes through
 * adding the widget. See [ListWidgetConfigureFlowTest] for why this drives the real Activity
 * rather than calling the ViewModel/Glance functions directly.
 *
 * Requires the emulator/device to have pre-granted bind permission:
 * `adb shell appwidget grantbind --package com.secondmonday.hodith --user 0`. Which package needs
 * the grant is emulator-image-dependent (see DEV_PLAYBOOK.md §5) — on a Google Play/GMS-enabled
 * local image, grant `com.secondmonday.hodith.test` instead if this fails with
 * `bindAppWidgetIdIfAllowed failed` despite grantbind reporting success.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SingleCaseWidgetConfigureFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

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
    fun singleCaseWidget_showsBoundCase_afterRealConfigureFlow() =
        runBlocking {
            val caseIcon = "🐛"
            val caseName = "Coffee ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, icon = caseIcon))

            appWidgetId = host.allocateAppWidgetId()
            val provider = ComponentName(context, SingleCaseWidgetReceiver::class.java)
            val bound = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(appWidgetId, provider)
            assertTrue("bindAppWidgetIdIfAllowed failed - is bind permission granted for this package?", bound)

            val intent =
                Intent(context, SingleCaseWidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val scenario = ActivityScenario.launch<SingleCaseWidgetConfigureActivity>(intent)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(caseName, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.clickRowControl(caseName, isSelectable())
            composeTestRule.onNodeWithText(PlainVoice.singleCaseWidgetConfigureConfirmAction).performClick()

            var attempts = 0
            while (scenario.state != Lifecycle.State.DESTROYED && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            assertTrue(
                "SingleCaseWidgetConfigureActivity never finished after confirming the picker",
                scenario.state == Lifecycle.State.DESTROYED,
            )

            var texts = collectRenderedText()
            var renderAttempts = 0
            while (texts.none { it == caseIcon } && renderAttempts < 30) {
                Thread.sleep(200)
                texts = collectRenderedText()
                renderAttempts++
            }

            assertFalse(
                "Widget still shows the Case-not-found message after the real configure flow",
                texts.any { it.contains(PlainVoice.widgetCaseNotFoundMessage) },
            )
            assertTrue("Expected the bound Case's icon '$caseIcon' to render, but saw: $texts", texts.any { it == caseIcon })
        }

    private fun collectRenderedText(): List<String> = collectText(renderedView(context, host, appWidgetId))

    companion object {
        private const val HOST_ID = 424243
    }
}
