package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.TimeFormat
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

    // Just over the 24h stale-ongoing threshold.
    private val staleNow = 24 * 60 * 60_000L + 1

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
        activeHunch: HunchEntity? = null,
        hunchHistory: List<HunchEntity> = emptyList(),
        onEditCase: (Long) -> Unit = {},
        onOpenTriggers: (Long) -> Unit = {},
        onOpenShare: (Long) -> Unit = {},
        onSaveEvent: (LogDraft, EventEntity?, List<TagEntity>) -> Unit = { _, _, _ -> },
        onStopEvent: (EventEntity) -> Unit = {},
        onDismissStalePrompt: (EventEntity) -> Unit = {},
        nowMillis: () -> Long = { 10_000L },
        timeFormat: TimeFormat = TimeFormat.TWELVE_HOUR,
        onAddHunch: (HunchDirection, Int, ExpectedPer) -> Unit = { _, _, _ -> },
        onResolveHunch: (HunchEntity) -> Unit = {},
        onDismissHunchNudge: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice, LocalTimeFormat provides timeFormat) {
                CaseDetailScreen(
                    uiState =
                        CaseDetailUiState(
                            case = case,
                            events = events,
                            activeHunch = activeHunch,
                            hunchHistory = hunchHistory,
                            isLoading = false,
                        ),
                    onBack = {},
                    onEditCase = onEditCase,
                    onOpenTriggers = onOpenTriggers,
                    onOpenShare = onOpenShare,
                    onDeleteEvent = {},
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
                    onDismissStalePrompt = onDismissStalePrompt,
                    nowMillis = nowMillis,
                    onAddHunch = onAddHunch,
                    onResolveHunch = onResolveHunch,
                    onDismissHunchNudge = onDismissHunchNudge,
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
        setCaseDetailScreenContent(onSaveEvent = { draft, _, _ -> savedDraft = draft })

        composeTestRule.onNodeWithContentDescription(PlainVoice.retroLogEntryDescription, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetStartButton).performClick()

        assertNotNull(savedDraft)
        assertNull(savedDraft?.endedAt)
    }

    @Test
    fun stopNowInSheet_thenSave_savesWithAnEndedAt() {
        var savedDraft: LogDraft? = null
        setCaseDetailScreenContent(onSaveEvent = { draft, _, _ -> savedDraft = draft })

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
    fun editingStoppedEvent_backToOngoing_thenSave_savesWithNullEndedAt() {
        var savedDraft: LogDraft? = null
        val stopped = testEvent(id = 7L, caseId = 1L, occurredAt = 0L, endedAt = 5_000L)
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = stopped, tags = emptyList())),
            onSaveEvent = { draft, _, _ -> savedDraft = draft },
            nowMillis = { 10_000L },
        )

        composeTestRule.onNodeWithText(formatEventTime(stopped.occurredAt, 10_000L, use24Hour = false)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetBackToOngoingAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft)
        assertNull(savedDraft?.endedAt)
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
    fun logSortToggle_byEnded_floatsARunningEventAboveAMoreRecentlyStartedFinishedOne() {
        val running = testEvent(id = 5L, caseId = 1L, occurredAt = 1_000L, note = "still going")
        val finished = testEvent(id = 8L, caseId = 1L, occurredAt = 2_000L, endedAt = 3_000L, note = "all done")
        setCaseDetailScreenContent(
            case = startStopCase,
            events =
                listOf(
                    EventWithTags(event = running, tags = emptyList()),
                    EventWithTags(event = finished, tags = emptyList()),
                ),
            nowMillis = { 10_000L },
        )

        // Default "Started": the later-started finished event sits above the running one.
        assertTrue(
            composeTestRule.onNodeWithText("all done", substring = true).getUnclippedBoundsInRoot().top <
                composeTestRule.onNodeWithText("still going", substring = true).getUnclippedBoundsInRoot().top,
        )

        composeTestRule.onNodeWithText(PlainVoice.logSortByEndLabel).performClick()

        // "Ended": the running event floats to the top.
        assertTrue(
            composeTestRule.onNodeWithText("still going", substring = true).getUnclippedBoundsInRoot().top <
                composeTestRule.onNodeWithText("all done", substring = true).getUnclippedBoundsInRoot().top,
        )
    }

    @Test
    fun staleOngoingBanner_editEndTime_opensSheetInEditModeForThatEvent() {
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = ongoingEvent(), tags = emptyList())),
            nowMillis = { staleNow },
        )

        composeTestRule.onNodeWithText(PlainVoice.staleOngoingEditEndTimeAction).performClick()

        composeTestRule.onNodeWithText(PlainVoice.logSheetEditEventTitle).assertExists()
    }

    @Test
    fun staleOngoingBanner_stillGoing_invokesOnDismissStalePrompt() {
        val ongoing = ongoingEvent()
        var dismissed: EventEntity? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = ongoing, tags = emptyList())),
            onDismissStalePrompt = { dismissed = it },
            nowMillis = { staleNow },
        )

        composeTestRule.onNodeWithText(PlainVoice.staleOngoingStillGoingAction).performClick()

        assertEquals(ongoing, dismissed)
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

    @Test
    fun multipleStaleOngoingEvents_showConsolidatedBanner_thatDismissesAll() {
        val dismissed = mutableListOf<EventEntity>()
        setCaseDetailScreenContent(
            events =
                listOf(
                    EventWithTags(testEvent(id = 5L, caseId = 1L, occurredAt = 0L), emptyList()),
                    EventWithTags(testEvent(id = 6L, caseId = 1L, occurredAt = 10L), emptyList()),
                ),
            onDismissStalePrompt = { dismissed += it },
            // Well past the 24h stale threshold so both events, not just the first, are stale.
            nowMillis = { 3L * 24 * 60 * 60_000L },
        )

        composeTestRule.onNodeWithText(PlainVoice.staleOngoingMultiPromptMessage(startStopCase.name, 2)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.staleOngoingStillGoingAction).performClick()

        assertEquals(2, dismissed.size)
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
        composeTestRule.onNodeWithText(PlainVoice.hunchNudgeTitle).assertDoesNotExist()
    }

    @Test
    fun hunchTab_fiveEventsNoHunch_showsNudgeCard() {
        setCaseDetailScreenContent(events = eventsAt(5))
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchNudgeTitle).assertExists()
    }

    @Test
    fun hunchTab_dismissNudge_invokesOnDismissHunchNudge() {
        var dismissed = false
        setCaseDetailScreenContent(events = eventsAt(5), onDismissHunchNudge = { dismissed = true })
        openHunchTab()

        composeTestRule.onNodeWithText(PlainVoice.hunchNudgeDismissAction).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun hunchTab_addHunch_opensSheetAndSavesSelectedOptions() {
        var saved: Triple<HunchDirection, Int, ExpectedPer>? = null
        setCaseDetailScreenContent(onAddHunch = { direction, count, per -> saved = Triple(direction, count, per) })
        openHunchTab()

        composeTestRule.onAllNodesWithText(PlainVoice.hunchAddButtonLabel)[0].performClick()
        composeTestRule.onNodeWithText(PlainVoice.hunchCreatingSaveButton).performClick()

        assertEquals(HunchDirection.TOO_OFTEN, saved?.first)
        assertEquals(ExpectedPer.WEEK, saved?.third)
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
