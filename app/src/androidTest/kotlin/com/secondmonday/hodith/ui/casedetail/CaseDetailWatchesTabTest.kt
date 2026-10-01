package com.secondmonday.hodith.ui.casedetail

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.WatchesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val MILLIS_PER_DAY = 86_400_000L
private const val NOW = 40L * MILLIS_PER_DAY

/**
 * Drives [WatchesTabContent] and [WatchEditorSheet] directly, stateless, same
 * pattern as `CaseDetailScreenTest`/the retired `NotificationsScreenTest` -- no Hilt, since
 * [CaseDetailScreen]'s bell tab owns its own `hiltViewModel<WatchesViewModel>()` instance
 * that this test intentionally never reaches (see PROGRESS.md N2's architecture notes).
 */
@UiTest
class CaseDetailWatchesTabTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun oftenAt(
        id: Long,
        occurredAt: Long,
    ) = testEvent(id = id, caseId = 1L, occurredAt = occurredAt)

    private fun oftenWatch(
        id: Long = 1L,
        threshold: Int = 1,
        windowDays: Int? = 30,
        expectedPer: ExpectedPer = ExpectedPer.WEEK,
        metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity: Int? = null,
        enabled: Boolean = true,
        lastFiredAt: Long? = null,
    ) = WatchEntity(
        id = id,
        caseId = 1L,
        kind = WatchKind.OFTEN,
        threshold = threshold,
        windowDays = windowDays,
        expectedPer = expectedPer,
        metric = metric,
        minIntensity = minIntensity,
        enabled = enabled,
        lastFiredAt = lastFiredAt,
    )

    private fun quietWatch(
        id: Long = 2L,
        threshold: Int = 14,
        enabled: Boolean = true,
    ) = WatchEntity(
        id = id,
        caseId = 1L,
        kind = WatchKind.QUIET,
        threshold = threshold,
        windowDays = null,
        expectedPer = ExpectedPer.WEEK,
        metric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity = null,
        enabled = enabled,
        lastFiredAt = null,
    )

    private fun setTabContent(
        watches: List<WatchEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
        checkInsEnabled: Boolean = true,
        onCheckInToggle: (Boolean) -> Unit = {},
        onSetEnabled: (Long, Boolean) -> Unit = { _, _ -> },
        onCreateRequest: () -> Unit = {},
        onCardClick: (WatchEntity) -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            WatchesTabContent(
                uiState =
                    WatchesUiState(
                        watches = watches,
                        events = events,
                        durationMode = durationMode,
                        intensityEnabled = intensityEnabled,
                        checkInsEnabled = checkInsEnabled,
                        caseCreatedAt = 0L,
                        isLoading = false,
                    ),
                now = NOW,
                voice = PlainVoice,
                onCheckInToggle = onCheckInToggle,
                onSetEnabled = onSetEnabled,
                onCreateRequest = onCreateRequest,
                onCardClick = onCardClick,
            )
        }
    }

    @Test
    fun checkInsRow_present_andTogglingInvokesCallback() {
        var toggled: Boolean? = null
        // Empty watch list so the row's Switch is the only toggleable node in the tree.
        setTabContent(watches = emptyList(), checkInsEnabled = true, onCheckInToggle = { toggled = it })

        composeTestRule.onNodeWithText(PlainVoice.caseCheckInLabel).assertExists()
        composeTestRule.onNode(isToggleable()).performClick()

        assertEquals(false, toggled)
    }

    @Test
    fun emptyState_showsCreateCta_andInvokesOnCreateRequest() {
        var created = false
        setTabContent(watches = emptyList(), onCreateRequest = { created = true })

        composeTestRule.onNodeWithText(PlainVoice.watchesEmptyTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.watchesEmptyCta).performClick()

        assertEquals(true, created)
    }

    @Test
    fun oftenCard_showsTitleAndEarlyState() {
        // No events yet -- NO_VERDICT, so the Now zone shows the Early-days badge, not a verdict.
        val watch = oftenWatch(threshold = 3, windowDays = 30, expectedPer = ExpectedPer.WEEK)
        setTabContent(watches = listOf(watch), events = emptyList())

        composeTestRule
            .onNodeWithText(PlainVoice.watchCardTitleOften(3, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT))
            .assertExists()
        composeTestRule.onNodeWithText(PlainVoice.expectationEarlyBadgeLabel).assertExists()
    }

    @Test
    fun quietCard_showsTitleAndNowLine() {
        val watch = quietWatch(threshold = 14)
        val events = listOf(oftenAt(1L, NOW - 3 * MILLIS_PER_DAY))
        setTabContent(watches = listOf(watch), events = events)

        composeTestRule.onNodeWithText(PlainVoice.watchCardTitleQuiet(14)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.watchNowLineQuiet(3L)).assertExists()
    }

    @Test
    fun oftenCard_comparisonLine_hiddenBelowTheConfidenceTier() {
        // A single event never clears PRELIMINARY_MIN_EVENTS (5) -- NO_VERDICT, so only the
        // Early-days badge shows, never a tier badge.
        val watch = oftenWatch(threshold = 1, windowDays = 30)
        setTabContent(watches = listOf(watch), events = listOf(oftenAt(1L, NOW)))

        composeTestRule.onNodeWithText(PlainVoice.expectationEarlyBadgeLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.expectationTierBadgeLabel(ConfidenceTier.PRELIMINARY)).assertDoesNotExist()
    }

    @Test
    fun oftenCard_comparisonLine_shownOnceTheConfidenceTierClears() {
        // 5 events across a 30-day window clears both PRELIMINARY_MIN_EVENTS (5) and
        // PRELIMINARY_MIN_DAYS (14) -- a real (non-NO_VERDICT) tier badge shows.
        val watch = oftenWatch(threshold = 1, windowDays = 30)
        val events = (0 until 5).map { i -> oftenAt(i.toLong(), NOW - i * MILLIS_PER_DAY) }
        setTabContent(watches = listOf(watch), events = events)

        composeTestRule
            .onNodeWithText(PlainVoice.expectationTierBadgeLabel(ConfidenceTier.PRELIMINARY))
            .assertExists()
        composeTestRule.onNodeWithText(PlainVoice.expectationEarlyBadgeLabel).assertDoesNotExist()
    }

    @Test
    fun disabledCard_stillShowsCurrentState() {
        val watch = oftenWatch(threshold = 3, enabled = false)
        setTabContent(watches = listOf(watch), events = emptyList())

        composeTestRule.onNodeWithText(PlainVoice.expectationEarlyBadgeLabel).assertExists()
    }

    @Test
    fun card_toggleSwitch_invokesOnSetEnabled() {
        val watch = oftenWatch(id = 7L, threshold = 3, enabled = true)
        var toggled: Pair<Long, Boolean>? = null
        setTabContent(watches = listOf(watch), onSetEnabled = { id, enabled -> toggled = id to enabled })

        val title = PlainVoice.watchCardTitleOften(3, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT)
        composeTestRule.onNodeWithContentDescription(PlainVoice.watchToggleDescription(title)).performClick()

        assertEquals(7L to false, toggled)
    }

    @Test
    fun card_tap_invokesOnCardClick() {
        val watch = oftenWatch(id = 9L, threshold = 3)
        var clicked: WatchEntity? = null
        setTabContent(watches = listOf(watch), onCardClick = { clicked = it })

        composeTestRule
            .onNodeWithText(PlainVoice.watchCardTitleOften(3, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT))
            .performClick()

        assertEquals(watch, clicked)
    }

    // ---- WatchEditorSheet ----

    private fun setEditorContent(
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
        editing: WatchEntity? = null,
        onDismiss: () -> Unit = {},
        onSave: (WatchKind, Int, Int?, ExpectedPer, VerdictMetric, Int?) -> Unit = { _, _, _, _, _, _ -> },
        onDelete: () -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            WatchEditorSheet(
                voice = PlainVoice,
                durationMode = durationMode,
                intensityEnabled = intensityEnabled,
                editing = editing,
                onDismiss = onDismiss,
                onSave = onSave,
                onDelete = onDelete,
            )
        }
    }

    @Test
    fun editor_create_defaultOften_savesWithDefaults() {
        var saved: Triple<WatchKind, Int, Int?>? = null
        setEditorContent(onSave = { kind, threshold, windowDays, _, _, _ -> saved = Triple(kind, threshold, windowDays) })

        composeTestRule.onNodeWithText(PlainVoice.watchesCreateTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.watchesSaveButton).performClick()

        assertEquals(WatchKind.OFTEN, saved?.first)
        assertEquals(14, saved?.third)
    }

    @Test
    fun editor_editingExisting_prefillsTitleAndSavesUpdatedFields() {
        val existing = oftenWatch(id = 3L, threshold = 5, windowDays = 30, expectedPer = ExpectedPer.MONTH)
        var saved: Triple<Int, Int?, ExpectedPer>? = null
        setEditorContent(
            editing = existing,
            onSave = { _, threshold, windowDays, expectedPer, _, _ -> saved = Triple(threshold, windowDays, expectedPer) },
        )

        composeTestRule.onNodeWithText(PlainVoice.watchesEditTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.watchesSaveButton).performClick()

        assertEquals(5, saved?.first)
        assertEquals(30, saved?.second)
        assertEquals(ExpectedPer.MONTH, saved?.third)
    }

    @Test
    fun editor_deleteButton_onlyShownWhenEditing() {
        setEditorContent(editing = null)
        composeTestRule.onNodeWithContentDescription(PlainVoice.watchDeleteDescription(""), substring = true).assertDoesNotExist()
    }

    @Test
    fun editor_delete_opensConfirmDialog_confirmInvokesCallback() {
        val existing = quietWatch(id = 4L, threshold = 14)
        var deleted = false
        setEditorContent(editing = existing, onDelete = { deleted = true })

        val summary = PlainVoice.watchSummary(existing.kind, existing.threshold, existing.windowDays)
        composeTestRule.onNodeWithContentDescription(PlainVoice.watchDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.watchesDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.watchesDeleteConfirmAction).performClick()

        assertEquals(true, deleted)
    }

    @Test
    fun editor_delete_cancelDoesNotInvokeCallback() {
        val existing = quietWatch(id = 4L, threshold = 14)
        var deleted = false
        setEditorContent(editing = existing, onDelete = { deleted = true })

        val summary = PlainVoice.watchSummary(existing.kind, existing.threshold, existing.windowDays)
        composeTestRule.onNodeWithContentDescription(PlainVoice.watchDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.watchesDeleteCancelAction).performClick()

        assertFalse(deleted)
    }

    @Test
    fun editor_measurePicker_hiddenForANonDurationTrackingCase() {
        setEditorContent(durationMode = DurationMode.NONE)
        composeTestRule.onNodeWithText(PlainVoice.watchesMetricLabel).assertDoesNotExist()
    }

    @Test
    fun editor_measurePicker_shownForADurationTrackingCase() {
        setEditorContent(durationMode = DurationMode.MANUAL)
        composeTestRule.onNodeWithText(PlainVoice.watchesMetricLabel).assertExists()
    }

    @Test
    fun editor_intensityPicker_hiddenWhenIntensityIsOff() {
        setEditorContent(intensityEnabled = false)
        composeTestRule.onNodeWithText(PlainVoice.watchesIntensityLabel).assertDoesNotExist()
    }

    @Test
    fun editor_intensityPicker_shownWhenIntensityIsOn() {
        setEditorContent(intensityEnabled = true)
        composeTestRule.onNodeWithText(PlainVoice.watchesIntensityLabel).assertExists()
    }

    @Test
    fun editor_switchToQuiet_savesWithNullWindow() {
        var saved: Triple<WatchKind, Int, Int?>? = null
        setEditorContent(onSave = { kind, threshold, windowDays, _, _, _ -> saved = Triple(kind, threshold, windowDays) })

        composeTestRule.onNodeWithText(PlainVoice.watchKindLabel(WatchKind.QUIET)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.watchesQuietLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.watchesSaveButton).performClick()

        assertEquals(WatchKind.QUIET, saved?.first)
        assertEquals(14, saved?.second)
        assertNull(saved?.third)
    }
}
