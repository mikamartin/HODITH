package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import java.time.temporal.ChronoUnit
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
    private val earlierYearMonth = YearMonth.of(2025, 12)
    private val earlierMonthTitle = "December 2025 ›"

    // The grid's month order is current-first/earliest-last (spec §9); with the full, unfiltered
    // range the earliest month is off-screen below the fold until the LazyColumn is scrolled to
    // it -- this is its index (last, since displayMonths reverses the ascending months list).
    private val fullRangeEarliestMonthIndex = ChronoUnit.MONTHS.between(earlierYearMonth, currentMonth).toInt()

    private fun scrollToEarliestMonth() {
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(fullRangeEarliestMonthIndex)
    }

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
                // BigPictureScreen is stateless: its Case/Tag/Year filters now live in uiState
                // rather than BigPictureGrid's own remember state, so this local var stands in for
                // the ViewModel, feeding each filter callback back into the state the screen reads.
                var state by remember { mutableStateOf(uiState) }
                BigPictureScreen(
                    uiState = state,
                    onOpenCase = onOpenCase,
                    onToggleDetail = onToggleDetail,
                    onSetVisibleCaseIds = { state = state.copy(visibleCaseIds = it) },
                    onSetVisibleTagNames = { state = state.copy(visibleTagNames = it) },
                    onSelectYear = { state = state.copy(selectedYear = it) },
                )
            }
        }
    }

    private fun uiStateWith(
        cases: List<CalendarCase> = emptyList(),
        events: List<CalendarEvent> = emptyList(),
        detail: BigPictureDetail = BigPictureDetail.DEFAULT,
        earliestMonth: YearMonth = currentMonth,
        visibleCaseIds: Set<Long>? = null,
        visibleTagNames: Set<String>? = null,
        selectedYear: Int? = null,
    ) = BigPictureUiState(
        cases = cases,
        events = events,
        earliestMonth = earliestMonth,
        currentMonth = currentMonth,
        today = today,
        detail = detail,
        isLoading = false,
        visibleCaseIds = visibleCaseIds,
        visibleTagNames = visibleTagNames,
        selectedYear = selectedYear,
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

        composeTestRule.onNodeWithTag(BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG).performClick()

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

        composeTestRule.onNodeWithTag(BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG).performClick()

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
        composeTestRule.onNodeWithTag(BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG).performClick()

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

        composeTestRule.onNodeWithTag(BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG).performClick()

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

        composeTestRule.onNodeWithTag(BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG).performClick()

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

        composeTestRule.onNodeWithText(": " + PlainVoice.bigPictureFilterCountAll).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(": " + PlainVoice.bigPictureFilterCount(1)).assertExists()
    }

    @Test
    fun bulkToggle_clearAll_clearsEveryCase_thenLabelFlipsToSelectAll() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        // Starts fully selected, so the bulk toggle reads "Clear all".
        composeTestRule.onNodeWithText(PlainVoice.bigPictureClearAllAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureNoCasesSelectedNote).assertExists()
        // "None" reads better than a bare "0" for the trigger chip's count.
        composeTestRule.onNodeWithText(": " + PlainVoice.bigPictureFilterCountNone).assertExists()

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
    // CaseGroupChip's entire BRIGHT branch (via BrightChip) would go untested. TagFilterChip no
    // longer branches on decoration style at all (see its own doc comment).

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

        composeTestRule.onNodeWithText(": " + PlainVoice.bigPictureFilterCount(1)).assertExists()
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

    @Test
    fun caseChipAndTagChip_haveEqualHeight_inSharedFlowRow() {
        // Narrows both Cases and Tags to "Some" (not All, not None) so the legend row renders a
        // real CaseFilterChip next to a real TagFilterChip -- the layout the two chip kinds must
        // measure identically in.
        // Both tags stay on the still-visible Case (Coffee) — Tea is deselected purely to give the
        // Cases dimension a "Some" state, so it carries no events of its own; if it owned "later"
        // instead, deselecting Tea would scope "later" out of the Tags dialog entirely.
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        val eventA = eventToday(id = 1L, tags = listOf("urgent"))
        val eventB = CalendarEvent(id = 2L, caseId = case.id, occurredAt = eventA.occurredAt, tags = listOf("later"))
        setContent(uiStateWith(cases = listOf(case, secondCase), events = listOf(eventA, eventB)))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureTagsFilterLabel).performClick()
        composeTestRule.onNodeWithText("later").performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        val caseChipBounds = composeTestRule.onNodeWithText(case.name).getBoundsInRoot()
        val tagChipBounds = composeTestRule.onNodeWithText("urgent").getBoundsInRoot()
        val caseChipHeight = caseChipBounds.bottom - caseChipBounds.top
        val tagChipHeight = tagChipBounds.bottom - tagChipBounds.top
        assert(caseChipHeight == tagChipHeight) {
            "expected the case chip ($caseChipHeight) and tag chip ($tagChipHeight) to measure the same height"
        }
    }

    @Test
    fun casesDialog_pillsWrappedAcrossRows_dontOverlapVertically() {
        // Regression guard: the Cases/Tags/Year dialog FlowRows and the legend row's own FlowRow
        // used to have no verticalArrangement, so once pills wrapped onto a second line the rows
        // sat flush against each other with no gap. Enough cases here to force at least two rows in
        // the dialog's own width.
        val manyCases = (1..12).map { CalendarCase(id = it.toLong(), icon = "•", name = "Case $it") }
        setContent(uiStateWith(cases = manyCases))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()

        val bounds = manyCases.map { composeTestRule.onNodeWithText(it.name).getBoundsInRoot() }
        val rowTops = bounds.map { it.top }.distinct().sorted()
        assert(rowTops.size > 1) { "expected the case pills to wrap onto more than one row, got ${rowTops.size}" }
        for (i in 0 until rowTops.size - 1) {
            val rowBottom = bounds.filter { it.top == rowTops[i] }.maxOf { it.bottom }
            val nextRowTop = rowTops[i + 1]
            assert(rowBottom <= nextRowTop) {
                "row at y=${rowTops[i]} (bottom=$rowBottom) overlaps the next row starting at y=$nextRowTop"
            }
        }
    }

    // ---- Year filter (spec §9) ----

    @Test
    fun yearChip_absent_whenDataSpansOnlyOneYear() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).assertDoesNotExist()
    }

    @Test
    fun yearChip_present_defaultsToAllYears_whenDataSpansMultipleYears() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday()), earliestMonth = earlierYearMonth))

        // The Cases chip also reads ": All" (fully selected) at this point, so anchor on the Year
        // chip's own "Year" label too -- a bare ": All" match is ambiguous between the two chips.
        composeTestRule
            .onNode(hasText(PlainVoice.bigPictureYearFilterLabel) and hasText(": " + PlainVoice.bigPictureFilterCountAll))
            .assertExists()
        composeTestRule.onNodeWithText(monthTitle).assertExists()
        scrollToEarliestMonth()
        composeTestRule.onNodeWithText(earlierMonthTitle).assertExists()
    }

    @Test
    fun yearChip_pickingAYear_narrowsVisibleMonths_andAllYearsResetsTheFullRange() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday()), earliestMonth = earlierYearMonth))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(earlierYearMonth.year.toString()).performClick()

        composeTestRule.onNodeWithText(earlierMonthTitle).assertExists()
        composeTestRule.onNodeWithText(monthTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(": " + earlierYearMonth.year).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureFilterCountAll).performClick()

        composeTestRule.onNodeWithText(monthTitle).assertExists()
        scrollToEarliestMonth()
        composeTestRule.onNodeWithText(earlierMonthTitle).assertExists()
    }

    @Test
    fun monthPickerDialog_scopesToTheActiveYearFilter() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday()), earliestMonth = earlierYearMonth))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(earlierYearMonth.year.toString()).performClick()

        composeTestRule.onNodeWithText(earlierMonthTitle).performClick()

        // The month picker lists bare month labels (no trailing "›"); only the filtered year's
        // month should be offered, not July 2026, which is outside the active Year filter.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureMonthPickerTitle).assertExists()
        composeTestRule.onNodeWithText("December 2025").assertExists()
        composeTestRule.onNodeWithText("July 2026").assertDoesNotExist()
    }

    @Test
    fun yearChip_pickingASecondYear_directlyReplacesThePreviousSelection() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday()), earliestMonth = earlierYearMonth))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(earlierYearMonth.year.toString()).performClick()
        composeTestRule.onNodeWithText(earlierMonthTitle).assertExists()

        // Switch straight from one specific year to another, not routed back through "All years"
        // first -- the single-select dialog must overwrite the prior selection, not toggle it off.
        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(currentMonth.year.toString()).performClick()

        composeTestRule.onNodeWithText(monthTitle).assertExists()
        composeTestRule.onNodeWithText(earlierMonthTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(": " + currentMonth.year).assertExists()
    }

    @Test
    fun yearFilter_composesWithCaseFilter_dayDetailRespectsBothNarrowings() {
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        val earlierDay = earlierYearMonth.atDay(15)
        val coffeeEvent = CalendarEvent(id = 1L, caseId = case.id, occurredAt = millisAt(earlierDay, 9), note = "coffee note")
        val teaEvent = CalendarEvent(id = 2L, caseId = secondCase.id, occurredAt = millisAt(earlierDay, 10), note = "tea note")
        setContent(
            uiStateWith(cases = listOf(case, secondCase), events = listOf(coffeeEvent, teaEvent), earliestMonth = earlierYearMonth),
        )

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(earlierYearMonth.year.toString()).performClick()

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        composeTestRule.onNodeWithText(earlierDay.dayOfMonth.toString()).performClick()

        composeTestRule.onNodeWithText("coffee note").assertExists()
        composeTestRule.onNodeWithText("tea note").assertDoesNotExist()
    }

    @Test
    fun yearFilterTriggerAndDialogChip_workUnderBrightTheme() {
        setContent(
            uiStateWith(cases = listOf(case), events = listOf(eventToday()), earliestMonth = earlierYearMonth),
            decorationStyle = CardDecorationStyle.BRIGHT,
        )

        composeTestRule.onNodeWithText(PlainVoice.bigPictureYearFilterLabel).performClick()
        composeTestRule.onNodeWithText(earlierYearMonth.year.toString()).performClick()

        composeTestRule.onNodeWithText(earlierMonthTitle).assertExists()
        composeTestRule.onNodeWithText(monthTitle).assertDoesNotExist()
    }

    @Test
    fun currentMonth_asFirstRenderedMonth_dropsFutureWeekEntirely_notBlank() {
        // today is Jul 23 2026; July's Monday-starting weeks are Jun29, Jul6, Jul13, Jul20, Jul27.
        // The Jul27 week starts entirely after today and must be dropped outright -- exactly the
        // bug the settled prototype hit once the current month became first-in-list instead of
        // last, where a dropped-vs-blanked trailing week reads as a stray gap.
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday())))

        composeTestRule.onAllNodesWithText("›").assertCountEquals(4)
    }

    // ---- persisted filter selections (spec §9) ----

    @Test
    fun seededVisibleCaseIds_dropsAStaleIdInsteadOfCrashingOrInflatingTheCount() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday()), visibleCaseIds = setOf(case.id, 99L)))

        // 99L doesn't exist among cases, so it's dropped; the remaining real id is every current
        // case, which collapses to "All", not a bogus "2 of 1".
        composeTestRule.onNodeWithText(": " + PlainVoice.bigPictureFilterCountAll).assertExists()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureDayDetailEmptyState).assertDoesNotExist()
    }

    @Test
    fun seededVisibleCaseIds_explicitEmptySet_showsNoCasesSelected_distinctFromNull() {
        setContent(uiStateWith(cases = listOf(case), events = listOf(eventToday()), visibleCaseIds = emptySet()))

        composeTestRule.onNodeWithText(PlainVoice.bigPictureNoCasesSelectedNote).assertExists()
        composeTestRule.onNodeWithText(today.dayOfMonth.toString()).performClick()
        composeTestRule.onNodeWithText(PlainVoice.bigPictureDayDetailEmptyState).assertExists()
    }

    @Test
    fun seededVisibleTagNames_dropsAStaleNameInsteadOfInflatingTheCount() {
        setContent(
            uiStateWith(
                cases = listOf(case),
                events = listOf(eventToday(tags = listOf("urgent"))),
                visibleTagNames = setOf("urgent", "gone"),
            ),
        )

        // The legend row itself renders nothing once every dimension resolves to "All" (both Case
        // and Tag legends collapse silently), so assert on the trigger chip instead -- anchored on
        // its own label since the Cases chip also reads ": All" at this point.
        composeTestRule
            .onNode(hasText(PlainVoice.bigPictureTagsFilterLabel) and hasText(": " + PlainVoice.bigPictureFilterCountAll))
            .assertExists()
    }

    @Test
    fun togglingACaseChip_clearsAnActivePersistedTagSelection_backToAll() {
        // Coffee carries two tags ("work", "personal") so that, after Tea is deselected, a stale
        // {"work"} tag selection re-intersected against Coffee's own scoped tags would still read
        // as a partial "1 of 2" -- only an explicit reset to null collapses it to "All".
        val secondCase = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
        val workEvent = eventToday(id = 1L, tags = listOf("work"))
        val personalEvent = CalendarEvent(id = 2L, caseId = case.id, occurredAt = workEvent.occurredAt, tags = listOf("personal"))
        val soloEvent =
            CalendarEvent(id = 3L, caseId = secondCase.id, occurredAt = workEvent.occurredAt, tags = listOf("solo"))
        setContent(
            uiStateWith(
                cases = listOf(case, secondCase),
                events = listOf(workEvent, personalEvent, soloEvent),
                visibleTagNames = setOf("work"),
            ),
        )

        composeTestRule.onNodeWithText(PlainVoice.bigPictureCasesFilterLabel).performClick()
        composeTestRule.onNodeWithText(secondCase.name).performClick()
        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()

        // The Tag legend collapses to "All tags" only once the resolved selection covers every tag
        // currently in scope — the persisted {"work"} selection must have been reset to null, not
        // merely re-intersected against Coffee-only tags (which would also read {"work"} = "All").
        composeTestRule.onNodeWithText(PlainVoice.bigPictureAllTagsLabel).assertExists()
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

        composeTestRule.onNodeWithTag(BIG_PICTURE_TODAY_WEEK_CHEVRON_TAG).performClick()

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
