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
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalBigPictureCellStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
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
        decorationStyle: CardDecorationStyle = CardDecorationStyle.PLAIN,
        onOpenCase: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalVoice provides PlainVoice,
                LocalBigPictureCellStyle provides cellStyle,
                LocalCardDecorationStyle provides decorationStyle,
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
    fun grid_showsCaseFilterTriggerAndMonthTitle_whenDataPresent() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).assertExists()
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

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).assertExists()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText("felt fine").assertExists()
    }

    @Test
    fun grid_rendersAndOpensDayDetail_underBrightCellStyle() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))),
            cellStyle = BigPictureCellStyle.BRIGHT,
        )

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).assertExists()
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
        // The legend row is empty by default (both Cases and tags start fully selected), so the
        // only "late night" node is the tag pill on this event row inside the now-open dialog.
        composeTestRule.onNodeWithText("late night").assertExists()
    }

    private fun millisAt(
        date: LocalDate,
        hour: Int,
    ) = date
        .atTime(hour, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun dayDetailDialog_spannedDay_showsSpanRangeInsteadOfClockTime() {
        // A finished 3-day event: Mon 20th 09:00 -> Wed 22nd 17:00. The 21st is a carried day.
        val span =
            CalendarEvent(
                id = 1L,
                caseId = case.id,
                occurredAt = millisAt(weekStart, 9),
                endedAt = millisAt(weekStart.plusDays(2), 17),
                note = "rough stretch",
            )
        setContent(uiStateWith(cases = listOf(case), events = listOf(span)))

        composeTestRule.onNodeWithText(weekStart.plusDays(1).dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureEventSpanRange("Jul 20", "Jul 22")).assertExists()
        composeTestRule.onNodeWithText("9:00 AM").assertDoesNotExist()
    }

    @Test
    fun dayDetailDialog_carriedDayOfOngoingEvent_showsOngoingSince() {
        val ongoing =
            CalendarEvent(
                id = 1L,
                caseId = case.id,
                occurredAt = millisAt(weekStart.plusDays(1), 8),
                isOngoing = true,
                note = "forgot to stop",
            )
        setContent(uiStateWith(cases = listOf(case), events = listOf(ongoing)))

        // today is the 23rd; the 22nd is a carried day of the still-running event.
        composeTestRule.onNodeWithText(today.minusDays(1).dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureEventOngoingSince("Jul 21")).assertExists()
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

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("later").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // Confirm "later" was actually deselected before reading the grid.
        composeTestRule.onNodeWithText("urgent").assertExists()
        composeTestRule.onNodeWithText("later").assertDoesNotExist()

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText("urgent note").assertExists()
        composeTestRule.onNodeWithText("later note").assertDoesNotExist()
    }

    @Test
    fun tagFilterChip_deselectingAllTags_showsUntaggedOnly() {
        val taggedEvent = eventToday(id = 1L, note = "tagged note", tags = listOf("urgent"))
        val untaggedEvent = eventToday(id = 2L, note = "plain note")
        setContent(uiStateWith(cases = listOf(case), events = listOf(taggedEvent, untaggedEvent)))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("urgent").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // Confirm "urgent" was actually deselected (selection collapses to "Untagged only").
        composeTestRule.onNodeWithText(PlainVoice.bigPictureUntaggedOnlyLabel).assertExists()

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        // Behavior change from the old always-expanded chips: zero tags selected now means
        // "untagged only", not "hide everything".
        composeTestRule.onNodeWithText("tagged note").assertDoesNotExist()
        composeTestRule.onNodeWithText("plain note").assertExists()
    }

    @Test
    fun tagsDialog_onlyOffersTagsFromSelectedCases() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        val workEvent = eventToday(id = 1L, note = "work note", tags = listOf("work"))
        val soloEvent =
            CalendarEvent(id = 2L, caseId = secondCase.id, occurredAt = workEvent.occurredAt, note = "solo note", tags = listOf("solo"))
        setContent(uiStateWith(cases = listOf(case, secondCase), events = listOf(workEvent, soloEvent)))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // Confirm Tea was actually deselected before reopening the tags dialog.
        composeTestRule.onNodeWithText(case.name).assertExists()
        composeTestRule.onNodeWithText(secondCase.name).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("work").assertExists()
        composeTestRule.onNodeWithText("solo").assertDoesNotExist()
    }

    @Test
    fun deselectingACase_resetsStaleTagSelection_insteadOfEmptyingTheGrid() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        val workEvent = eventToday(id = 1L, note = "work note", tags = listOf("work"))
        val soloEvent =
            CalendarEvent(id = 2L, caseId = secondCase.id, occurredAt = workEvent.occurredAt, note = "solo note", tags = listOf("solo"))
        setContent(uiStateWith(cases = listOf(case, secondCase), events = listOf(workEvent, soloEvent)))

        // Narrow tags to "solo" only, while both Cases are still visible.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("work").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // Confirm the tag selection actually narrowed to "solo" before deselecting the Case.
        composeTestRule.onNodeWithText("solo").assertExists()
        composeTestRule.onNodeWithText("work").assertDoesNotExist()

        // Deselect Tea, the only Case with "solo" events — before the tag-scoping fix, the stale
        // {"solo"} tag selection would AND against Coffee's "work"-only events to an empty grid.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText("work note").assertExists()
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
        // The legend row is empty by default (both Cases and tags start fully selected), so the
        // only "late night" node is the tag pill on this event row inside the now-open dialog.
        composeTestRule.onNodeWithText("late night").assertExists()
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

    @Test
    fun filterLegend_showsNoCasesSelectedNote_afterDeselectingOnlyCase() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday(note = "felt fine"))))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(case.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureNoCasesSelectedNote).assertExists()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureDayDetailEmptyState).assertExists()
    }

    @Test
    fun filterLegend_showsUntaggedOnlyChip_afterDeselectingOnlyTag() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday(tags = listOf("urgent")))))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("urgent").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureUntaggedOnlyLabel).assertExists()
    }

    @Test
    fun filterLegend_showsAllTagsChipAndItemizedCase_whenOnlyCaseSelectionIsPartial() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        setContent(uiStateWith(cases = listOf(case, secondCase), events = listOf(eventToday(tags = listOf("urgent")))))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // Tags stay fully selected (collapses to one "All tags" chip); Cases isn't fully selected
        // anymore, so the still-selected case is itemized rather than collapsing to "All Cases".
        composeTestRule.onNodeWithText(PlainVoice.bigPictureAllTagsLabel).assertExists()
        composeTestRule.onNodeWithText(case.name).assertExists()
    }

    @Test
    fun caseFilterTrigger_countUpdates_afterDeselectingACase() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        setContent(uiStateWith(cases = listOf(case, secondCase), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureFilterCountAll).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureFilterCount(1, 2)).assertExists()
    }

    @Test
    fun bulkToggle_clearAll_clearsEveryCase_thenLabelFlipsToSelectAll() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        // Starts fully selected, so the bulk toggle reads "Clear all".
        composeTestRule.onNodeWithText(PlainVoice.bigPictureClearAllAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureNoCasesSelectedNote).assertExists()

        // Reopening confirms the label flipped now that nothing is selected.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureSelectAllAction).assertExists()
    }

    @Test
    fun bulkToggle_selectAll_reselectsEveryTag() {
        val eventA = eventToday(id = 1L, note = "a note", tags = listOf("urgent"))
        val eventB = CalendarEvent(id = 2L, caseId = case.id, occurredAt = eventA.occurredAt, note = "b note", tags = listOf("later"))
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventA, eventB)))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("urgent").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // Confirm "urgent" was actually deselected before re-selecting everything.
        composeTestRule.onNodeWithText("later").assertExists()
        composeTestRule.onNodeWithText("urgent").assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureSelectAllAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText("a note").assertExists()
        composeTestRule.onNodeWithText("b note").assertExists()
    }

    // [CardDecorationStyle] (chip skin) is a different composition local from [BigPictureCellStyle]
    // (day-cell skin) exercised above — no other test in the app provides
    // [LocalCardDecorationStyle], so without these two, FilterTriggerChip/CaseFilterChip/
    // TagFilterChip/CaseGroupChip's entire BRIGHT branch (via BrightChip) would go untested.

    @Test
    fun filterTriggerAndCaseChip_toggleWorksUnderBrightTheme() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        setContent(
            uiStateWith(cases = listOf(case, secondCase), events = listOf(eventToday())),
            decorationStyle = CardDecorationStyle.BRIGHT,
        )

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureFilterCount(1, 2)).assertExists()
        composeTestRule.onNodeWithText(case.name).assertExists()
    }

    @Test
    fun filterLegend_showsAllCasesGroupChipAndUntaggedOnlyChip_underBrightTheme() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday(tags = listOf("urgent")))),
            decorationStyle = CardDecorationStyle.BRIGHT,
        )

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("urgent").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureAllCasesLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureUntaggedOnlyLabel).assertExists()
    }
}
