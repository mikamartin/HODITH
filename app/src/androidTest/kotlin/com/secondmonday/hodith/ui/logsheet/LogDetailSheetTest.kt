package com.secondmonday.hodith.ui.logsheet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * [LogDetailSheet] is the new-event counterpart to [LogDetailScreen] — same shared
 * [LogDetailForm], different container ([androidx.compose.material3.ModalBottomSheet] instead
 * of a [androidx.compose.material3.Scaffold]). Covers that the Save button stays reachable while
 * the tag field is focused here too, since the fix lives in the shared form.
 */
@UiTest
class LogDetailSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Smoke
    @Test
    fun tagFieldFocused_saveButtonStaysReachable() {
        var savedDraft: LogDraft? = null
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                LogDetailSheet(
                    durationMode = DurationMode.NONE,
                    intensityEnabled = false,
                    initialDraft = draft(),
                    tagSuggestions = emptyList(),
                    now = 0L,
                    onSave = { savedDraft = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.logSheetAddTagHint).performTextInput("focus")
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft)
    }

    @Test
    fun startStopDraftWithEndBeforeStart_showsClampedNotice() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                LogDetailSheet(
                    durationMode = DurationMode.START_STOP,
                    intensityEnabled = false,
                    initialDraft = draft(endedAt = -1L),
                    tagSuggestions = emptyList(),
                    now = 0L,
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.logSheetEndBeforeStartClampedNotice).assertExists()
    }
}
