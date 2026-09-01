package com.secondmonday.hodith.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.BackupEvent
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
        backupEvents: MutableSharedFlow<BackupEvent> = MutableSharedFlow(extraBufferCapacity = 1),
        onThemeSelect: (AppTheme) -> Unit = {},
        onTimeFormatSelect: (TimeFormat) -> Unit = {},
        onCheckInDefaultIntervalSelect: (CheckInDefaultInterval) -> Unit = {},
        onCloudBackupToggle: (Boolean) -> Unit = {},
        onLoadDemoData: () -> Unit = {},
        onDeleteAllData: () -> Unit = {},
        onExportClick: () -> Unit = {},
        onImportConfirm: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
        onContactUs: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                SettingsScreen(
                    uiState = uiState,
                    demoDataLoaded = demoDataLoaded,
                    backupEvents = backupEvents,
                    onThemeSelect = onThemeSelect,
                    onTimeFormatSelect = onTimeFormatSelect,
                    onCheckInDefaultIntervalSelect = onCheckInDefaultIntervalSelect,
                    onCloudBackupToggle = onCloudBackupToggle,
                    onLoadDemoData = onLoadDemoData,
                    onDeleteAllData = onDeleteAllData,
                    onExportClick = onExportClick,
                    onImportConfirm = onImportConfirm,
                    onOpenAbout = onOpenAbout,
                    onContactUs = onContactUs,
                )
            }
        }
    }

    @Test
    fun aboutButton_invokesOnOpenAbout() {
        var opened = false
        setContent(onOpenAbout = { opened = true })

        composeTestRule.onNodeWithText(PlainVoice.aboutScreenTitle).performClick()

        assertEquals(true, opened)
    }

    @Test
    fun rateAppButton_showsComingSoonSnackbar() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.settingsRateAppButton).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(PlainVoice.comingSoonPlaceholder)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun contactUsButton_invokesOnContactUs() {
        var contacted = false
        setContent(onContactUs = { contacted = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsContactUsButton).performClick()

        assertEquals(true, contacted)
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
    fun themeInfoIcon_opensAndDismissesDialog() {
        setContent()

        // Theme's info icon is the first of the screen's three (Theme, then Check-ins, then the
        // cloud-backup toggle) — see CaseEditScreenTest for the same shared-content-description
        // convention.
        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)
            .onFirst()
            .performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsThemeInfoTitle).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsThemeInfoTitle).assertDoesNotExist()
    }

    @Test
    fun timeFormatSection_showsBothOptions() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.timeFormatOption12Hour).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.timeFormatOption24Hour).assertExists()
    }

    @Smoke
    @Test
    fun timeFormatOption_tapInvokesCallbackWithThatFormat() {
        var selected: TimeFormat? = null
        setContent(
            uiState = SettingsUiState(timeFormat = TimeFormat.TWELVE_HOUR, isLoading = false),
            onTimeFormatSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.timeFormatOption24Hour).performClick()

        assertEquals(TimeFormat.TWENTY_FOUR_HOUR, selected)
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
    fun checkInInfoIcon_opensDialog() {
        setContent()

        // Check-ins' info icon is the middle of the screen's three (Theme, then Check-ins, then
        // the cloud-backup toggle) — see themeInfoIcon_opensAndDismissesDialog above.
        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)[1]
            .performClick()

        composeTestRule.onNodeWithText(PlainVoice.settingsCheckInInfoTitle).assertExists()
    }

    @Test
    fun cloudBackupInfoIcon_opensDialog() {
        setContent()

        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)
            .onLast()
            .performClick()

        composeTestRule.onNodeWithText(PlainVoice.settingsCloudBackupInfoTitle).assertExists()
    }

    @Test
    fun developerMode_hiddenByDefault() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.settingsLoadDemoDataButton).assertDoesNotExist()
    }

    @Test
    fun developerMode_shownWhenUnlocked() {
        setContent(uiState = SettingsUiState(theme = AppTheme.PLAIN, developerModeUnlocked = true, isLoading = false))

        composeTestRule.onNodeWithText(PlainVoice.settingsLoadDemoDataButton).assertExists()
    }

    @Test
    fun loadDemoData_tapInvokesCallback() {
        var loaded = false
        setContent(
            uiState = SettingsUiState(theme = AppTheme.PLAIN, developerModeUnlocked = true, isLoading = false),
            onLoadDemoData = { loaded = true },
        )

        // performScrollTo() first: the Developer Mode plank is the last item in this screen's
        // scrolling Column, below the fold on typical screen sizes — see SharePreviewScreenTest
        // for the same pattern.
        composeTestRule.onNodeWithText(PlainVoice.settingsLoadDemoDataButton).performScrollTo().performClick()

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

        // Scroll first: delete-all is the last row of the last plank, below the fold on typical
        // window sizes — same reason as loadDemoData_tapInvokesCallback.
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataButton).performScrollTo().performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataConfirmAction).performClick()

        assertEquals(true, deleted)
    }

    @Test
    fun deleteAllData_cancelDoesNotInvokeCallback() {
        var deleted = false
        setContent(onDeleteAllData = { deleted = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataButton).performScrollTo().performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.settingsDeleteAllDataCancelAction).performClick()

        assertFalse(deleted)
    }

    @Smoke
    @Test
    fun cloudBackupToggle_tapInvokesCallbackWithFlippedValue() {
        var toggledTo: Boolean? = null
        setContent(uiState = SettingsUiState(cloudBackupEnabled = true, isLoading = false), onCloudBackupToggle = { toggledTo = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.settingsCloudBackupToggleLabel).performClick()

        assertEquals(false, toggledTo)
    }

    @Smoke
    @Test
    fun exportButton_tapInvokesCallback() {
        var exported = false
        setContent(onExportClick = { exported = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsExportButton).performClick()

        assertEquals(true, exported)
    }

    @Smoke
    @Test
    fun importButton_opensConfirmDialog_confirmInvokesCallback() {
        var confirmed = false
        setContent(onImportConfirm = { confirmed = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsImportButton).performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsImportConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.settingsImportConfirmAction).performClick()

        assertEquals(true, confirmed)
    }

    @Test
    fun importButton_cancelDoesNotInvokeCallback() {
        var confirmed = false
        setContent(onImportConfirm = { confirmed = true })

        composeTestRule.onNodeWithText(PlainVoice.settingsImportButton).performClick()
        composeTestRule.onNodeWithText(PlainVoice.settingsImportConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.settingsImportCancelAction).performClick()

        assertFalse(confirmed)
    }

    @Test
    fun backupEvent_showsMatchingSnackbarMessage() {
        val backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
        setContent(backupEvents = backupEvents)

        composeTestRule.runOnIdle { backupEvents.tryEmit(BackupEvent.ExportSuccess) }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(PlainVoice.settingsExportSuccessMessage)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
