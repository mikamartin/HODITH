package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.theme.BigPictureCellStyle
import com.secondmonday.hodith.ui.theme.LocalBigPictureCellStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.BigPictureUiState
import com.secondmonday.hodith.viewmodel.CalendarCase
import com.secondmonday.hodith.viewmodel.CalendarEvent
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * [BigPictureScreen] is a stateless composable driven entirely by plain data, so these tests
 * exercise it directly with `createComposeRule()`, same pattern as
 * `ArchivedCasesScreenTest`/`HomeScreenTest` — no Hilt/Activity/Room needed. A fixed [today]
 * (rather than `LocalDate.now()`) keeps the grid's week layout deterministic across run dates.
 */
@UiTest
class BigPictureScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 7, 23)
    private val weekStart = LocalDate.of(2026, 7, 20)
    private val currentMonth = YearMonth.from(today)
    private val case = CalendarCase(id = 1L, icon = "☕", name = "Coffee")
    private val monthTitle = "July 2026 ›"

    private fun setContent(
        uiState: BigPictureUiState,
        cellStyle: BigPictureCellStyle = BigPictureCellStyle.PLAIN,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalVoice provides PlainVoice,
                LocalBigPictureCellStyle provides cellStyle,
            ) {
                BigPictureScreen(uiState = uiState)
            }
        }
    }

    private fun uiStateWith(
        cases: List<CalendarCase> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
    ) = BigPictureUiState(
        cases = cases,
        events = events,
        earliestMonth = currentMonth,
        currentMonth = currentMonth,
        today = today,
        isLoading = false,
    )

    private fun eventToday(note: String? = null) =
        CalendarEvent(
            caseId = case.id,
            occurredAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            note = note,
        )

    @Test
    fun emptyState_showsWhenNoCases() {
        setContent(uiStateWith())

        composeTestRule.onNodeWithText(PlainVoice.noCasesEmptyState).assertExists()
    }

    @Test
    fun earlyDaysState_showsWhenCasesHaveNoEvents() {
        setContent(uiStateWith(cases = listOf(case)))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureEarlyDays).assertExists()
    }

    @Smoke
    @Test
    fun grid_showsCaseFilterChipAndMonthTitle_whenDataPresent() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(case.name).assertExists()
        composeTestRule.onNodeWithText(monthTitle).assertExists()
    }

    @Test
    fun dayDetailDialog_opensOnDayTap_showsEventNote() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))))

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText("felt fine").assertExists()
    }

    @Test
    fun monthPickerDialog_opensOnMonthTitleTap() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(monthTitle).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureMonthPickerTitle).assertExists()
    }

    @Test
    fun weekChevron_opensWeekDetailDialog_forTodaysWeek() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        // Today's week is the last (bottom-most) rendered week row.
        composeTestRule.onAllNodesWithText("›").onLast().performClick()

        val formattedWeekStart = weekStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US))
        composeTestRule.onNodeWithText(PlainVoice.bigPictureWeekDetailTitle(formattedWeekStart)).assertExists()
    }

    @Test
    fun grid_rendersAndOpensDayDetail_underIntenseCellStyle() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))),
            cellStyle = BigPictureCellStyle.INTENSE,
        )

        composeTestRule.onNodeWithText(case.name).assertExists()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText("felt fine").assertExists()
    }

    @Test
    fun grid_rendersAndOpensDayDetail_underBrightCellStyle() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))),
            cellStyle = BigPictureCellStyle.BRIGHT,
        )

        composeTestRule.onNodeWithText(case.name).assertExists()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText("felt fine").assertExists()
    }
}
