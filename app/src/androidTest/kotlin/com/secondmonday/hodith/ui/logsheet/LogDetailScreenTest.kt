package com.secondmonday.hodith.ui.logsheet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDetailScreenUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [LogDetailScreen] is a stateless composable driven by plain data + callbacks, same pattern as
 * [com.secondmonday.hodith.ui.case.CaseEditScreen]'s test. Covers the chrome that distinguishes
 * the edit screen from the new-event [LogDetailSheet] — the back arrow and the delete action —
 * plus a START_STOP round-trip through the shared [LogDetailForm].
 */
@UiTest
class LogDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun draft(endedAt: Long? = null) =
        LogDraft(
            occurredAt = 0L,
            intensity = null,
            durationAmount = "",
            durationUnit = DurationUnit.MINUTES,
            note = "",
            tags = emptyList(),
            endedAt = endedAt,
            existingEndedAt = endedAt,
        )

    private fun setContent(
        uiState: LogDetailScreenUiState =
            LogDetailScreenUiState(isLoading = false, durationMode = DurationMode.NONE, initialDraft = draft()),
        onSave: (LogDraft) -> Unit = {},
        onBack: () -> Unit = {},
        onDelete: (() -> Unit)? = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice, LocalTimeFormat provides TimeFormat.TWELVE_HOUR) {
                LogDetailScreen(uiState = uiState, onSave = onSave, onBack = onBack, onDelete = onDelete)
            }
        }
    }

    @Test
    fun backArrow_invokesOnBack() {
        var backed = false
        setContent(onBack = { backed = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.backButtonDescription).performClick()

        assertTrue(backed)
    }

    @Test
    fun deleteAction_opensConfirm_confirmInvokesOnDelete() {
        var deleted = false
        setContent(onDelete = { deleted = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.deleteEventConfirmAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.deleteEventConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.deleteEventConfirmAction).performClick()

        assertTrue(deleted)
    }

    @Test
    fun deleteAction_hiddenWhenOnDeleteIsNull() {
        setContent(onDelete = null)

        composeTestRule.onNodeWithContentDescription(PlainVoice.deleteEventConfirmAction).assertDoesNotExist()
    }

    @Smoke
    @Test
    fun startStopEvent_backToOngoing_thenSave_savesWithNullEndedAt() {
        var savedDraft: LogDraft? = null
        setContent(
            uiState =
                LogDetailScreenUiState(
                    isLoading = false,
                    durationMode = DurationMode.START_STOP,
                    initialDraft = draft(endedAt = 5_000L),
                    now = 10_000L,
                ),
            onSave = { savedDraft = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.logSheetBackToOngoingAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft)
        assertNull(savedDraft?.endedAt)
    }
}
