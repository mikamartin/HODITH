package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.LogSortOrder
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.formatEventTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
        activeHunch: HunchEntity? = null,
        hunchHistory: List<HunchEntity> = emptyList(),
        onEditCase: (Long) -> Unit = {},
        onOpenTriggers: (Long) -> Unit = {},
        onOpenShare: (Long) -> Unit = {},
        onEditEvent: (caseId: Long, eventId: Long) -> Unit = { _, _ -> },
        onSaveEvent: (LogDraft) -> Unit = {},
        onStopEvent: (EventEntity) -> Unit = {},
        nowMillis: () -> Long = { 10_000L },
        timeFormat: TimeFormat = TimeFormat.TWELVE_HOUR,
        onAddHunch: (HunchDirection, Int, ExpectedPer, VerdictMetric, ObservationWindow, Long?) -> Unit =
            { _, _, _, _, _, _ -> },
        onResolveHunch: (HunchEntity) -> Unit = {},
        onLogSortOrderChange: (LogSortOrder) -> Unit = {},
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
                            activeHunch = activeHunch,
                            hunchHistory = hunchHistory,
                            isLoading = false,
                        ),
                    onBack = {},
                    onEditCase = onEditCase,
                    onEditEvent = onEditEvent,
                    onOpenTriggers = onOpenTriggers,
                    onOpenShare = onOpenShare,
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
                    onShowMoreLogEvents = onShowMoreLogEvents,
                )
            }
        }
    }

    @Test
    fun headerActions_editTriggersAndShareIcons_invokeCallbacksWithCaseId() {
        var editedCaseId: Long? = null
        var triggersCaseId: Long? = null
        var shareCaseId: Long? = null
        setCaseDetailScreenContent(
            onEditCase = { editedCaseId = it },
            onOpenTriggers = { triggersCaseId = it },
            onOpenShare = { shareCaseId = it },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.shareOpenDescription).performClick()
        composeTestRule.onNodeWithContentDescription(PlainVoice.triggersOpenDescription).performClick()
        composeTestRule.onNodeWithContentDescription(PlainVoice.caseDetailEditDescription).performClick()

        assertEquals(startStopCase.id, shareCaseId)
        assertEquals(startStopCase.id, triggersCaseId)
        assertEquals(startStopCase.id, editedCaseId)
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

        composeTestRule.onNodeWithText(PlainVoice.logSortLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSortByStartLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSortByEndLabel).assertExists()
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

        composeTestRule.onNodeWithText(PlainVoice.logSortByEndLabel).performClick()

        assertEquals(LogSortOrder.BY_END, changedTo)
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
}
