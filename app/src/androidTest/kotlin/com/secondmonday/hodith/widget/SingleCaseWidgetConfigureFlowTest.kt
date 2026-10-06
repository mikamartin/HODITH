package com.secondmonday.hodith.widget

import android.app.Activity
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
import org.junit.Assert.assertEquals
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
    private val allocatedWidgetIds = mutableListOf<Int>()
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
            allocatedWidgetIds.forEach { host.deleteAppWidgetId(it) }
            host.stopListening()
            insertedCaseIds.forEach { id -> repository.getCase(id)?.let { repository.deleteCase(it) } }
        }

    @Test
    fun singleCaseWidget_showsBoundCase_afterRealConfigureFlow() =
        runBlocking {
            val caseIcon = "🐛"
            val caseName = "Coffee ${System.currentTimeMillis()}"
            insertedCaseIds += repository.insertCase(testCase(name = caseName, icon = caseIcon))

            val appWidgetId = bindWidgetId()
            configureWidgetFor(appWidgetId, caseName)

            var texts = collectRenderedText(appWidgetId)
            var renderAttempts = 0
            while (texts.none { it == caseIcon } && renderAttempts < 30) {
                Thread.sleep(200)
                texts = collectRenderedText(appWidgetId)
                renderAttempts++
            }

            assertFalse(
                "Widget still shows the Case-not-found message after the real configure flow",
                texts.any { it.contains(PlainVoice.widgetCaseNotFoundMessage) },
            )
            assertTrue("Expected the bound Case's icon '$caseIcon' to render, but saw: $texts", texts.any { it == caseIcon })
        }

    @Test
    fun singleCaseWidget_twoInstances_eachShowsItsOwnCase() =
        runBlocking {
            val suffix = System.currentTimeMillis()
            val coffeeIcon = "🐛"
            val teaIcon = "🫖"
            val coffeeName = "Coffee $suffix"
            val teaName = "Tea $suffix"
            insertedCaseIds += repository.insertCase(testCase(name = coffeeName, icon = coffeeIcon))
            insertedCaseIds += repository.insertCase(testCase(name = teaName, icon = teaIcon))

            val coffeeWidgetId = bindWidgetId()
            configureWidgetFor(coffeeWidgetId, coffeeName)
            val teaWidgetId = bindWidgetId()
            configureWidgetFor(teaWidgetId, teaName)

            val coffeeTexts = awaitRenderedIcon(coffeeWidgetId, coffeeIcon)
            val teaTexts = awaitRenderedIcon(teaWidgetId, teaIcon)

            assertTrue("First instance should show only Coffee, but saw: $coffeeTexts", coffeeTexts.none { it == teaIcon })
            assertTrue("Second instance should show only Tea, but saw: $teaTexts", teaTexts.none { it == coffeeIcon })
        }

    @Test
    fun singleCaseWidget_cancelingThePicker_finishesWithResultCanceled() =
        runBlocking {
            val caseName = "Coffee ${System.currentTimeMillis()}"
            insertedCaseIds += repository.insertCase(testCase(name = caseName))

            val appWidgetId = bindWidgetId()
            val scenario = launchConfigure(appWidgetId)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(caseName, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(PlainVoice.widgetConfigureSkipAction).performClick()

            awaitDestroyed(scenario)
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }

    /** Allocates and binds a widget id, tracked so [tearDown] deletes it. */
    private fun bindWidgetId(): Int {
        val appWidgetId = host.allocateAppWidgetId()
        allocatedWidgetIds += appWidgetId
        val provider = ComponentName(context, SingleCaseWidgetReceiver::class.java)
        val bound = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(appWidgetId, provider)
        assertTrue("bindAppWidgetIdIfAllowed failed - is bind permission granted for this package?", bound)
        return appWidgetId
    }

    private fun launchConfigure(appWidgetId: Int): ActivityScenario<SingleCaseWidgetConfigureActivity> {
        val intent =
            Intent(context, SingleCaseWidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return ActivityScenario.launchActivityForResult(intent)
    }

    /** Runs the real picker for [appWidgetId], choosing the Case named [caseName] and confirming. */
    private fun configureWidgetFor(
        appWidgetId: Int,
        caseName: String,
    ) {
        val scenario = launchConfigure(appWidgetId)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(caseName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.clickRowControl(caseName, isSelectable())
        composeTestRule.onNodeWithText(PlainVoice.singleCaseWidgetConfigureConfirmAction).performClick()

        awaitDestroyed(scenario)
    }

    private fun awaitDestroyed(scenario: ActivityScenario<*>) {
        var attempts = 0
        while (scenario.state != Lifecycle.State.DESTROYED && attempts < 50) {
            Thread.sleep(100)
            attempts++
        }
        assertTrue(
            "SingleCaseWidgetConfigureActivity never finished",
            scenario.state == Lifecycle.State.DESTROYED,
        )
    }

    private fun awaitRenderedIcon(
        appWidgetId: Int,
        icon: String,
    ): List<String> {
        var texts = collectRenderedText(appWidgetId)
        var renderAttempts = 0
        while (texts.none { it == icon } && renderAttempts < 30) {
            Thread.sleep(200)
            texts = collectRenderedText(appWidgetId)
            renderAttempts++
        }
        assertTrue("Expected the icon '$icon' to render, but saw: $texts", texts.any { it == icon })
        return texts
    }

    private fun collectRenderedText(appWidgetId: Int): List<String> = collectText(renderedView(context, host, appWidgetId))

    companion object {
        private const val HOST_ID = 424243
    }
}
