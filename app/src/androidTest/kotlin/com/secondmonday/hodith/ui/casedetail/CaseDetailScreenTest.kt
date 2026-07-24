package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        CaseEntity(
            id = 1L,
            name = "Focus session",
            icon = "⏱️",
            createdAt = 0L,
            logFlow = LogFlow.DETAIL_SHEET,
            durationMode = DurationMode.START_STOP,
            intensityEnabled = false,
            hunchNudgeDismissed = false,
            pinned = false,
            checkInDays = null,
            lastCheckInAt = null,
            sortOrder = 0,
            archived = false,
        )

    private fun ongoingEvent() =
        EventEntity(id = 5L, caseId = 1L, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L)

    private fun setCaseDetailScreenContent(
        events: List<EventWithTags> = emptyList(),
        onSaveEvent: (LogDraft, EventEntity?, List<TagEntity>) -> Unit = { _, _, _ -> },
        onStopEvent: (EventEntity) -> Unit = {},
        onDismissStalePrompt: (EventEntity) -> Unit = {},
        nowMillis: () -> Long = { 10_000L },
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                CaseDetailScreen(
                    uiState = CaseDetailUiState(case = startStopCase, events = events, isLoading = false),
                    onBack = {},
                    onEditCase = {},
                    onDeleteEvent = {},
                    newEventDraft = {
                        LogDraft(
                            occurredAt = nowMillis(),
                            intensity = null,
                            durationMinutes = "",
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
                )
            }
        }
    }

    @Smoke
    @Test
    fun retroLogFab_forStartStopCaseWithNoOngoingEvent_showsOngoingByDefaultAndStartsOnSave() {
        var savedDraft: LogDraft? = null
        setCaseDetailScreenContent(onSaveEvent = { draft, _, _ -> savedDraft = draft })

        composeTestRule.onNodeWithText(PlainVoice.retroLogEntryLabel, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetStartButton).performClick()

        assertNotNull(savedDraft)
        assertNull(savedDraft?.endedAt)
    }

    @Test
    fun stopNowInSheet_thenSave_savesWithAnEndedAt() {
        var savedDraft: LogDraft? = null
        setCaseDetailScreenContent(onSaveEvent = { draft, _, _ -> savedDraft = draft })

        composeTestRule.onNodeWithText(PlainVoice.retroLogEntryLabel, useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetStopNowAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetSaveButton).performClick()

        assertNotNull(savedDraft?.endedAt)
    }

    @Test
    fun ongoingEvent_showsStopButtonInHeader_andInvokesOnStopEvent() {
        val ongoing = ongoingEvent()
        var stopped: EventEntity? = null
        setCaseDetailScreenContent(
            events = listOf(EventWithTags(event = ongoing, tags = emptyList())),
            onStopEvent = { stopped = it },
        )

        composeTestRule.onNodeWithContentDescription(PlainVoice.stopActionDescription(startStopCase.name)).performClick()

        assertEquals(ongoing, stopped)
    }

    @Test
    fun eventList_showsOngoingLabelForTheOpenEvent() {
        setCaseDetailScreenContent(events = listOf(EventWithTags(event = ongoingEvent(), tags = emptyList())))

        // Exact-match lookup: the header's OngoingElapsedText renders "Ongoing · <elapsed>",
        // distinct from the event row's bare "Ongoing" label, so this uniquely targets the row.
        composeTestRule.onNodeWithText(PlainVoice.logSheetOngoingLabel).assertExists()
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
}
