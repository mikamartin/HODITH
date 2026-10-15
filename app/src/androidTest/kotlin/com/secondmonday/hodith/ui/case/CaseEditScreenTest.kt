package com.secondmonday.hodith.ui.case

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseEditUiState
import com.secondmonday.hodith.viewmodel.IntensityToggleNotice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [CaseEditScreen] is a stateless composable driven entirely by plain data + callbacks, same
 * pattern as `HomeScreenTest`/`CaseDetailScreenTest`. Covers the archive icon/dialog added by
 * `feature/case-archive` plus the collapsible icon picker and logFlow-disabling behavior added by
 * `feature/case-edit-polish` — full field-by-field form coverage otherwise remains a separate,
 * pre-existing gap out of scope here.
 */
@UiTest
class CaseEditScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: CaseEditUiState = CaseEditUiState(isEditing = true, isLoading = false, canArchive = true),
        intensityToggleNotice: Flow<IntensityToggleNotice> = emptyFlow(),
        onArchive: () -> Unit = {},
        onTracksDurationChange: (Boolean) -> Unit = {},
        onConfirmTurnOffDuration: () -> Unit = {},
        onDismissTurnOffDuration: () -> Unit = {},
        onConfirmTurnOnDuration: () -> Unit = {},
        onDismissTurnOnDuration: () -> Unit = {},
        onIconSelect: (String) -> Unit = {},
        theme: AppTheme = AppTheme.PLAIN,
    ) {
        composeTestRule.setHodithContent(theme = theme) {
            CaseEditScreen(
                uiState = uiState,
                intensityToggleNotice = intensityToggleNotice,
                onNameChange = {},
                onDescriptionChange = {},
                onIconSelect = onIconSelect,
                onLogFlowChange = {},
                onTracksDurationChange = onTracksDurationChange,
                onConfirmTurnOffDuration = onConfirmTurnOffDuration,
                onDismissTurnOffDuration = onDismissTurnOffDuration,
                onConfirmTurnOnDuration = onConfirmTurnOnDuration,
                onDismissTurnOnDuration = onDismissTurnOnDuration,
                onIntensityToggle = {},
                onSave = {},
                onArchive = onArchive,
                onBack = {},
            )
        }
    }

    @Test
    fun archiveIcon_hiddenWhenCreatingNewCase() {
        setContent(uiState = CaseEditUiState(isEditing = false, isLoading = false, canArchive = false))

        composeTestRule.onNodeWithContentDescription(PlainVoice.archiveCaseDescription).assertDoesNotExist()
    }

    @Test
    fun archiveIcon_shownWhenEditingExistingCase() {
        setContent()

        composeTestRule.onNodeWithContentDescription(PlainVoice.archiveCaseDescription).assertExists()
    }

    @Test
    fun archiveIcon_opensConfirmDialog_confirmInvokesCallback() {
        var archived = false
        setContent(onArchive = { archived = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.archiveCaseDescription).performClick()
        composeTestRule.onNodeWithText(PlainVoice.archiveCaseConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.archiveCaseConfirmAction).performClick()

        assertEquals(true, archived)
    }

    @Test
    fun archiveIcon_cancelDoesNotInvokeCallback() {
        var archived = false
        setContent(onArchive = { archived = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.archiveCaseDescription).performClick()
        composeTestRule.onNodeWithText(PlainVoice.archiveCaseConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.archiveCaseCancelAction).performClick()

        assertFalse(archived)
    }

    @Test
    fun iconPicker_collapsedByDefaultWhenEditingExistingCase() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, icon = "🤕"))

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseIconSectionExpandDescription).assertExists()
    }

    @Test
    fun iconPicker_expandedByDefaultForNewCase() {
        setContent(uiState = CaseEditUiState(isEditing = false, isLoading = false))

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseIconSectionCollapseDescription).assertExists()
    }

    @Test
    fun iconPicker_tapToggles_expandedThenCollapsed() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, icon = "🤕"))

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseIconSectionExpandDescription).performClick()
        composeTestRule.onNodeWithContentDescription(PlainVoice.caseIconSectionCollapseDescription).assertExists()

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseIconSectionCollapseDescription).performClick()
        composeTestRule.onNodeWithContentDescription(PlainVoice.caseIconSectionExpandDescription).assertExists()
    }

    @Test
    fun iconChoice_tap_invokesOnIconSelect_underBrightTheme() {
        // IconChoice renders as BrightIconChoice (IconHalo + selection ring) under Bright, instead
        // of Plain/Intense's plain bordered circle -- confirms the click target still works.
        var selected: String? = null
        setContent(
            uiState = CaseEditUiState(isEditing = false, isLoading = false),
            onIconSelect = { selected = it },
            theme = AppTheme.BRIGHT,
        )

        composeTestRule.onNodeWithText(CASE_ICONS.first()).performClick()

        assertEquals(CASE_ICONS.first(), selected)
    }

    @Smoke
    @Test
    fun logFlow_oneTapEnabled_whenNoDurationAndNoIntensity() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, tracksDuration = false))

        composeTestRule.onNodeWithText(PlainVoice.caseOneTapToggleLabel).assertIsEnabled()
    }

    @Test
    fun logFlow_oneTapStaysEnabled_whenTracksDurationIsOn() {
        // One-tap is never gated by duration tracking (spec section 6 Story U2): a tracked event
        // starts open-ended by default, exactly like a non-tracked one.
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, tracksDuration = true))

        composeTestRule.onNodeWithText(PlainVoice.caseOneTapToggleLabel).assertIsEnabled()
    }

    @Test
    fun logFlow_oneTapHidden_whenIntensityEnabled() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, intensityEnabled = true))

        composeTestRule.onNodeWithText(PlainVoice.caseOneTapToggleLabel).assertDoesNotExist()
    }

    @Test
    fun infoIcon_opensAndDismissesDialog() {
        setContent()

        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)
            .onFirst()
            .performClick()
        composeTestRule.onNodeWithText(PlainVoice.caseTracksDurationInfoTitle).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.caseTracksDurationInfoTitle).assertDoesNotExist()
    }

    @Test
    fun oneTapInfoIcon_opensAndDismissesDialog() {
        setContent()

        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)
            .onLast()
            .performClick()
        composeTestRule.onNodeWithText(PlainVoice.caseOneTapInfoTitle).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.caseOneTapInfoTitle).assertDoesNotExist()
    }

    @Test
    fun turnOffDurationConfirm_whenFlagged_showsDialogNamingTheRunningCount() {
        setContent(
            uiState =
                CaseEditUiState(
                    isEditing = true,
                    isLoading = false,
                    tracksDuration = true,
                    runningEventCount = 2,
                    showTurnOffDurationConfirm = true,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.turnOffDurationConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.turnOffDurationConfirmBody(2)).assertExists()
    }

    @Test
    fun turnOffDurationConfirm_confirmAndCancel_invokeTheirCallbacks() {
        var confirmed = false
        var dismissed = false
        setContent(
            uiState =
                CaseEditUiState(
                    isEditing = true,
                    isLoading = false,
                    tracksDuration = true,
                    runningEventCount = 1,
                    showTurnOffDurationConfirm = true,
                ),
            onConfirmTurnOffDuration = { confirmed = true },
            onDismissTurnOffDuration = { dismissed = true },
        )

        composeTestRule.onNodeWithText(PlainVoice.turnOffDurationCancelAction).performClick()
        assertTrue(dismissed)

        composeTestRule.onNodeWithText(PlainVoice.turnOffDurationConfirmAction).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun turnOnDurationConfirm_whenFlagged_showsDialogNamingTheOpenEndedCount() {
        setContent(
            uiState =
                CaseEditUiState(
                    isEditing = true,
                    isLoading = false,
                    tracksDuration = false,
                    runningEventCount = 3,
                    showTurnOnDurationConfirm = true,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.turnOnDurationConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.turnOnDurationConfirmBody(3)).assertExists()
    }

    @Test
    fun turnOnDurationConfirm_confirmAndCancel_invokeTheirCallbacks() {
        var confirmed = false
        var dismissed = false
        setContent(
            uiState =
                CaseEditUiState(
                    isEditing = true,
                    isLoading = false,
                    tracksDuration = false,
                    runningEventCount = 1,
                    showTurnOnDurationConfirm = true,
                ),
            onConfirmTurnOnDuration = { confirmed = true },
            onDismissTurnOnDuration = { dismissed = true },
        )

        composeTestRule.onNodeWithText(PlainVoice.turnOnDurationCancelAction).performClick()
        assertTrue(dismissed)

        composeTestRule.onNodeWithText(PlainVoice.turnOnDurationConfirmAction).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun durationConfirms_neitherShown_whenFlagsClear() {
        setContent(
            uiState =
                CaseEditUiState(
                    isEditing = true,
                    isLoading = false,
                    tracksDuration = true,
                    runningEventCount = 2,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.turnOffDurationConfirmTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.turnOnDurationConfirmTitle).assertDoesNotExist()
    }

    @Test
    fun intensityToggleNotice_enabled_showsEnableSnackbarWithEventCount() {
        val notices = Channel<IntensityToggleNotice>(capacity = 1)
        setContent(intensityToggleNotice = notices.receiveAsFlow())

        notices.trySend(IntensityToggleNotice(enabled = true, eventCount = 3))

        composeTestRule.onNodeWithText(PlainVoice.enableIntensityNotice(3)).assertExists()
    }

    @Test
    fun intensityToggleNotice_disabled_showsDisableSnackbarWithEventCount() {
        val notices = Channel<IntensityToggleNotice>(capacity = 1)
        setContent(intensityToggleNotice = notices.receiveAsFlow())

        notices.trySend(IntensityToggleNotice(enabled = false, eventCount = 5))

        composeTestRule.onNodeWithText(PlainVoice.disableIntensityNotice(5)).assertExists()
    }
}
