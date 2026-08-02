package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
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
        onOpenCase: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalVoice provides PlainVoice,
                LocalBigPictureCellStyle provides cellStyle,
            ) {
                BigPictureScreen(uiState = uiState, onOpenCase = onOpenCase)
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

    private fun eventToday(
        id: Long = 1L,
        note: String? = null,
        tags: List<String> = emptyList(),
    ) = CalendarEvent(
        id = id,
        caseId = case.id,
        occurredAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        note = note,
        tags = tags,
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

    @Test
    fun dayDetailDialog_showsEventTimestampAndTags() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine", tags = listOf("late night")))),
        )

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        // Midnight (today.atStartOfDay) formats as "12:00 AM".
        composeTestRule.onNodeWithText("12:00 AM").assertExists()
        // One "late night" node is the filter chip above the grid, the other is the tag pill on
        // this event row inside the now-open dialog — both coexist since the dialog overlays
        // rather than replacing the screen.
        composeTestRule.onAllNodesWithText("late night").assertCountEquals(2)
    }

    @Test
    fun dayDetailDialog_eventRowTap_opensCaseDetailAndDismissesDialog() {
        var openedCaseId: Long? = null
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))),
            onOpenCase = { openedCaseId = it },
        )
        val dayTitle = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US))
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText("${case.icon} ${case.name}").performClick()

        assert(openedCaseId == case.id) { "expected onOpenCase to be called with ${case.id}, was $openedCaseId" }
        composeTestRule.onNodeWithText(dayTitle).assertDoesNotExist()
    }

    @Test
    fun tagFilterChip_deselecting_hidesEventsOfOtherTags() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        val urgentEvent = eventToday(id = 1L, note = "urgent note", tags = listOf("urgent"))
        val laterEvent =
            CalendarEvent(id = 2L, caseId = secondCase.id, occurredAt = urgentEvent.occurredAt, note = "later note", tags = listOf("later"))
        setContent(uiStateWith(cases = listOf(case, secondCase), events = listOf(urgentEvent, laterEvent)))

        // Deselect while only the filter-chip row is on screen, before opening the dialog risks
        // matching the tag pill inside it too.
        composeTestRule.onNodeWithText("later").performClick()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText("urgent note").assertExists()
        composeTestRule.onNodeWithText("later note").assertDoesNotExist()
    }

    @Test
    fun tagFilterChip_deselecting_hidesUntaggedEventsToo() {
        val taggedEvent = eventToday(id = 1L, note = "tagged note", tags = listOf("urgent"))
        val untaggedEvent = eventToday(id = 2L, note = "plain note")
        setContent(uiStateWith(cases = listOf(case), events = listOf(taggedEvent, untaggedEvent)))

        composeTestRule.onNodeWithText("urgent").performClick()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText("tagged note").assertDoesNotExist()
        composeTestRule.onNodeWithText("plain note").assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureDayDetailEmptyState).assertExists()
    }

    @Test
    fun weekDetailDialog_showsEventTimestampAndTags() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine", tags = listOf("late night")))),
        )

        // Today's week is the last (bottom-most) rendered week row.
        composeTestRule.onAllNodesWithText("›").onLast().performClick()

        // Midnight (today.atStartOfDay) formats as "12:00 AM".
        composeTestRule.onNodeWithText("12:00 AM").assertExists()
        // One "late night" node is the filter chip above the grid, the other is the tag pill on
        // this event row inside the now-open dialog.
        composeTestRule.onAllNodesWithText("late night").assertCountEquals(2)
    }

    @Test
    fun weekDetailDialog_eventRowTap_opensCaseDetailAndDismissesDialog() {
        var openedCaseId: Long? = null
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))),
            onOpenCase = { openedCaseId = it },
        )
        val formattedWeekStart = weekStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US))
        composeTestRule.onAllNodesWithText("›").onLast().performClick()

        composeTestRule.onNodeWithText("${case.icon} ${case.name}").performClick()

        assert(openedCaseId == case.id) { "expected onOpenCase to be called with ${case.id}, was $openedCaseId" }
        composeTestRule.onNodeWithText(PlainVoice.bigPictureWeekDetailTitle(formattedWeekStart)).assertDoesNotExist()
    }
}
