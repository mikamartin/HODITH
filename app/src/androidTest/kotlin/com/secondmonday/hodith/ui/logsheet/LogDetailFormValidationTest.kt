package com.secondmonday.hodith.ui.logsheet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDetailScreenUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.formatEventDate
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Regression coverage for [LogDetailForm]'s start/end date guardrail: picking an invalid date no
 * longer reverts the field to its prior value — it's applied as picked, alongside a warning, with
 * Save disabled until the pair is valid again. Drives the real `DatePicker` dialogs (not the
 * clock-dial `TimePicker`, which has no text-input mode to click in this app), since that's the
 * only way to reach an actual rejection through the UI. A calendar day cell's `Text` semantics is
 * its full accessible description (e.g. "Wednesday, January 10, 2024"), not the bare digit shown
 * on screen, so day cells are matched by the "<Month> <day>, <year>" substring instead.
 */
@UiTest
class LogDetailFormValidationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val zone: ZoneId = ZoneId.systemDefault()
    private val occurredDate = LocalDate.of(2024, 1, 9)
    private val endedDate = LocalDate.of(2024, 1, 10)
    private val nowDate = LocalDate.of(2024, 1, 15)
    private val pickedDate = LocalDate.of(2024, 1, 14)
    private val dayCellFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    private fun millisAt(
        date: LocalDate,
        hour: Int,
    ): Long =
        date
            .atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun clickDayCell(date: LocalDate) {
        composeTestRule.onNodeWithText(dayCellFormatter.format(date), substring = true).performClick()
    }

    @Test
    fun pickingStartDateAfterEnd_appliesPickWithoutRevert_disablesSaveUntilFixed() {
        val occurredAt = millisAt(occurredDate, hour = 9)
        val endedAt = millisAt(endedDate, hour = 10)
        val now = millisAt(nowDate, hour = 12)
        var savedDraft: LogDraft? = null

        composeTestRule.setHodithContent {
            CompositionLocalProvider(LocalTimeFormat provides TimeFormat.TWELVE_HOUR) {
                LogDetailScreen(
                    uiState =
                        LogDetailScreenUiState(
                            isLoading = false,
                            durationMode = DurationMode.START_STOP,
                            initialDraft =
                                LogDraft(
                                    occurredAt = occurredAt,
                                    intensity = null,
                                    durationAmount = "",
                                    durationUnit = DurationUnit.MINUTES,
                                    note = "",
                                    tags = emptyList(),
                                    endedAt = endedAt,
                                    existingEndedAt = endedAt,
                                ),
                            now = now,
                        ),
                    onSave = { savedDraft = it },
                    onBack = {},
                    onDelete = {},
                )
            }
        }

        // Open the start-date picker and pick day 14 — after the current end date (day 10) but
        // before `now` (day 15), so it trips AFTER_END, not FUTURE.
        composeTestRule.onNodeWithText(formatEventDate(occurredAt, zone)).performClick()
        clickDayCell(pickedDate)
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        val pickedStart = millisAt(pickedDate, hour = 9)
        composeTestRule.onNodeWithText(formatEventDate(pickedStart, zone)).assertExists()
        composeTestRule.onNodeWithText(formatEventDate(occurredAt, zone)).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.logSheetStartAfterEndNotice).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).assertIsNotEnabled()

        // Fix it: move the end date out to day 14 too, so start <= end again.
        composeTestRule.onNodeWithText(formatEventDate(endedAt, zone)).performClick()
        clickDayCell(pickedDate)
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        composeTestRule.onNodeWithText(PlainVoice.logSheetStartAfterEndNotice).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).assertIsEnabled()

        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()
        assertNotNull(savedDraft)
    }
}
