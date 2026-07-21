package com.secondmonday.hodith.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.SeriousVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.HomeLogSheetState
import com.secondmonday.hodith.viewmodel.HomeUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.QuickLogUndo
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * First Compose UI instrumented test in the repo (TESTING.md's deferral note named
 * "one-tap log + undo" as the scenario to stand this pattern up with). [HomeScreen] is a
 * stateless composable driven entirely by plain data + callbacks, so these tests exercise it
 * directly with `createComposeRule()` rather than needing Hilt/Activity/Room ceremony — the
 * ViewModel-level wiring (repository inserts/deletes, tag fetching) is covered separately by
 * `HomeViewModelMappingTest` and the DAO instrumented tests.
 */
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val oneTapRow =
        HomeCaseRow(
            caseId = 1L,
            icon = "🐛",
            name = "One Tap Case",
            todayCount = 0,
            weekCount = 0,
            logFlow = LogFlow.ONE_TAP,
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
        )

    private fun setHomeScreenContent(
        uiState: HomeUiState = HomeUiState(cases = listOf(oneTapRow), isLoading = false),
        logSheet: HomeLogSheetState? = null,
        quickLogUndo: MutableSharedFlow<QuickLogUndo> = MutableSharedFlow(extraBufferCapacity = 1),
        onOpenCase: (Long) -> Unit = {},
        onQuickLogTap: (HomeCaseRow) -> Unit = {},
        onSaveLogSheetEvent: (LogDraft) -> Unit = {},
        onUndoQuickLog: (Long) -> Unit = {},
        onDismissStalePrompt: (EventEntity) -> Unit = {},
        nowMillis: () -> Long = { 0L },
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides SeriousVoice) {
                HomeScreen(
                    uiState = uiState,
                    logSheet = logSheet,
                    quickLogUndo = quickLogUndo,
                    onNewCase = {},
                    onOpenCase = onOpenCase,
                    onQuickLogTap = onQuickLogTap,
                    onDismissLogSheet = {},
                    onSaveLogSheetEvent = onSaveLogSheetEvent,
                    onUndoQuickLog = onUndoQuickLog,
                    onDismissStalePrompt = onDismissStalePrompt,
                    nowMillis = nowMillis,
                )
            }
        }
    }

    @Test
    fun quickLogButton_isDistinctTapTargetFromTheRow() {
        var quickLogTapped: HomeCaseRow? = null
        var openedCaseId: Long? = null
        setHomeScreenContent(
            onQuickLogTap = { quickLogTapped = it },
            onOpenCase = { openedCaseId = it },
        )

        composeTestRule.onNodeWithContentDescription(SeriousVoice.quickLogButtonDescription(oneTapRow.name)).performClick()

        assertEquals(oneTapRow, quickLogTapped)
        assertNull(openedCaseId)
    }

    @Test
    fun rowTap_opensCaseDetail_notQuickLog() {
        var quickLogTapped: HomeCaseRow? = null
        var openedCaseId: Long? = null
        setHomeScreenContent(
            onQuickLogTap = { quickLogTapped = it },
            onOpenCase = { openedCaseId = it },
        )

        composeTestRule.onNodeWithText(oneTapRow.name).performClick()

        assertEquals(oneTapRow.caseId, openedCaseId)
        assertNull(quickLogTapped)
    }

    @Test
    fun quickLogUndo_showsSnackbarAndUndoActionInvokesCallback() {
        val quickLogUndo = MutableSharedFlow<QuickLogUndo>(extraBufferCapacity = 1)
        var undoneEventId: Long? = null
        setHomeScreenContent(
            quickLogUndo = quickLogUndo,
            onUndoQuickLog = { undoneEventId = it },
        )

        composeTestRule.runOnIdle {
            quickLogUndo.tryEmit(QuickLogUndo(eventId = 42L, caseName = oneTapRow.name))
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(SeriousVoice.quickLogUndoMessage(oneTapRow.name))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText(SeriousVoice.quickLogUndoAction).performClick()

        assertEquals(42L, undoneEventId)
    }

    @Test
    fun ongoingRow_showsStopButtonInsteadOfQuickLog_andInvokesOnQuickLogTap() {
        val ongoingEvent = EventEntity(caseId = 2L, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L)
        val ongoingRow =
            oneTapRow.copy(caseId = 2L, name = "Ongoing Case", durationMode = DurationMode.START_STOP, ongoingEvent = ongoingEvent)
        var quickLogTapped: HomeCaseRow? = null
        setHomeScreenContent(
            uiState = HomeUiState(cases = listOf(ongoingRow), isLoading = false),
            onQuickLogTap = { quickLogTapped = it },
            nowMillis = { 10_000L },
        )

        composeTestRule.onNodeWithContentDescription(SeriousVoice.quickLogButtonDescription(ongoingRow.name)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(SeriousVoice.stopActionDescription(ongoingRow.name)).performClick()

        assertEquals(ongoingRow, quickLogTapped)
    }

    @Test
    fun nonOngoingStartStopRow_usesStartContentDescription() {
        val startStopRow = oneTapRow.copy(durationMode = DurationMode.START_STOP)
        setHomeScreenContent(uiState = HomeUiState(cases = listOf(startStopRow), isLoading = false))

        composeTestRule.onNodeWithContentDescription(SeriousVoice.startActionDescription(startStopRow.name)).assertExists()
    }

    @Test
    fun staleOngoingBanner_showsPastThreshold_andStillGoingInvokesDismiss() {
        val ongoingEvent = EventEntity(caseId = 2L, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L)
        val staleRow =
            oneTapRow.copy(caseId = 2L, name = "Stale Case", durationMode = DurationMode.START_STOP, ongoingEvent = ongoingEvent)
        var dismissed: EventEntity? = null
        // Just over the 24h threshold, so the banner should be showing.
        val now = 24 * 60 * 60_000L + 1
        setHomeScreenContent(
            uiState = HomeUiState(cases = listOf(staleRow), isLoading = false),
            onDismissStalePrompt = { dismissed = it },
            nowMillis = { now },
        )

        composeTestRule.onNodeWithText(SeriousVoice.staleOngoingStillGoingAction).performClick()

        assertEquals(ongoingEvent, dismissed)
    }

    @Test
    fun staleOngoingBanner_doesNotShowBeforeThreshold() {
        val ongoingEvent = EventEntity(caseId = 2L, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L)
        val ongoingRow =
            oneTapRow.copy(caseId = 2L, name = "Fresh Case", durationMode = DurationMode.START_STOP, ongoingEvent = ongoingEvent)
        setHomeScreenContent(
            uiState = HomeUiState(cases = listOf(ongoingRow), isLoading = false),
            nowMillis = { 10_000L },
        )

        composeTestRule.onNodeWithText(SeriousVoice.staleOngoingStillGoingAction).assertDoesNotExist()
    }

    @Test
    fun logSheet_whenPresentForACase_savesDraftOnSaveTap() {
        val sheetState =
            HomeLogSheetState(
                caseId = 9L,
                caseName = "Sheet Case",
                durationMode = DurationMode.NONE,
                intensityEnabled = false,
                tagSuggestions = emptyList(),
                draft =
                    LogDraft(
                        occurredAt = 0L,
                        intensity = null,
                        durationMinutes = "",
                        note = "",
                        tags = emptyList(),
                        endedAt = null,
                        existingEndedAt = null,
                    ),
            )
        var savedDraft: LogDraft? = null
        setHomeScreenContent(
            logSheet = sheetState,
            onSaveLogSheetEvent = { savedDraft = it },
        )

        composeTestRule.onNodeWithText(SeriousVoice.logSheetSaveButton).performClick()

        assertEquals(sheetState.draft, savedDraft)
    }
}
