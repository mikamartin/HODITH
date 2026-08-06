package com.secondmonday.hodith.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.ui.voice.PlainVoice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
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
 * [WidgetLogTrampolineActivity] is the widget's DETAIL_SHEET entry point (spec §15), but the row
 * that launches it lives inside a Glance `LazyColumn` — a `RemoteViewsService`-backed `ListView`
 * that never populates under `AppWidgetHost.createView()` (see [WidgetActionsFlowTest]'s doc
 * comment), so that row tap itself can't be driven from an instrumented test. This test drives the
 * Activity directly with the same `EXTRA_CASE_ID` extra the widget row supplies, covering
 * everything downstream of that tap: the real sheet renders, Save persists a real event via
 * [com.secondmonday.hodith.viewmodel.WidgetLogSheetViewModel], and the Activity finishes.
 *
 * Pins the theme to [AppTheme.PLAIN] for the duration of the test and restores whatever was
 * persisted before — [WidgetLogTrampolineActivity] reads the real Settings theme (unlike the
 * widget's own chrome, which is fixed to [PlainVoice]), so a stale non-Plain theme left over from
 * manual testing on the same device would otherwise make the [PlainVoice] string lookups below
 * flaky.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WidgetLogTrampolineActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var repository: HodithRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var context: Context
    private var insertedCaseId = 0L
    private var originalTheme = AppTheme.PLAIN

    @Before
    fun setUp() =
        runBlocking {
            hiltRule.inject()
            context = ApplicationProvider.getApplicationContext()
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
    fun save_insertsAnEventAndFinishesTheActivity() =
        runBlocking {
            val caseName = "Migraine ${System.currentTimeMillis()}"
            insertedCaseId = repository.insertCase(testCase(name = caseName, logFlow = LogFlow.DETAIL_SHEET))

            val intent =
                Intent(context, WidgetLogTrampolineActivity::class.java)
                    .putExtra(EXTRA_CASE_ID, insertedCaseId)
            val scenario = ActivityScenario.launch<WidgetLogTrampolineActivity>(intent)

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText(PlainVoice.logSheetSaveButton).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

            var attempts = 0
            while (scenario.state != Lifecycle.State.DESTROYED && attempts < 50) {
                Thread.sleep(100)
                attempts++
            }
            assertEquals(
                "Expected WidgetLogTrampolineActivity to finish itself after Save",
                Lifecycle.State.DESTROYED,
                scenario.state,
            )

            val event = repository.getMostRecentEventForCase(insertedCaseId)
            assertNotNull("Expected Save to insert an event for the trampoline's Case", event)
        }
}
