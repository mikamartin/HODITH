package com.secondmonday.hodith.ui.logsheet

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.LogDetailScreenUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Covers the intensity-required-on-new-event fix (spec §6): a *new* event on an
 * intensity-enabled Case opens pre-rated at 1 and can't be tapped back to none, while *editing*
 * an existing event never has its stored intensity (including a legitimate null from before the
 * Case tracked intensity) touched just by opening the form.
 */
@UiTest
class LogDetailIntensityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ratedDraft(intensity: Int?) =
        LogDraft(
            occurredAt = 0L,
            intensity = intensity,
            note = "",
            tags = emptyList(),
            endedAt = null,
            daysInput = "",
            hoursInput = "",
            minutesInput = "",
        )

    @Smoke
    @Test
    fun newEvent_onIntensityEnabledCase_opensPreRatedAtOne_andSaveIsImmediatelyEnabled() {
        var savedDraft: LogDraft? = null
        composeTestRule.setHodithContent {
            LogDetailSheet(
                tracksDuration = false,
                intensityEnabled = true,
                initialDraft = draft(),
                tagSuggestions = emptyList(),
                now = 0L,
                onSave = { savedDraft = it },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("1").assertIsSelected()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertEquals(1, savedDraft?.intensity)
    }

    @Test
    fun newEvent_tappingSelectedChoiceAgain_doesNotDeselect() {
        composeTestRule.setHodithContent {
            LogDetailSheet(
                tracksDuration = false,
                intensityEnabled = true,
                initialDraft = draft(),
                tagSuggestions = emptyList(),
                now = 0L,
                onSave = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("1").performClick()

        composeTestRule.onNodeWithText("1").assertIsSelected()
    }

    @Test
    fun newEvent_tappingADifferentChoice_switchesSelectionAndPersistsOnSave() {
        var savedDraft: LogDraft? = null
        composeTestRule.setHodithContent {
            LogDetailSheet(
                tracksDuration = false,
                intensityEnabled = true,
                initialDraft = draft(),
                tagSuggestions = emptyList(),
                now = 0L,
                onSave = { savedDraft = it },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("3").performClick()

        composeTestRule.onNodeWithText("3").assertIsSelected()
        composeTestRule.onNodeWithText("1").assertIsNotSelected()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()
        assertEquals(3, savedDraft?.intensity)
    }

    @Test
    fun editingEvent_withNoStoredIntensity_onIntensityEnabledCase_opensWithNothingSelected_andSavesNullUnlessRated() {
        var savedDraft: LogDraft? = null
        composeTestRule.setHodithContent {
            LogDetailScreen(
                uiState =
                    LogDetailScreenUiState(
                        isLoading = false,
                        tracksDuration = false,
                        intensityEnabled = true,
                        initialDraft = draft(),
                    ),
                onSave = { savedDraft = it },
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("1").assertIsNotSelected()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNull(savedDraft?.intensity)
    }

    @Test
    fun editingEvent_withNoStoredIntensity_canStillBeRatedRetroactively_thenCannotBeDeselected() {
        composeTestRule.setHodithContent {
            LogDetailScreen(
                uiState =
                    LogDetailScreenUiState(
                        isLoading = false,
                        tracksDuration = false,
                        intensityEnabled = true,
                        initialDraft = draft(),
                    ),
                onSave = {},
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.onNodeWithText("4").assertIsSelected()

        composeTestRule.onNodeWithText("4").performClick()

        composeTestRule.onNodeWithText("4").assertIsSelected()
    }

    @Test
    fun editingEvent_withAnExistingRating_showsItSelected_andCannotBeClearedToNone() {
        var savedDraft: LogDraft? = null
        composeTestRule.setHodithContent {
            LogDetailScreen(
                uiState =
                    LogDetailScreenUiState(
                        isLoading = false,
                        tracksDuration = false,
                        intensityEnabled = true,
                        initialDraft = ratedDraft(intensity = 2),
                    ),
                onSave = { savedDraft = it },
                onBack = {},
            )
        }

        composeTestRule.onNodeWithText("2").assertIsSelected()

        composeTestRule.onNodeWithText("2").performClick()

        composeTestRule.onNodeWithText("2").assertIsSelected()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()
        assertEquals(2, savedDraft?.intensity)
    }
}
