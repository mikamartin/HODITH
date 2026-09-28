package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.LogSortOrder
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.overlapsRect
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.formatSpanDate
import com.secondmonday.hodith.viewmodel.startOfDayMillis
import com.secondmonday.hodith.viewmodel.toLocalDateIn
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
        logEvents: List<EventWithTags> = events,
        logHasMore: Boolean = false,
        logSortOrder: LogSortOrder = LogSortOrder.BY_START,
        logDateFrom: Long? = null,
        logDateTo: Long? = null,
        logVisibleFields: Set<LogRowField> = LogRowField.entries.toSet(),
        activeHunch: HunchEntity? = null,
        hunchHistory: List<HunchEntity> = emptyList(),
        onEditCase: (Long) -> Unit = {},
        onOpenTriggers: (Long) -> Unit = {},
        onOpenShare: (Long) -> Unit = {},
        onOpenLogShare: (Long) -> Unit = {},
        onOpenTrends: (Long) -> Unit = {},
        onEditEvent: (caseId: Long, eventId: Long) -> Unit = { _, _ -> },
        onSaveEvent: (LogDraft) -> Unit = {},
        onStopEvent: (EventEntity) -> Unit = {},
        nowMillis: () -> Long = { 10_000L },
        timeFormat: TimeFormat = TimeFormat.TWELVE_HOUR,
        onAddHunch: (HunchDirection, Int, ExpectedPer, VerdictMetric, ObservationWindow, Long?) -> Unit =
            { _, _, _, _, _, _ -> },
        onResolveHunch: (HunchEntity) -> Unit = {},
        onLogSortOrderChange: (LogSortOrder) -> Unit = {},
        onLogDateFromChange: (LocalDate?) -> Unit = {},
        onLogDateToChange: (LocalDate?) -> Unit = {},
        onLogFieldVisibleChange: (LogRowField, Boolean) -> Unit = { _, _ -> },
        onShowMoreLogEvents: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice, LocalTimeFormat provides timeFormat) {
                CaseDetailScreen(
                    uiState =
                        CaseDetailUiState(
                            case = case,
                            events = events,
                            logEvents = logEvents,
                            logHasMore = logHasMore,
                            logSortOrder = logSortOrder,
                            logDateFrom = logDateFrom,
                            logDateTo = logDateTo,
                            logVisibleFields = logVisibleFields,
                            activeHunch = activeHunch,
                            hunchHistory = hunchHistory,
                            isLoading = false,
                        ),
                    onBack = {},
                    onEditCase = onEditCase,
                    onEditEvent = onEditEvent,
                    onOpenTriggers = onOpenTriggers,
                    onOpenShare = onOpenShare,
                    onOpenLogShare = onOpenLogShare,
                    onOpenTrends = onOpenTrends,
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
                    onAddHunch = onAddHunch,
                    onResolveHunch = onResolveHunch,
                    onLogSortOrderChange = onLogSortOrderChange,
                    onLogDateFromChange = onLogDateFromChange,
                    onLogDateToChange = onLogDateToChange,
                    onLogFieldVisibleChange = onLogFieldVisibleChange,
                    onShowMoreLogEvents = onShowMoreLogEvents,
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
    fun headerActions_editAndTriggersIcons_invokeCallbacksWithCaseId() {
        var editedCaseId: Long? = null
        var triggersCaseId: Long? = null
        setCaseDetailScreenContent(
            onEditCase = { editedCaseId = it },
            onOpenTriggers = { triggersCaseId = it },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.triggersOpenDescription).performClick()
        composeTestRule.onNodeWithContentDescription(PlainVoice.caseDetailEditDescription).performClick()

        assertEquals(startStopCase.id, triggersCaseId)
        assertEquals(startStopCase.id, editedCaseId)
    }

    @Test
    fun shareIcon_opensChooserDialog_ratherThanNavigatingDirectly() {
        var shareCaseId: Long? = null
        setCaseDetailScreenContent(onOpenShare = { shareCaseId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.shareOpenDescription).performClick()

        composeTestRule.onNodeWithText(PlainVoice.shareChooserInsightOption).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareChooserLogOption).assertExists()
        assertNull(shareCaseId)
    }

    @Test
    fun shareChooser_confirmingTheDefaultInsightOption_invokesOnOpenShare() {
        var shareCaseId: Long? = null
        var logShareCaseId: Long? = null
        setCaseDetailScreenContent(
            onOpenShare = { shareCaseId = it },
            onOpenLogShare = { logShareCaseId = it },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.shareOpenDescription).performClick()
        // The dialog's title and its confirm button share Insight Share's own "Share" label (same
        // reuse SharePreviewScreen's title/button already do) -- .onLast() is the confirm button.
        composeTestRule.onAllNodesWithText(PlainVoice.shareOpenDescription).onLast().performClick()

        assertEquals(startStopCase.id, shareCaseId)
        assertNull(logShareCaseId)
    }

    @Test
    fun shareChooser_selectingLogShareThenConfirming_invokesOnOpenLogShare() {
        var shareCaseId: Long? = null
        var logShareCaseId: Long? = null
        setCaseDetailScreenContent(
            onOpenShare = { shareCaseId = it },
            onOpenLogShare = { logShareCaseId = it },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.shareOpenDescription).performClick()
        composeTestRule.onNodeWithText(PlainVoice.shareChooserLogOption).performClick()
        composeTestRule.onAllNodesWithText(PlainVoice.shareOpenDescription).onLast().performClick()

        assertEquals(startStopCase.id, logShareCaseId)
        assertNull(shareCaseId)
    }

    @Test
    fun shareChooser_cancel_invokesNeitherCallback() {
        var shareCaseId: Long? = null
        var logShareCaseId: Long? = null
        setCaseDetailScreenContent(
            onOpenShare = { shareCaseId = it },
            onOpenLogShare = { logShareCaseId = it },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.shareOpenDescription).performClick()
        composeTestRule.onNodeWithText(PlainVoice.shareChooserCancelAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.shareChooserInsightOption).assertDoesNotExist()
        assertNull(shareCaseId)
        assertNull(logShareCaseId)
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
    fun logSortToggle_hidden_whenTheCaseDoesNotTrackDuration() {
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.NONE),
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.logSortLabel).assertDoesNotExist()
    }

    @Test
    fun logSortToggle_hidden_whenTheLogIsEmpty() {
        setCaseDetailScreenContent(case = startStopCase, events = emptyList())

        composeTestRule.onNodeWithText(PlainVoice.logSortLabel).assertDoesNotExist()
    }

    @Test
    fun logSortToggle_shown_whenTheCaseTracksDurationAndHasEvents() {
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.MANUAL),
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
        )

        // The chip's own count text already reads the current selection ("Started" by default,
        // rendered as ": Started" -- hence substring lookups here); the other option only surfaces
        // once the chip opens its dialog.
        composeTestRule.onNodeWithText(PlainVoice.logSortLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSortByStartLabel, substring = true).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSortByEndLabel, substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.logSortLabel).performClick()

        composeTestRule.onAllNodesWithText(PlainVoice.logSortByStartLabel, substring = true).assertCountEquals(2)
        composeTestRule.onNodeWithText(PlainVoice.logSortByEndLabel, substring = true).assertExists()
    }

    @Test
    fun logSortToggle_tapEnded_invokesSortOrderChangeCallback() {
        // CaseDetailScreen is stateless now — it renders uiState.logEvents exactly as given and
        // just forwards the tap. The actual BY_END reordering (running event floats first, then by
        // endedAt) is proven in EventDaoTest against the real paged query, not re-proven here.
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        var changedTo: LogSortOrder? = null
        setCaseDetailScreenContent(
            case = startStopCase,
            events = listOf(EventWithTags(event = finished, tags = emptyList())),
            onLogSortOrderChange = { changedTo = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.logSortLabel).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSortByEndLabel).performClick()

        assertEquals(LogSortOrder.BY_END, changedTo)
    }

    @Test
    fun logDateChips_defaultToAllTime() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareLogDateFromLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareLogDateToLabel).assertExists()
        composeTestRule.onAllNodesWithText(PlainVoice.shareLogRangeAllTimeLabel, substring = true).assertCountEquals(2)
    }

    @Test
    fun logDateChips_showFormattedBounds_whenRangeIsNarrowed() {
        val zone = ZoneId.systemDefault()
        val from = zone.startOfDayMillis(LocalDate.of(2026, 7, 3))
        val to = zone.startOfDayMillis(LocalDate.of(2026, 8, 15))
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            logDateFrom = from,
            logDateTo = to,
        )

        composeTestRule.onNodeWithText(formatSpanDate(from.toLocalDateIn(zone)), substring = true).assertExists()
        composeTestRule.onNodeWithText(formatSpanDate(to.toLocalDateIn(zone)), substring = true).assertExists()
    }

    @Test
    fun logDateFromChip_tap_opensItsOwnPickerDirectly_notANestedDialog() {
        // Regression guard: From/To each open the real DatePicker directly -- a combined "Range"
        // chip that opened an InfoDialog which then opened a second, much bigger picker dialog on
        // top of it is what broke (the InfoDialog's own two-button row overflowing and clipping
        // the "To" button out of reach). One chip, one dialog now, same as every other filter chip.
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareLogDateFromLabel).performClick()

        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerCancel).assertExists()
    }

    @Test
    fun logDetailEditIcon_tap_opensFieldsDialog_andTogglingNotesInvokesCallback() {
        var toggled: Pair<LogRowField, Boolean>? = null
        setCaseDetailScreenContent(
            case = startStopCase.copy(durationMode = DurationMode.NONE, intensityEnabled = false),
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            onLogFieldVisibleChange = { field, visible -> toggled = field to visible },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.logDetailEditDescription).performClick()

        // Duration/Intensity only offered when the Case tracks them (spec §6), same gating as Log Share's own field picker.
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
        // By tag, not by label text: the label/Switch semantics don't reliably merge into one
        // clickable node in every context (see LOG_DETAIL_FIELD_TOGGLE_TAG_PREFIX's own doc comment).
        composeTestRule.onNodeWithTag(LOG_DETAIL_FIELD_TOGGLE_TAG_PREFIX + LogRowField.NOTES.name).performClick()

        assertEquals(LogRowField.NOTES to false, toggled)
    }

    @Test
    fun eventRow_hidesNote_whenNotesFieldIsToggledOff() {
        val event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, note = "a private note")
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = event, tags = emptyList())),
            logVisibleFields = LogRowField.entries.toSet() - LogRowField.NOTES,
        )

        composeTestRule.onNodeWithText("a private note", substring = true).assertDoesNotExist()
    }

    @Test
    fun logTab_showsEmptyRangeMessage_whenTheFilteredLogIsEmptyButHistoryIsNot() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            logEvents = emptyList(),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareLogEmptyRangeMessage).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.eventListEmptyState).assertDoesNotExist()
    }

    @Test
    fun logTab_rendersOnlyLogEvents_notTheFullEventHistory() {
        // Regression guard for PROGRESS.md F4: the Log tab's row list must come from the capped,
        // paged uiState.logEvents, not the full uiState.events used by ongoing-event detection and
        // the Insights/Hunch tabs.
        val shown = testEvent(id = 8L, caseId = 1L, occurredAt = 0L, note = "shown row")
        val hidden = testEvent(id = 9L, caseId = 1L, occurredAt = 1_000L, note = "hidden row")
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = shown, tags = emptyList()), EventWithTags(event = hidden, tags = emptyList())),
            logEvents = listOf(EventWithTags(event = shown, tags = emptyList())),
        )

        composeTestRule.onNodeWithText("shown row", substring = true).assertExists()
        composeTestRule.onNodeWithText("hidden row", substring = true).assertDoesNotExist()
    }

    @Test
    fun logShowMoreButton_hidden_whenLogHasNoMoreEvents() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            logHasMore = false,
        )

        composeTestRule.onNodeWithText(PlainVoice.logShowMoreAction).assertDoesNotExist()
    }

    @Test
    fun logShowMoreButton_shown_whenLogHasMoreEvents() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            logHasMore = true,
        )

        composeTestRule.onNodeWithText(PlainVoice.logShowMoreAction).assertExists()
    }

    @Test
    fun logShowMoreButton_tap_invokesOnShowMoreLogEvents() {
        var tapped = false
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = testEvent(id = 8L, caseId = 1L, occurredAt = 0L), tags = emptyList())),
            logHasMore = true,
            onShowMoreLogEvents = { tapped = true },
        )

        composeTestRule.onNodeWithText(PlainVoice.logShowMoreAction).performClick()

        assertTrue(tapped)
    }

    @Test
    fun logTab_fullScreenList_lastRowsStopButton_doesNotOverlapRetroLogFab() {
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

    private fun openHunchTab() {
        composeTestRule.onNodeWithText(PlainVoice.caseDetailHunchTabLabel).performClick()
    }

    @Test
    fun hunchTab_fewEventsNoHunch_showsNoneCardWithoutNudge() {
        setCaseDetailScreenContent(events = eventsAt(2))
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchTabNoneTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.hunchTabNoneDataNote).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.hunchNudgeTitle).assertDoesNotExist()
    }

    @Test
    fun hunchTab_zeroEvents_stillOffersAddingAHunch() {
        setCaseDetailScreenContent(events = emptyList())
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchTabNoneDataNote).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.hunchAddButtonLabel).assertExists()
    }

    @Test
    fun hunchTab_fiveEventsNoHunch_showsNudgeCard() {
        setCaseDetailScreenContent(events = eventsAt(5))
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchNudgeTitle).assertExists()
    }

    @Test
    fun hunchTab_nudgeBody_reflectsTheRealEventCount_notTheFixedThreshold() {
        setCaseDetailScreenContent(events = eventsAt(8))
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchNudgeBody(startStopCase.icon, startStopCase.name, 8)).assertExists()
    }

    private data class SavedHunch(
        val direction: HunchDirection,
        val count: Int,
        val per: ExpectedPer,
        val metric: VerdictMetric,
        val window: ObservationWindow,
        val windowStartDate: Long?,
    )

    private val noneCase =
        testCase(id = 2L, name = "Coffee", icon = "☕", logFlow = LogFlow.ONE_TAP, durationMode = DurationMode.NONE)

    @Test
    fun hunchTab_addHunch_opensSheetAndSavesSelectedOptions() {
        var saved: SavedHunch? = null
        setCaseDetailScreenContent(
            onAddHunch = { d, c, p, m, w, s -> saved = SavedHunch(d, c, p, m, w, s) },
        )
        openHunchTab()

        composeTestRule.onAllNodesWithText(PlainVoice.hunchAddButtonLabel)[0].performClick()
        composeTestRule.onNodeWithText(PlainVoice.hunchCreatingSaveButton).performClick()

        assertEquals(HunchDirection.TOO_OFTEN, saved?.direction)
        assertEquals(ExpectedPer.WEEK, saved?.per)
        assertEquals(VerdictMetric.OCCURRENCE_COUNT, saved?.metric)
        assertEquals(ObservationWindow.SINCE_START, saved?.window)
        assertNull(saved?.windowStartDate)
    }

    @Test
    fun hunchCreationSheet_metricPicker_showsForDurationTrackingCase() {
        setCaseDetailScreenContent(case = startStopCase)
        openHunchTab()
        composeTestRule.onAllNodesWithText(PlainVoice.hunchAddButtonLabel)[0].performClick()

        composeTestRule.onNodeWithText(PlainVoice.hunchCreatingMetricLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.hunchMetricDaysActive).assertExists()
    }

    @Test
    fun hunchCreationSheet_metricPicker_absentForNoneCase() {
        setCaseDetailScreenContent(case = noneCase)
        openHunchTab()
        composeTestRule.onAllNodesWithText(PlainVoice.hunchAddButtonLabel)[0].performClick()

        composeTestRule.onNodeWithText(PlainVoice.hunchCreatingMetricLabel).assertDoesNotExist()
        // The window picker still shows, whatever the duration mode.
        composeTestRule.onNodeWithText(PlainVoice.hunchCreatingWindowLabel).assertExists()
    }

    @Test
    fun hunchCreationSheet_periodRow_swapsOptionsWhenDaysActiveIsPicked() {
        setCaseDetailScreenContent(case = startStopCase)
        openHunchTab()
        composeTestRule.onAllNodesWithText(PlainVoice.hunchAddButtonLabel)[0].performClick()

        // Occurrence count (default): Day / Week / Month.
        composeTestRule.onNodeWithText(PlainVoice.hunchExpectedPerDay).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.hunchExpectedPerQuarter).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.hunchMetricDaysActive).performClick()

        // Days active: Week / Month / 3 Months — Day drops out.
        composeTestRule.onNodeWithText(PlainVoice.hunchExpectedPerQuarter).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.hunchExpectedPerDay).assertDoesNotExist()
    }

    @Test
    fun hunchCreationSheet_customWindow_revealsTheDateField() {
        setCaseDetailScreenContent(case = startStopCase)
        openHunchTab()
        composeTestRule.onAllNodesWithText(PlainVoice.hunchAddButtonLabel)[0].performClick()

        composeTestRule.onNodeWithText(PlainVoice.hunchWindowCustomDatePrompt).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.hunchWindowCustom).performClick()

        composeTestRule.onNodeWithText(PlainVoice.hunchWindowCustomDatePrompt).assertExists()
    }

    @Test
    fun hunchTab_daysActiveVerdictCard_rendersDaysActiveCopyAndNoHeatmap() {
        val oneDay = 24 * 60 * 60_000L
        val hunch =
            HunchEntity(
                id = 1L,
                caseId = 1L,
                direction = HunchDirection.TOO_OFTEN,
                expectedCount = 5,
                expectedPer = ExpectedPer.WEEK,
                createdAt = 0L,
                resolvedAt = null,
                metric = VerdictMetric.DAYS_ACTIVE,
            )
        // 5 active days over a 20-day window clears the Preliminary bar; the card renders the
        // days-active copy set. Kept small so the full 3-tab screen stays light on CI's emulator.
        val events =
            List(5) {
                EventWithTags(
                    testEvent(id = it.toLong(), caseId = 1L, occurredAt = it * 4 * oneDay, endedAt = it * 4 * oneDay),
                    emptyList(),
                )
            }
        setCaseDetailScreenContent(activeHunch = hunch, events = events, nowMillis = { 20 * oneDay })
        openHunchTab()

        // The verdict rate reads as a share of days, not a "×" count.
        composeTestRule.onAllNodesWithText("days/week", substring = true).onFirst().assertExists()
        composeTestRule.onAllNodesWithText("active days", substring = true).onFirst().assertExists()
        // Text-only card: the calendar heatmap belongs to Insights, never the verdict card.
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelHeatmap).assertDoesNotExist()
    }

    @Test
    fun hunchTab_activeVerdictHunch_resolveInvokesOnResolveHunch() {
        val hunch =
            HunchEntity(
                id = 1L,
                caseId = 1L,
                direction = HunchDirection.TOO_OFTEN,
                expectedCount = 5,
                expectedPer = ExpectedPer.WEEK,
                createdAt = 0L,
                resolvedAt = null,
            )
        var resolved: HunchEntity? = null
        val thirtyDaysMillis = 30 * 24 * 60 * 60_000L
        setCaseDetailScreenContent(
            activeHunch = hunch,
            events = eventsAt(6),
            nowMillis = { thirtyDaysMillis },
            onResolveHunch = { resolved = it },
        )
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchResolveLabel).performClick()

        assertEquals(hunch, resolved)
    }

    @Test
    fun hunchTab_activeVerdict_stillShowsPastHunches() {
        val activeHunch =
            HunchEntity(
                id = 1L,
                caseId = 1L,
                direction = HunchDirection.TOO_OFTEN,
                expectedCount = 5,
                expectedPer = ExpectedPer.WEEK,
                createdAt = 0L,
                resolvedAt = null,
            )
        val resolvedHunch =
            HunchEntity(
                id = 2L,
                caseId = 1L,
                direction = HunchDirection.NOT_ENOUGH,
                expectedCount = 1,
                expectedPer = ExpectedPer.DAY,
                createdAt = 0L,
                resolvedAt = 20 * 24 * 60 * 60_000L,
            )
        val thirtyDaysMillis = 30 * 24 * 60 * 60_000L
        setCaseDetailScreenContent(
            activeHunch = activeHunch,
            hunchHistory = listOf(resolvedHunch),
            events = eventsAt(6),
            nowMillis = { thirtyDaysMillis },
        )
        openHunchTab()

        // Regression guard: the resolved-Hunch record used to disappear entirely once a new
        // Hunch went active. It must still render underneath the active verdict card.
        composeTestRule.onNodeWithText(PlainVoice.hunchHistoryHeader).assertExists()
    }

    @Test
    fun hunchTab_historyRow_leadsWithTheMadeAndResolvedStamp() {
        val resolvedAt = 20 * 24 * 60 * 60_000L
        val resolvedHunch =
            HunchEntity(
                id = 1L,
                caseId = 1L,
                direction = HunchDirection.TOO_OFTEN,
                expectedCount = 1,
                expectedPer = ExpectedPer.DAY,
                createdAt = 0L,
                resolvedAt = resolvedAt,
            )
        setCaseDetailScreenContent(
            hunchHistory = listOf(resolvedHunch),
            events = eventsAt(6),
            nowMillis = { 30 * 24 * 60 * 60_000L },
        )
        openHunchTab()

        // Regression guard: this used to be `hunchHistoryRowWhen(monthsAgo(resolvedAt))`, which
        // read "0 months ago" for anything resolved inside its first month.
        composeTestRule
            .onNodeWithText(PlainVoice.hunchHistoryRowStamp(formatEventDate(0L), formatEventDate(resolvedAt)))
            .assertExists()
    }

    private fun resolvedHunchesForWindowingTests() =
        (1..7).map { index ->
            HunchEntity(
                id = index.toLong(),
                caseId = 1L,
                direction = HunchDirection.TOO_OFTEN,
                expectedCount = 1,
                expectedPer = ExpectedPer.DAY,
                createdAt = 0L,
                resolvedAt = (30L - index) * 24 * 60 * 60_000L,
            )
        }

    @Test
    fun hunchTab_history_showsFirstFiveThenRevealsRestOnShowMore() {
        val resolvedHunches = resolvedHunchesForWindowingTests()
        setCaseDetailScreenContent(
            hunchHistory = resolvedHunches,
            events = eventsAt(6),
            nowMillis = { 40 * 24 * 60 * 60_000L },
        )
        openHunchTab()

        fun stampFor(hunch: HunchEntity) = PlainVoice.hunchHistoryRowStamp(formatEventDate(0L), formatEventDate(hunch.resolvedAt!!))

        resolvedHunches.take(5).forEach { hunch ->
            composeTestRule.onNodeWithText(stampFor(hunch)).assertExists()
        }
        resolvedHunches.drop(5).forEach { hunch ->
            composeTestRule.onNodeWithText(stampFor(hunch)).assertDoesNotExist()
        }

        composeTestRule.onNodeWithText(PlainVoice.hunchHistoryShowMoreAction).performClick()

        resolvedHunches.forEach { hunch ->
            composeTestRule.onNodeWithText(stampFor(hunch)).assertExists()
        }
    }

    @Test
    fun hunchTab_history_retentionNote_onlyShowsOnceFullyExpanded() {
        setCaseDetailScreenContent(
            hunchHistory = resolvedHunchesForWindowingTests(),
            events = eventsAt(6),
            nowMillis = { 40 * 24 * 60 * 60_000L },
        )
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchHistoryRetentionNote).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.hunchHistoryShowMoreAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.hunchHistoryRetentionNote).assertExists()
    }
}
