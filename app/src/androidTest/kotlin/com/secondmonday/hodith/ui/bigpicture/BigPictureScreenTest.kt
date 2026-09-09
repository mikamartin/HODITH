package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.BigPictureDetail
import com.secondmonday.hodith.data.BigPictureDetailField
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
        onToggleDetail: (BigPictureDetailField, Boolean) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalVoice provides PlainVoice,
                LocalBigPictureCellStyle provides cellStyle,
                LocalCardDecorationStyle provides decorationStyle,
            ) {
                BigPictureScreen(uiState = uiState, onOpenCase = onOpenCase, onToggleDetail = onToggleDetail)
            }
        }
    }

    private fun uiStateWith(
        cases: List<CalendarCase> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        detail: BigPictureDetail = BigPictureDetail.DEFAULT,
    ) = BigPictureUiState(
        cases = cases,
        events = events,
        earliestMonth = currentMonth,
        currentMonth = currentMonth,
        today = today,
        detail = detail,
        isLoading = false,
    )

    private fun eventToday(
        id: Long = 1L,
        note: String? = null,
        tags: List<String> = emptyList(),
        intensity: Int? = null,
    ) = CalendarEvent(
        id = id,
        caseId = case.id,
        occurredAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        note = note,
        intensity = intensity,
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
        // The grid itself still renders alongside the note — it's never blocked once a Case exists.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).assertExists()
        composeTestRule.onNodeWithText(monthTitle).assertExists()
    }

    @Test
    fun earlyDaysNote_disappearsOnceAnEventExists() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureEarlyDays).assertDoesNotExist()
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
    fun dayDetailDialog_eventListIsScrollable() {
        // A plain `AlertDialog` clips overflowing content instead of scrolling it, so a day with
        // many events needs its own scrollable container -- assertExists() alone can't catch this,
        // since Compose's semantics tree doesn't care whether content is clipped from view.
        setContent(uiStateWith(cases = listOf(case), events = (1L..15L).map { eventToday(id = it) }))

        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

        // One scrollable container is the grid's own month list; a second is the day dialog's own event list.
        composeTestRule.onAllNodes(hasScrollAction()).assertCountEquals(2)
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

        // Midnight (today.atStartOfDay) formats as "12:00 AM"; it's a trailing span in the name line.
        composeTestRule.onNodeWithText("12:00 AM", substring = true).assertExists()
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

        // The carried-day row carries the real start date + time, not a bare clock time.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureEventSpanRange("Jul 20, 9:00 AM", "Jul 22, 5:00 PM")).assertExists()
        composeTestRule.onNodeWithText("9:00 AM").assertDoesNotExist()
    }

    @Test
    fun dayDetailDialog_carriedDayOfOngoingEvent_showsOngoingSinceWithDateAndTime() {
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

        composeTestRule.onNodeWithText(PlainVoice.bigPictureEventOngoingSince("Jul 21, 8:00 AM")).assertExists()
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

        composeTestRule.onNodeWithText("${case.icon} ${case.name}", substring = true).performClick()

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

        // Midnight (today.atStartOfDay) formats as "12:00 AM"; it's a trailing span in the name line.
        composeTestRule.onNodeWithText("12:00 AM", substring = true).assertExists()
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

        composeTestRule.onNodeWithText("${case.icon} ${case.name}", substring = true).performClick()

        assert(openedCaseId == case.id) { "expected onOpenCase to be called with ${case.id}, was $openedCaseId" }
        composeTestRule.onNodeWithText(PlainVoice.bigPictureWeekDetailTitle(formattedWeekStart)).assertDoesNotExist()
    }

    @Test
    fun weekDetailDialog_spannedDay_showsSpanRangeInsteadOfClockTime() {
        // A finished 3-day event: Mon 20th 09:00 -> Wed 22nd 17:00, entirely inside today's week.
        val span =
            CalendarEvent(
                id = 1L,
                caseId = case.id,
                occurredAt = millisAt(weekStart, 9),
                endedAt = millisAt(weekStart.plusDays(2), 17),
                note = "rough stretch",
            )
        setContent(uiStateWith(cases = listOf(case), events = listOf(span)))

        composeTestRule.onAllNodesWithText("›").onLast().performClick()

        // The week dialog lists the event once per covered day (20th, 21st, 22nd); every row reads
        // the span range (start + end date and time) in place of a bare clock time.
        composeTestRule
            .onAllNodesWithText(PlainVoice.bigPictureEventSpanRange("Jul 20, 9:00 AM", "Jul 22, 5:00 PM"))
            .assertCountEquals(3)
        composeTestRule.onNodeWithText("9:00 AM").assertDoesNotExist()
    }

    @Test
    fun weekDetailDialog_carriedDayOfOngoingEvent_showsOngoingSince() {
        val ongoing =
            CalendarEvent(
                id = 1L,
                caseId = case.id,
                occurredAt = millisAt(weekStart.plusDays(1), 8),
                isOngoing = true,
                note = "forgot to stop",
            )
        setContent(uiStateWith(cases = listOf(case), events = listOf(ongoing)))

        composeTestRule.onAllNodesWithText("›").onLast().performClick()

        // Covered days 21st, 22nd, 23rd (today) all fall in this week; each row reads "ongoing since".
        composeTestRule.onAllNodesWithText(PlainVoice.bigPictureEventOngoingSince("Jul 21, 8:00 AM")).assertCountEquals(3)
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

    // ---- overview-detail control (spec §9) ----

    private fun detailTag(field: BigPictureDetailField) = BIG_PICTURE_DETAIL_TOGGLE_TAG_PREFIX + field.name

    private fun openDay() = composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()

    @Test
    fun dayDetailDialog_notelessEvent_showsNoPlaceholder() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday(note = null))))

        openDay()

        // The retired bigPictureEventNoteEmptyState used to render "No note" here.
        composeTestRule.onNodeWithText("No note").assertDoesNotExist()
        composeTestRule.onNodeWithText("12:00 AM", substring = true).assertExists()
    }

    @Test
    fun detailDialog_opensFromEditIcon_showsFourTogglesAtTheDefault() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithContentDescription(PlainVoice.bigPictureDetailEditDescription).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureDetailDialogTitle).assertExists()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.NOTES)).assertIsOn()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.TAGS)).assertIsOn()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.DURATION)).assertIsOn()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.INTENSITY)).assertIsOff()
    }

    @Test
    fun detailDialog_togglingARow_reportsTheFieldAndNewValue() {
        val toggles = mutableListOf<Pair<BigPictureDetailField, Boolean>>()
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday())),
            onToggleDetail = { field, enabled -> toggles += field to enabled },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.bigPictureDetailEditDescription).performClick()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.INTENSITY)).performClick()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.NOTES)).performClick()

        assert(toggles == listOf(BigPictureDetailField.INTENSITY to true, BigPictureDetailField.NOTES to false)) {
            "expected an on and an off toggle, was $toggles"
        }
    }

    @Test
    fun editIcon_sitsRightOfTheCasesAndTagsChips() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday(tags = listOf("late night")))))

        val casesRight = composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).getBoundsInRoot().right
        val editLeft =
            composeTestRule.onNodeWithContentDescription(PlainVoice.bigPictureDetailEditDescription).getBoundsInRoot().left

        assert(editLeft > casesRight) { "expected the edit icon ($editLeft) right of the Cases chip ($casesRight)" }
    }

    @Test
    fun dayDetailRow_notesToggleOff_hidesTheNote() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(note = "started at the temples")),
                detail = BigPictureDetail.DEFAULT.copy(notes = false),
            ),
        )

        openDay()

        composeTestRule.onNodeWithText("started at the temples").assertDoesNotExist()
    }

    @Test
    fun dayDetailRow_tagsToggleOff_hidesTheTagPills() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(note = "felt fine", tags = listOf("late night"))),
                detail = BigPictureDetail.DEFAULT.copy(tags = false),
            ),
        )

        openDay()

        composeTestRule.onNodeWithText("felt fine").assertExists()
        composeTestRule.onNodeWithText("late night").assertDoesNotExist()
    }

    @Test
    fun dayDetailRow_intensityToggleOn_showsTheIntensityLabel() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(intensity = 3)),
                detail = BigPictureDetail.DEFAULT.copy(intensity = true),
            ),
        )

        openDay()

        composeTestRule.onNodeWithText(PlainVoice.eventIntensityLabel(3), substring = true).assertExists()
    }

    @Test
    fun dayDetailRow_intensityToggleOff_hidesTheIntensityLabel() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(intensity = 3)),
                detail = BigPictureDetail.DEFAULT.copy(intensity = false),
            ),
        )

        openDay()

        composeTestRule.onNodeWithText(PlainVoice.eventIntensityLabel(3), substring = true).assertDoesNotExist()
    }

    @Test
    fun dayDetailRow_durationToggleOn_showsLastedForASameDayDurationEvent() {
        val start = millisAt(today, 9)
        val end = start + 40 * 60_000L
        val sameDayDuration = CalendarEvent(id = 1L, caseId = case.id, occurredAt = start, endedAt = end)
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(sameDayDuration),
                detail = BigPictureDetail.DEFAULT.copy(duration = true),
            ),
        )

        openDay()

        composeTestRule.onNodeWithText(PlainVoice.eventDurationLabel("40m"), substring = true).assertExists()
    }

    @Test
    fun dayDetailRow_durationToggleOff_hidesLasted() {
        val start = millisAt(today, 9)
        val sameDayDuration = CalendarEvent(id = 1L, caseId = case.id, occurredAt = start, endedAt = start + 40 * 60_000L)
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(sameDayDuration),
                detail = BigPictureDetail.DEFAULT.copy(duration = false),
            ),
        )

        openDay()

        composeTestRule.onNodeWithText(PlainVoice.eventDurationLabel("40m"), substring = true).assertDoesNotExist()
    }

    @Test
    fun dayDetailRow_multiDaySpan_keepsItsRangeLabelAndAddsNoLastedLine() {
        val span =
            CalendarEvent(
                id = 1L,
                caseId = case.id,
                occurredAt = millisAt(weekStart, 9),
                endedAt = millisAt(weekStart.plusDays(2), 17),
            )
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(span), detail = BigPictureDetail.DEFAULT.copy(duration = true)),
        )

        composeTestRule.onNodeWithText(weekStart.plusDays(1).dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureEventSpanRange("Jul 20, 9:00 AM", "Jul 22, 5:00 PM")).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.eventDurationLabel(""), substring = true).assertDoesNotExist()
    }

    @Test
    fun dayDetailRow_allTogglesOff_showsOnlyNameAndTime() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(note = "felt fine", tags = listOf("late night"), intensity = 3)),
                detail = BigPictureDetail.ALL_OFF,
            ),
        )

        openDay()

        // Name + time are one wrapping line; nothing else on the row.
        composeTestRule.onNodeWithText("${case.icon} ${case.name}", substring = true).assertExists()
        composeTestRule.onNodeWithText("12:00 AM", substring = true).assertExists()
        composeTestRule.onNodeWithText("felt fine").assertDoesNotExist()
        composeTestRule.onNodeWithText("late night").assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.eventIntensityLabel(3), substring = true).assertDoesNotExist()
    }

    @Test
    fun weekDetailRow_honoursTheIntensityToggle() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(intensity = 4)),
                detail = BigPictureDetail.DEFAULT.copy(intensity = true),
            ),
        )

        composeTestRule.onAllNodesWithText("›").onLast().performClick()

        composeTestRule.onNodeWithText(PlainVoice.eventIntensityLabel(4), substring = true).assertExists()
    }

    @Test
    fun detailMetaLineAndDialog_renderUnderBrightTheme() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(intensity = 3)),
                detail = BigPictureDetail.DEFAULT.copy(intensity = true),
            ),
            cellStyle = BigPictureCellStyle.BRIGHT,
            decorationStyle = CardDecorationStyle.BRIGHT,
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.bigPictureDetailEditDescription).performClick()
        composeTestRule.onNodeWithTag(detailTag(BigPictureDetailField.INTENSITY)).assertIsOn()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        openDay()
        composeTestRule.onNodeWithText(PlainVoice.eventIntensityLabel(3), substring = true).assertExists()
    }
}
