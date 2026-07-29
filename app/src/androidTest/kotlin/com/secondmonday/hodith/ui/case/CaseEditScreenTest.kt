package com.secondmonday.hodith.ui.case

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseEditUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        onArchive: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                CaseEditScreen(
                    uiState = uiState,
                    onNameChange = {},
                    onDescriptionChange = {},
                    onIconSelect = {},
                    onLogFlowChange = {},
                    onDurationModeChange = {},
                    onIntensityToggle = {},
                    onPinnedToggle = {},
                    onCheckInToggle = {},
                    onSave = {},
                    onArchive = onArchive,
                    onBack = {},
                )
            }
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

    @Smoke
    @Test
    fun logFlow_oneTapEnabled_whenNoDurationAndNoIntensity() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, durationMode = DurationMode.NONE))

        composeTestRule.onNodeWithText(PlainVoice.caseLogFlowOneTap).assertIsEnabled()
    }

    @Test
    fun logFlow_oneTapDisabled_whenDurationModeIsManual() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, durationMode = DurationMode.MANUAL))

        composeTestRule.onNodeWithText(PlainVoice.caseLogFlowOneTap).assertIsNotEnabled()
    }

    @Test
    fun logFlow_oneTapDisabled_whenIntensityEnabled() {
        setContent(uiState = CaseEditUiState(isEditing = true, isLoading = false, intensityEnabled = true))

        composeTestRule.onNodeWithText(PlainVoice.caseLogFlowOneTap).assertIsNotEnabled()
    }

    @Test
    fun infoIcon_opensAndDismissesDialog() {
        setContent()

        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)
            .onFirst()
            .performClick()
        composeTestRule.onNodeWithText(PlainVoice.caseLogFlowInfoTitle).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.caseLogFlowInfoTitle).assertDoesNotExist()
    }
}
