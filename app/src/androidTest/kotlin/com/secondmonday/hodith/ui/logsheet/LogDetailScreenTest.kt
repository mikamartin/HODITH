package com.secondmonday.hodith.ui.logsheet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.LogDetailScreenUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [LogDetailScreen] is a stateless composable driven by plain data + callbacks, same pattern as
 * [com.secondmonday.hodith.ui.case.CaseEditScreen]'s test. Covers the chrome that distinguishes
 * the edit screen from the new-event [LogDetailSheet] — the back arrow and the delete action —
 * plus a tracked-duration round-trip through the shared [LogDetailForm].
 */
@UiTest
class LogDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: LogDetailScreenUiState =
            LogDetailScreenUiState(isLoading = false, tracksDuration = false, initialDraft = draft()),
        onSave: (LogDraft) -> Unit = {},
        onBack: () -> Unit = {},
        onDelete: (() -> Unit)? = {},
    ) {
        composeTestRule.setHodithContent {
            CompositionLocalProvider(LocalTimeFormat provides TimeFormat.TWELVE_HOUR) {
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
    fun endedEvent_startAgain_thenSave_savesWithNullEndedAt() {
        var savedDraft: LogDraft? = null
        setContent(
            uiState =
                LogDetailScreenUiState(
                    isLoading = false,
                    tracksDuration = true,
                    initialDraft = draft(endedAt = 5_000L),
                    now = 10_000L,
                ),
            onSave = { savedDraft = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.logSheetStartAgainAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft)
        assertNull(savedDraft?.endedAt)
    }

    @Smoke
    @Test
    fun trackedEvent_projectedPastNow_stopNow_thenSave_capsDurationAtElapsed() {
        var savedDraft: LogDraft? = null
        val now = 90 * 60_000L // 90 minutes after occurredAt
        setContent(
            uiState =
                LogDetailScreenUiState(
                    isLoading = false,
                    tracksDuration = true,
                    initialDraft =
                        LogDraft(
                            occurredAt = 0L,
                            intensity = null,
                            note = "",
                            tags = emptyList(),
                            endedAt = 5 * 60 * 60_000L, // 5 hours -- projects well past `now`
                            daysInput = "0",
                            hoursInput = "5",
                            minutesInput = "0",
                        ),
                    now = now,
                ),
            onSave = { savedDraft = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.logSheetStopNowAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft)
        assertEquals(now, savedDraft?.endedAt)
        assertEquals("0", savedDraft?.daysInput)
        assertEquals("1", savedDraft?.hoursInput)
        assertEquals("30", savedDraft?.minutesInput)
    }

    @Test
    fun tagFieldFocused_saveButtonStaysReachable() {
        var savedDraft: LogDraft? = null
        setContent(onSave = { savedDraft = it })

        composeTestRule.onNodeWithText(PlainVoice.logSheetAddTagHint).performTextInput("focus")
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft)
    }
}
