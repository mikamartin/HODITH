package com.secondmonday.hodith.ui.case

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.SeriousVoice
import com.secondmonday.hodith.viewmodel.CaseEditUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * [CaseEditScreen] is a stateless composable driven entirely by plain data + callbacks, same
 * pattern as `HomeScreenTest`/`CaseDetailScreenTest`. Scoped to the archive icon/dialog added by
 * `feature/case-archive` — full form coverage (name/icon/logFlow/etc.) is a separate, pre-existing
 * gap and stays out of scope here.
 */
class CaseEditScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: CaseEditUiState = CaseEditUiState(isEditing = true, isLoading = false, canArchive = true),
        onArchive: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides SeriousVoice) {
                CaseEditScreen(
                    uiState = uiState,
                    onNameChange = {},
                    onDescriptionChange = {},
                    onIconSelect = {},
                    onLogFlowChange = {},
                    onDurationModeChange = {},
                    onIntensityToggle = {},
                    onPinnedToggle = {},
                    onCheckInOptionChange = {},
                    onCheckInCustomDaysChange = {},
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

        composeTestRule.onNodeWithContentDescription(SeriousVoice.archiveCaseDescription).assertDoesNotExist()
    }

    @Test
    fun archiveIcon_shownWhenEditingExistingCase() {
        setContent()

        composeTestRule.onNodeWithContentDescription(SeriousVoice.archiveCaseDescription).assertExists()
    }

    @Test
    fun archiveIcon_opensConfirmDialog_confirmInvokesCallback() {
        var archived = false
        setContent(onArchive = { archived = true })

        composeTestRule.onNodeWithContentDescription(SeriousVoice.archiveCaseDescription).performClick()
        composeTestRule.onNodeWithText(SeriousVoice.archiveCaseConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(SeriousVoice.archiveCaseConfirmAction).performClick()

        assertEquals(true, archived)
    }

    @Test
    fun archiveIcon_cancelDoesNotInvokeCallback() {
        var archived = false
        setContent(onArchive = { archived = true })

        composeTestRule.onNodeWithContentDescription(SeriousVoice.archiveCaseDescription).performClick()
        composeTestRule.onNodeWithText(SeriousVoice.archiveCaseCancelAction).performClick()

        assertFalse(archived)
    }
}
