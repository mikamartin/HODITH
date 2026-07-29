package com.secondmonday.hodith.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.BrightVoice
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.SettingsUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * First Compose UI instrumented test for [SettingsScreen] (previously covered only at the
 * ViewModel level by `SettingsViewModelTest`). [SettingsScreen] is a stateless composable driven
 * entirely by plain data + callbacks, same pattern as `HomeScreenTest`/`ArchivedCasesScreenTest`
 * — no Hilt/Activity/Room needed.
 */
@UiTest
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: SettingsUiState = SettingsUiState(theme = AppTheme.PLAIN, isLoading = false),
        demoDataLoaded: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1),
        onThemeSelect: (AppTheme) -> Unit = {},
        onCheckInDefaultIntervalSelect: (CheckInDefaultInterval) -> Unit = {},
        onLoadDemoData: () -> Unit = {},
        onDeleteAllData: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                SettingsScreen(
                    uiState = uiState,
                    demoDataLoaded = demoDataLoaded,
                    onThemeSelect = onThemeSelect,
                    onCheckInDefaultIntervalSelect = onCheckInDefaultIntervalSelect,
                    onLoadDemoData = onLoadDemoData,
                    onDeleteAllData = onDeleteAllData,
                )
            }
        }
    }

    @Test
    fun themeSection_showsAllThreeOptions() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.themeOptionPlain).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.themeOptionIntense).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.themeOptionBright).assertExists()
    }

    @Smoke
    @Test
    fun themeOption_tapInvokesCallbackWithThatTheme() {
        var selected: AppTheme? = null
        setContent(onThemeSelect = { selected = it })

        composeTestRule.onNodeWithText(PlainVoice.themeOptionIntense).performClick()

        assertEquals(AppTheme.INTENSE, selected)
    }

    @Test
    fun checkInSection_showsAllFourOptions() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.checkInIntervalOptionOff).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.checkInIntervalOptionSeven).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.checkInIntervalOptionFourteen).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.checkInIntervalOptionThirty).assertExists()
    }

    @Smoke
    @Test
    fun checkInOption_tapInvokesCallbackWithThatInterval() {
        var selected: CheckInDefaultInterval? = null
        setContent(onCheckInDefaultIntervalSelect = { selected = it })

        composeTestRule.onNodeWithText(PlainVoice.checkInIntervalOptionFourteen).performClick()

        assertEquals(CheckInDefaultInterval.FOURTEEN, selected)
    }

    @Test
    fun previewCard_showsPreviewedThemeHeaderAndEmptyState() {
        setContent(uiState = SettingsUiState(theme = AppTheme.BRIGHT, isLoading = false))

        composeTestRule.onNodeWithText(BrightVoice.homeHeaderTitle).assertExists()
        composeTestRule.onNodeWithText(BrightVoice.noCasesEmptyState).assertExists()
    }

    @Test
    fun loadDemoData_tapInvokesCallback() {
        var loaded = false
        setContent(onLoadDemoData = { loaded = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsLoadDemoDataButton).performClick()

        assertEquals(true, loaded)
    }

    @Test
    fun demoDataLoaded_showsSnackbarMessage() {
        val demoDataLoaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        setContent(demoDataLoaded = demoDataLoaded)

        composeTestRule.runOnIdle { demoDataLoaded.tryEmit(Unit) }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(PlainVoice.settingsDemoDataLoadedMessage)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun deleteAllData_opensConfirmDialog_confirmInvokesCallback() {
        var deleted = false
        setContent(onDeleteAllData = { deleted = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataButton).performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataConfirmAction).performClick()

        assertEquals(true, deleted)
    }

    @Test
    fun deleteAllData_cancelDoesNotInvokeCallback() {
        var deleted = false
        setContent(onDeleteAllData = { deleted = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataButton).performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataCancelAction).performClick()

        assertFalse(deleted)
    }
}
