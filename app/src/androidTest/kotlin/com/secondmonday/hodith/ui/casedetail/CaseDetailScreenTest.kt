package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.HistorySortOrder
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.DATE_RANGE_ALL_TIME_BUTTON_TAG
import com.secondmonday.hodith.ui.common.overlapsRect
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.formatDateRangeBound
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.startOfDayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Second Compose UI instrumented test in the repo, closing the gap `TESTING.md` had twice
 * deferred for `LogDetailSheet`/`CaseDetailScreen` — this branch's "start/stop flow" is the
 * scenario its planned coverage table named for it. Follows
 * [com.secondmonday.hodith.ui.home.HomeScreenTest]'s pattern: drives the stateless
 * [CaseDetailScreen] directly with fake callbacks, no Hilt/Room.
 */
@UiTest
class CaseDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val startStopCase =
        testCase(
            id = 1L,
            name = "Focus session",
            icon = "⏱️",
            logFlow = LogFlow.DETAIL_SHEET,
            durationMode = DurationMode.START_STOP,
        )

    private fun ongoingEvent() = testEvent(id = 5L, caseId = 1L)

    private fun setCaseDetailScreenContent(
        case: CaseEntity = startStopCase,
        events: List<EventWithTags> = emptyList(),
        historyEvents: List<EventWithTags> = events,
        historyHasMore: Boolean = false,
        historySortOrder: HistorySortOrder = HistorySortOrder.BY_START,
        historyDateFrom: Long? = null,
        historyDateTo: Long? = null,
        historyVisibleFields: Set<HistoryRowField> = HistoryRowField.entries.toSet(),
        onEditCase: (Long) -> Unit = {},
        onOpenShare: (Long) -> Unit = {},
        onOpenTrends: (Long) -> Unit = {},
        onEditEvent: (caseId: Long, eventId: Long) -> Unit = { _, _ -> },
        onSaveEvent: (LogDraft) -> Unit = {},
        onStopEvent: (EventEntity) -> Unit = {},
        nowMillis: () -> Long = { 10_000L },
        timeFormat: TimeFormat = TimeFormat.TWELVE_HOUR,
        theme: AppTheme = AppTheme.PLAIN,
        onHistorySortOrderChange: (HistorySortOrder) -> Unit = {},
        onHistoryDateFromChange: (LocalDate?) -> Unit = {},
        onHistoryDateToChange: (LocalDate?) -> Unit = {},
        onHistoryFieldVisibleChange: (HistoryRowField, Boolean) -> Unit = { _, _ -> },
        onShowMoreHistoryEvents: () -> Unit = {},
    ) {
        composeTestRule.setHodithContent(theme = theme) {
            CompositionLocalProvider(LocalTimeFormat provides timeFormat) {
                CaseDetailScreen(
                    uiState =
                        CaseDetailUiState(
                            case = case,
                            events = events,
                            historyEvents = historyEvents,
                            historyHasMore = historyHasMore,
                            historySortOrder = historySortOrder,
                            historyDateFrom = historyDateFrom,
                            historyDateTo = historyDateTo,
                            historyVisibleFields = historyVisibleFields,
                            isLoading = false,
                        ),
                    onBack = {},
                    onEditCase = onEditCase,
                    onEditEvent = onEditEvent,
                    onOpenShare = onOpenShare,
                    onOpenTrends = onOpenTrends,
                    onOpenTags = {},
                    newEventDraft = {
                        LogDraft(
                            occurredAt = nowMillis(),
                            intensity = null,
                            durationAmount = "",
                            durationUnit = DurationUnit.MINUTES,
                            note = "",
                            tags = emptyList(),
                            endedAt = null,
                            existingEndedAt = null,
                        )
                    },
                    onSaveEvent = onSaveEvent,
                    onStopEvent = onStopEvent,
                    nowMillis = nowMillis,
                    onHistorySortOrderChange = onHistorySortOrderChange,
                    onHistoryDateFromChange = onHistoryDateFromChange,
                    onHistoryDateToChange = onHistoryDateToChange,
                    onHistoryFieldVisibleChange = onHistoryFieldVisibleChange,
                    onShowMoreHistoryEvents = onShowMoreHistoryEvents,
                )
            }
        }
    }

    @Test
    fun description_showsText_whenCaseHasOne() {
        val describedCase = startStopCase.copy(description = "From first twinge to when it fully lifts")
        setCaseDetailScreenContent(case = describedCase)

        composeTestRule.onNodeWithText("From first twinge to when it fully lifts").assertExists()
    }

    @Test
    fun description_showsNothing_whenCaseHasNone() {
        setCaseDetailScreenContent(case = startStopCase.copy(description = null))

        composeTestRule.onNodeWithText("From first twinge to when it fully lifts").assertDoesNotExist()
    }

    @Test
    fun headerActions_editIcon_invokesCallbackWithCaseId() {
        var editedCaseId: Long? = null
        setCaseDetailScreenContent(onEditCase = { editedCaseId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseDetailEditDescription).performClick()

        assertEquals(startStopCase.id, editedCaseId)
    }

    @Test
    fun notificationsTab_iconOnly_hasVoiceContentDescription() {
        setCaseDetailScreenContent()

        composeTestRule.onNodeWithContentDescription(PlainVoice.watchesTabDescription).assertExists()
    }

    @Test
    fun shareIcon_opensTheShareScreenForThisCase() {
        var shareCaseId: Long? = null
        setCaseDetailScreenContent(onOpenShare = { shareCaseId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.shareOpenDescription).performClick()

        assertEquals(startStopCase.id, shareCaseId)
    }

    @Smoke
    @Test
    fun retroLogFab_forStartStopCaseWithNoOngoingEvent_showsOngoingByDefaultAndStartsOnSave() {
        var savedDraft: LogDraft? = null
        setCaseDetailScreenContent(onSaveEvent = { draft -> savedDraft = draft })

        composeTestRule.onNodeWithContentDescription(PlainVoice.retroLogEntryDescription, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetStartButton).performClick()

        assertNotNull(savedDraft)
        assertNull(savedDraft?.endedAt)
    }

    @Test
    fun stopNowInSheet_thenSave_savesWithAnEndedAt() {
        var savedDraft: LogDraft? = null
        setCaseDetailScreenContent(onSaveEvent = { draft -> savedDraft = draft })

        composeTestRule.onNodeWithContentDescription(PlainVoice.retroLogEntryDescription, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetStopNowAction).performClick()

        // Confirm "Stop Now" actually cleared the ongoing state before saving.
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft?.endedAt)
    }

    @Test
    fun eventRow_rendersInTwentyFourHourTime_whenLocalTimeFormatIsTwentyFourHour() {
        // 15:30 UTC — but the row formats in the device zone, so assert on what the shared
        // formatter produces for that zone rather than a fixed "15:30".
        val event = testEvent(id = 9L, caseId = 1L, occurredAt = 15L * 60 * 60_000L)
        setCaseDetailScreenContent(
            case = testCase(id = 1L, name = "Focus", durationMode = DurationMode.NONE),
            events = listOf(EventWithTags(event = event, tags = emptyList())),
            nowMillis = { event.occurredAt },
            timeFormat = TimeFormat.TWENTY_FOUR_HOUR,
        )

        val expected = formatEventTime(event.occurredAt, event.occurredAt, use24Hour = true)
        composeTestRule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun eventRow_click_invokesOnEditEventForThatEvent() {
        val event = testEvent(id = 7L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        var edited: Pair<Long, Long>? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = event, tags = emptyList())),
            onEditEvent = { caseId, eventId -> edited = caseId to eventId },
            nowMillis = { 10_000L },
        )

        composeTestRule.onNodeWithText(formatEventTime(event.occurredAt, 10_000L, use24Hour = false)).performClick()

        assertEquals(startStopCase.id to event.id, edited)
    }

    @Test
    fun eventRow_click_invokesOnEditEvent_underBrightTheme() {
        // EventRow renders Card-wrapped under Bright, with its click target placed differently
        // than Plain's flat row -- a real risk if that branch silently breaks (spec survey).
        val event = testEvent(id = 7L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        var edited: Pair<Long, Long>? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = event, tags = emptyList())),
            onEditEvent = { caseId, eventId -> edited = caseId to eventId },
            nowMillis = { 10_000L },
            theme = AppTheme.BRIGHT,
        )

        composeTestRule.onNodeWithText(formatEventTime(event.occurredAt, 10_000L, use24Hour = false)).performClick()

        assertEquals(startStopCase.id to event.id, edited)
    }

    @Test
    fun ongoingEvent_showsStopButtonOnItsRow_andInvokesOnStopEvent() {
        val ongoing = ongoingEvent()
        var stopped: EventEntity? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = ongoing, tags = emptyList())),
            onStopEvent = { stopped = it },
        )

        // Stop lives on the open event's own log row now (spec §6), not in the header.
        composeTestRule.onNodeWithContentDescription(PlainVoice.stopActionDescription(startStopCase.name)).performClick()

        assertEquals(ongoing, stopped)
    }

    @Test
    fun openEvent_showsOngoingPillInTheHeaderAndOnItsRow() {
        setCaseDetailScreenContent(events = listOf(EventWithTags(event = ongoingEvent(), tags = emptyList())))

        // One "Ongoing" pill in the header summary, one on the open event's row.
        composeTestRule.onAllNodesWithText(PlainVoice.ongoingPillLabel).assertCountEquals(2)
        composeTestRule.onNodeWithContentDescription(PlainVoice.stopActionDescription(startStopCase.name)).assertExists()
    }

    @Test
    fun finishedEventRow_showsDurationLabel_whenTheCaseTracksDuration() {
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 45 * 60_000L)
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.MANUAL),
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.eventDurationLabel("45m"), substring = true).assertExists()
    }

    @Test
    fun finishedEventRow_hidesDurationLabel_whenTheCaseNoLongerTracksDuration() {
        // Same stored endedAt, but durationMode is NONE now: the row is a point (spec §9).
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 45 * 60_000L)
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.NONE),
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.eventDurationLabel("45m"), substring = true).assertDoesNotExist()
    }

    @Test
    fun historySortToggle_hidden_whenTheCaseDoesNotTrackDuration() {
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.NONE),
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.historySortLabel).assertDoesNotExist()
    }

    @Test
    fun historySortToggle_hidden_whenTheHistoryIsEmpty() {
        setCaseDetailScreenContent(case = startStopCase, events = emptyList())

        composeTestRule.onNodeWithText(PlainVoice.historySortLabel).assertDoesNotExist()
    }

    @Test
    fun historySortToggle_shown_whenTheCaseTracksDurationAndHasEvents() {
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.MANUAL),
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
        )

        // The chip's own count text already reads the current selection ("Started" by default,
        // rendered as ": Started" -- hence substring lookups here); the other option only surfaces
        // once the chip opens its dialog.
        composeTestRule.onNodeWithText(PlainVoice.historySortLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.historySortByStartLabel, substring = true).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.historySortByEndLabel, substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.historySortLabel).performClick()

        composeTestRule.onAllNodesWithText(PlainVoice.historySortByStartLabel, substring = true).assertCountEquals(2)
        composeTestRule.onNodeWithText(PlainVoice.historySortByEndLabel, substring = true).assertExists()
    }

    @Test
    fun historySortToggle_tapEnded_invokesSortOrderChangeCallback() {
        // CaseDetailScreen is stateless now — it renders uiState.historyEvents exactly as given and
        // just forwards the tap. The actual BY_END reordering (running event floats first, then by
        // endedAt) is proven in EventDaoTest against the real paged query, not re-proven here.
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        var changedTo: HistorySortOrder? = null
        setCaseDetailScreenContent(
            case = startStopCase,
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
            onHistorySortOrderChange = { changedTo = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.historySortLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.historySortByEndLabel).performClick()

        assertEquals(HistorySortOrder.BY_END, changedTo)
    }

    @Test
    fun historySortToggle_worksUnderBrightTheme() {
        // The Sort trigger is a FilterTriggerChip; its dialog's Started/Ended choice is a
        // SegmentedChoiceRow -- the two structurally-branching composables this screen owns,
        // both exercised here under real Bright rendering rather than Plain's default code path.
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        var changedTo: HistorySortOrder? = null
        setCaseDetailScreenContent(
            case = startStopCase,
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
            onHistorySortOrderChange = { changedTo = it },
            theme = AppTheme.BRIGHT,
        )

        composeTestRule.onNodeWithText(PlainVoice.historySortLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.historySortByEndLabel).performClick()

        assertEquals(HistorySortOrder.BY_END, changedTo)
    }

    @Test
    fun historyRangeChip_defaultsToAllTime() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeAllTimeLabel, substring = true).assertExists()
    }

    @Test
    fun historyRangeChip_collapsesToSelected_withFormattedBoundsInANoteBelow_whenRangeIsNarrowed() {
        // The chip itself stays terse ("Selected") -- a formatted date pair didn't fit the chip's
        // own width alongside the Sort chip and the pinned Edit icon. The actual bounds render as
        // a separate line underneath instead.
        val zone = ZoneId.systemDefault()
        val from = zone.startOfDayMillis(LocalDate.of(2026, 7, 3))
        val to = zone.startOfDayMillis(LocalDate.of(2026, 8, 15))
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyDateFrom = from,
            historyDateTo = to,
        )

        val now = 10_000L
        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeSelectedLabel, substring = true).assertExists()
        composeTestRule
            .onNodeWithText(
                PlainVoice.shareHistoryRangeNote(formatDateRangeBound(from, now, zone), formatDateRangeBound(to, now, zone)),
            ).assertExists()
    }

    @Test
    fun historyRangeChip_tap_opensOneCombinedRangeDialog() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeLabel).performClick()

        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerCancel).assertExists()
    }

    @Test
    fun historyRangeDialog_confirmWithoutChanges_roundTripsTheExistingRangeToBothCallbacks() {
        // Regression guard for the dialog's own state seeding: opening it should pre-select the
        // current dateFrom/dateTo (via toDatePickerUtcMillis), and Confirm should hand both back
        // out through their own callback -- proving the combined onConfirm(from, to) wiring, not
        // just that the dialog opens.
        val zone = ZoneId.systemDefault()
        val from = zone.startOfDayMillis(LocalDate.of(2026, 7, 3))
        val to = zone.startOfDayMillis(LocalDate.of(2026, 8, 15))
        var changedFrom: LocalDate? = null
        var changedTo: LocalDate? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyDateFrom = from,
            historyDateTo = to,
            onHistoryDateFromChange = { changedFrom = it },
            onHistoryDateToChange = { changedTo = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        assertEquals(LocalDate.of(2026, 7, 3), changedFrom)
        assertEquals(LocalDate.of(2026, 8, 15), changedTo)
    }

    @Test
    fun historyRangeDialog_allTimeTap_clearsBothBoundsInOneTapAndClosesTheDialog() {
        val zone = ZoneId.systemDefault()
        val from = zone.startOfDayMillis(LocalDate.of(2026, 7, 3))
        val to = zone.startOfDayMillis(LocalDate.of(2026, 8, 15))
        var changedFrom: LocalDate? = LocalDate.of(2026, 7, 3)
        var changedTo: LocalDate? = LocalDate.of(2026, 8, 15)
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyDateFrom = from,
            historyDateTo = to,
            onHistoryDateFromChange = { changedFrom = it },
            onHistoryDateToChange = { changedTo = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeLabel).performClick()
        composeTestRule.onNodeWithTag(DATE_RANGE_ALL_TIME_BUTTON_TAG).performClick()

        assertEquals(null, changedFrom)
        assertEquals(null, changedTo)
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).assertDoesNotExist()
    }

    @Test
    fun historyRangeDialog_allTimeButton_hiddenWhenNothingIsFilteredYet() {
        // The chip itself already reads "All time" when unfiltered -- the dialog's own shortcut
        // has nothing to do in that state, so it should stay off rather than double up on it.
        // By tag, not by label text: the chip's own collapsed label is that same "All time" text.
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryRangeLabel).performClick()

        composeTestRule.onNodeWithTag(DATE_RANGE_ALL_TIME_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun historyFieldsEditIcon_tap_opensFieldsDialog_andTogglingNotesInvokesCallback() {
        var toggled: Pair<HistoryRowField, Boolean>? = null
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.NONE, intensityEnabled = false),
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            onHistoryFieldVisibleChange = { field, visible -> toggled = field to visible },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.historyFieldsEditDescription).performClick()

        // Duration/Intensity only offered when the Case tracks them (spec §6), same gating as History Share's own field picker.
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
        // By tag, not by label text: the label/Switch semantics don't reliably merge into one
        // clickable node in every context (see HISTORY_FIELD_TOGGLE_TAG_PREFIX's own doc comment).
        composeTestRule.onNodeWithTag(HISTORY_FIELD_TOGGLE_TAG_PREFIX + HistoryRowField.NOTES.name).performClick()

        assertEquals(HistoryRowField.NOTES to false, toggled)
    }

    @Test
    fun eventRow_hidesNote_whenNotesFieldIsToggledOff() {
        val event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, note = "a private note")
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = event, tags = emptyList())),
            historyVisibleFields = HistoryRowField.entries.toSet() - HistoryRowField.NOTES,
        )

        composeTestRule.onNodeWithText("a private note", substring = true).assertDoesNotExist()
    }

    @Test
    fun historyTab_showsEmptyRangeMessage_whenTheFilteredHistoryIsEmptyButEventHistoryIsNot() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyEvents = emptyList(),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryEmptyRangeMessage).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.eventListEmptyState).assertDoesNotExist()
    }

    @Test
    fun historyTab_rendersOnlyHistoryEvents_notTheFullEventHistory() {
        // Regression guard for PROGRESS.md F4: the History tab's row list must come from the capped,
        // paged uiState.historyEvents, not the full uiState.events used by ongoing-event detection and
        // the Insights tab.
        val shown = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, note = "shown row")
        val hidden = testEvent(id = 9L, caseId = 1L, occurredAt = 1_000L, note = "hidden row")
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = shown, tags = emptyList()), EventWithTags(event = hidden, tags = emptyList())),
            historyEvents = listOf(EventWithTags(event = shown, tags = emptyList())),
        )

        composeTestRule.onNodeWithText("shown row", substring = true).assertExists()
        composeTestRule.onNodeWithText("hidden row", substring = true).assertDoesNotExist()
    }

    @Test
    fun historyShowMoreButton_hidden_whenHistoryHasNoMoreEvents() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyHasMore = false,
        )

        composeTestRule.onNodeWithText(PlainVoice.historyShowMoreAction).assertDoesNotExist()
    }

    @Test
    fun historyShowMoreButton_shown_whenHistoryHasMoreEvents() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyHasMore = true,
        )

        composeTestRule.onNodeWithText(PlainVoice.historyShowMoreAction).assertExists()
    }

    @Test
    fun historyShowMoreButton_tap_invokesOnShowMoreHistoryEvents() {
        var tapped = false
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            historyHasMore = true,
            onShowMoreHistoryEvents = { tapped = true },
        )

        composeTestRule.onNodeWithText(PlainVoice.historyShowMoreAction).performClick()

        assertTrue(tapped)
    }

    @Test
    fun historyTab_fullScreenList_lastRowsStopButton_doesNotOverlapRetroLogFab() {
        val events = eventsAt(30)
        setCaseDetailScreenContent(events = events)
        // The LazyColumn only composes visible rows, so scroll its container to the last index
        // first. Every ongoing row shares the same Stop content description, so once scrolled,
        // pick the bottom-most match rather than assuming a fixed index among composed nodes.
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(events.lastIndex)

        val fabBounds =
            composeTestRule
                .onNodeWithContentDescription(PlainVoice.retroLogEntryDescription, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val lastStopButtonBounds =
            composeTestRule
                .onAllNodesWithContentDescription(PlainVoice.stopActionDescription(startStopCase.name))
                .fetchSemanticsNodes()
                .maxBy { it.boundsInRoot.top }
                .boundsInRoot

        assertFalse(fabBounds.overlapsRect(lastStopButtonBounds))
    }

    @Test
    fun multipleOngoingEvents_headerShowsCount_andHasNoHeaderStop() {
        setCaseDetailScreenContent(
            events =
                listOf(
                    EventWithTags(event = testEvent(id = 5L, caseId = 1L, occurredAt = 0L), tags = emptyList()),
                    EventWithTags(event = testEvent(id = 6L, caseId = 1L, occurredAt = 1_000L), tags = emptyList()),
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.ongoingCountIndicator(2), substring = true).assertExists()
        // Per-event Stop moves onto the rows; the header no longer carries one.
        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.stopActionDescription(startStopCase.name))
            .assertCountEquals(2)
    }

    @Test
    fun multipleOngoingEvents_rowStopButton_stopsThatEvent() {
        // Rows are newest-start first, so `first` gets the later `occurredAt`.
        val first = testEvent(id = 5L, caseId = 1L, occurredAt = 2_000L)
        val second = testEvent(id = 6L, caseId = 1L, occurredAt = 1_000L)
        var stopped: EventEntity? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(first, emptyList()), EventWithTags(second, emptyList())),
            onStopEvent = { stopped = it },
        )

        // The first Stop button belongs to the top row, `first`.
        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.stopActionDescription(startStopCase.name))
            .onFirst()
            .performClick()

        assertEquals(first, stopped)
    }

    private fun eventsAt(
        count: Int,
        occurredAt: Long = 0L,
    ): List<EventWithTags> =
        List(count) {
            EventWithTags(
                testEvent(id = it.toLong(), caseId = 1L, occurredAt = occurredAt),
                emptyList(),
            )
        }
}
