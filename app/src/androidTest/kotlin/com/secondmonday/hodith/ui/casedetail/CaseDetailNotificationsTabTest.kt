package com.secondmonday.hodith.ui.casedetail

import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.NotificationsUiState
import com.secondmonday.hodith.viewmodel.formatRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val MILLIS_PER_DAY = 86_400_000L
private const val NOW = 40L * MILLIS_PER_DAY

/**
 * Drives [NotificationsTabContent] and [NotificationEditorSheet] directly, stateless, same
 * pattern as `CaseDetailScreenTest`/the retired `NotificationsScreenTest` -- no Hilt, since
 * [CaseDetailScreen]'s bell tab owns its own `hiltViewModel<NotificationsViewModel>()` instance
 * that this test intentionally never reaches (see PROGRESS.md N2's architecture notes).
 */
@UiTest
class CaseDetailNotificationsTabTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun oftenAt(
        id: Long,
        occurredAt: Long,
    ) = testEvent(id = id, caseId = 1L, occurredAt = occurredAt)

    private fun oftenNotification(
        id: Long = 1L,
        threshold: Int = 1,
        windowDays: Int? = 30,
        expectedPer: ExpectedPer = ExpectedPer.WEEK,
        metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity: Int? = null,
        enabled: Boolean = true,
        lastFiredAt: Long? = null,
    ) = NotificationEntity(
        id = id,
        caseId = 1L,
        kind = NotificationKind.OFTEN,
        threshold = threshold,
        windowDays = windowDays,
        expectedPer = expectedPer,
        metric = metric,
        minIntensity = minIntensity,
        enabled = enabled,
        lastFiredAt = lastFiredAt,
    )

    private fun quietNotification(
        id: Long = 2L,
        threshold: Int = 14,
        enabled: Boolean = true,
    ) = NotificationEntity(
        id = id,
        caseId = 1L,
        kind = NotificationKind.QUIET,
        threshold = threshold,
        windowDays = null,
        expectedPer = ExpectedPer.WEEK,
        metric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity = null,
        enabled = enabled,
        lastFiredAt = null,
    )

    private fun setTabContent(
        notifications: List<NotificationEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
        checkInsEnabled: Boolean = true,
        onCheckInToggle: (Boolean) -> Unit = {},
        onSetEnabled: (Long, Boolean) -> Unit = { _, _ -> },
        onCreateRequest: () -> Unit = {},
        onCardClick: (NotificationEntity) -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            NotificationsTabContent(
                uiState =
                    NotificationsUiState(
                        notifications = notifications,
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
        // Empty notification list so the row's Switch is the only toggleable node in the tree.
        setTabContent(notifications = emptyList(), checkInsEnabled = true, onCheckInToggle = { toggled = it })

        composeTestRule.onNodeWithText(PlainVoice.caseCheckInLabel).assertExists()
        composeTestRule.onNode(isToggleable()).performClick()

        assertEquals(false, toggled)
    }

    @Test
    fun emptyState_showsCreateCta_andInvokesOnCreateRequest() {
        var created = false
        setTabContent(notifications = emptyList(), onCreateRequest = { created = true })

        composeTestRule.onNodeWithText(PlainVoice.notificationsEmptyTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsEmptyCta).performClick()

        assertEquals(true, created)
    }

    @Test
    fun oftenCard_showsTitleAndNowLine() {
        val notification = oftenNotification(threshold = 3, windowDays = 30, expectedPer = ExpectedPer.WEEK)
        setTabContent(notifications = listOf(notification), events = emptyList())

        composeTestRule.onNodeWithText(PlainVoice.notificationCardTitleOften(3, ExpectedPer.WEEK)).assertExists()
        val rateLabel = formatRate(0.0, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT)
        composeTestRule.onNodeWithText(PlainVoice.notificationNowLineOften(rateLabel)).assertExists()
    }

    @Test
    fun quietCard_showsTitleAndNowLine() {
        val notification = quietNotification(threshold = 14)
        val events = listOf(oftenAt(1L, NOW - 3 * MILLIS_PER_DAY))
        setTabContent(notifications = listOf(notification), events = events)

        composeTestRule.onNodeWithText(PlainVoice.notificationCardTitleQuiet(14)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationNowLineQuiet(3L)).assertExists()
    }

    @Test
    fun oftenCard_comparisonLine_hiddenBelowTheConfidenceTier() {
        // A single event never clears PRELIMINARY_MIN_EVENTS (5) -- NO_VERDICT, so only the
        // Early-days badge shows, never a tier badge.
        val notification = oftenNotification(threshold = 1, windowDays = 30)
        setTabContent(notifications = listOf(notification), events = listOf(oftenAt(1L, NOW)))

        composeTestRule.onNodeWithText(PlainVoice.expectationEarlyBadgeLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.expectationTierBadgeLabel(ConfidenceTier.PRELIMINARY)).assertDoesNotExist()
    }

    @Test
    fun oftenCard_comparisonLine_shownOnceTheConfidenceTierClears() {
        // 5 events across a 30-day window clears both PRELIMINARY_MIN_EVENTS (5) and
        // PRELIMINARY_MIN_DAYS (14) -- a real (non-NO_VERDICT) tier badge shows.
        val notification = oftenNotification(threshold = 1, windowDays = 30)
        val events = (0 until 5).map { i -> oftenAt(i.toLong(), NOW - i * MILLIS_PER_DAY) }
        setTabContent(notifications = listOf(notification), events = events)

        composeTestRule
            .onNodeWithText(PlainVoice.expectationTierBadgeLabel(ConfidenceTier.PRELIMINARY))
            .assertExists()
        composeTestRule.onNodeWithText(PlainVoice.expectationEarlyBadgeLabel).assertDoesNotExist()
    }

    @Test
    fun disabledCard_stillShowsNowLine() {
        val notification = oftenNotification(threshold = 3, enabled = false)
        setTabContent(notifications = listOf(notification), events = emptyList())

        val rateLabel = formatRate(0.0, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT)
        composeTestRule.onNodeWithText(PlainVoice.notificationNowLineOften(rateLabel)).assertExists()
    }

    @Test
    fun card_toggleSwitch_invokesOnSetEnabled() {
        val notification = oftenNotification(id = 7L, threshold = 3, enabled = true)
        var toggled: Pair<Long, Boolean>? = null
        setTabContent(notifications = listOf(notification), onSetEnabled = { id, enabled -> toggled = id to enabled })

        val title = PlainVoice.notificationCardTitleOften(3, ExpectedPer.WEEK)
        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationToggleDescription(title)).performClick()

        assertEquals(7L to false, toggled)
    }

    @Test
    fun card_tap_invokesOnCardClick() {
        val notification = oftenNotification(id = 9L, threshold = 3)
        var clicked: NotificationEntity? = null
        setTabContent(notifications = listOf(notification), onCardClick = { clicked = it })

        composeTestRule.onNodeWithText(PlainVoice.notificationCardTitleOften(3, ExpectedPer.WEEK)).performClick()

        assertEquals(notification, clicked)
    }

    // ---- NotificationEditorSheet ----

    private fun setEditorContent(
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
        editing: NotificationEntity? = null,
        onDismiss: () -> Unit = {},
        onSave: (NotificationKind, Int, Int?, ExpectedPer, VerdictMetric, Int?) -> Unit = { _, _, _, _, _, _ -> },
        onDelete: () -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            NotificationEditorSheet(
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
        var saved: Triple<NotificationKind, Int, Int?>? = null
        setEditorContent(onSave = { kind, threshold, windowDays, _, _, _ -> saved = Triple(kind, threshold, windowDays) })

        composeTestRule.onNodeWithText(PlainVoice.notificationsCreateTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsSaveButton).performClick()

        assertEquals(NotificationKind.OFTEN, saved?.first)
        assertEquals(7, saved?.third)
    }

    @Test
    fun editor_editingExisting_prefillsTitleAndSavesUpdatedFields() {
        val existing = oftenNotification(id = 3L, threshold = 5, windowDays = 30, expectedPer = ExpectedPer.MONTH)
        var saved: Triple<Int, Int?, ExpectedPer>? = null
        setEditorContent(
            editing = existing,
            onSave = { _, threshold, windowDays, expectedPer, _, _ -> saved = Triple(threshold, windowDays, expectedPer) },
        )

        composeTestRule.onNodeWithText(PlainVoice.notificationsEditTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsSaveButton).performClick()

        assertEquals(5, saved?.first)
        assertEquals(30, saved?.second)
        assertEquals(ExpectedPer.MONTH, saved?.third)
    }

    @Test
    fun editor_deleteButton_onlyShownWhenEditing() {
        setEditorContent(editing = null)
        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationDeleteDescription(""), substring = true).assertDoesNotExist()
    }

    @Test
    fun editor_delete_opensConfirmDialog_confirmInvokesCallback() {
        val existing = quietNotification(id = 4L, threshold = 14)
        var deleted = false
        setEditorContent(editing = existing, onDelete = { deleted = true })

        val summary = PlainVoice.notificationSummary(existing.kind, existing.threshold, existing.windowDays)
        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteConfirmAction).performClick()

        assertEquals(true, deleted)
    }

    @Test
    fun editor_delete_cancelDoesNotInvokeCallback() {
        val existing = quietNotification(id = 4L, threshold = 14)
        var deleted = false
        setEditorContent(editing = existing, onDelete = { deleted = true })

        val summary = PlainVoice.notificationSummary(existing.kind, existing.threshold, existing.windowDays)
        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteCancelAction).performClick()

        assertFalse(deleted)
    }

    @Test
    fun editor_measurePicker_hiddenForANonDurationTrackingCase() {
        setEditorContent(durationMode = DurationMode.NONE)
        composeTestRule.onNodeWithText(PlainVoice.notificationsMetricLabel).assertDoesNotExist()
    }

    @Test
    fun editor_measurePicker_shownForADurationTrackingCase() {
        setEditorContent(durationMode = DurationMode.MANUAL)
        composeTestRule.onNodeWithText(PlainVoice.notificationsMetricLabel).assertExists()
    }

    @Test
    fun editor_intensityPicker_hiddenWhenIntensityIsOff() {
        setEditorContent(intensityEnabled = false)
        composeTestRule.onNodeWithText(PlainVoice.notificationsIntensityLabel).assertDoesNotExist()
    }

    @Test
    fun editor_intensityPicker_shownWhenIntensityIsOn() {
        setEditorContent(intensityEnabled = true)
        composeTestRule.onNodeWithText(PlainVoice.notificationsIntensityLabel).assertExists()
    }

    @Test
    fun editor_switchToQuiet_savesWithNullWindow() {
        var saved: Triple<NotificationKind, Int, Int?>? = null
        setEditorContent(onSave = { kind, threshold, windowDays, _, _, _ -> saved = Triple(kind, threshold, windowDays) })

        composeTestRule.onNodeWithText(PlainVoice.notificationKindLabel(NotificationKind.QUIET)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationsQuietLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsSaveButton).performClick()

        assertEquals(NotificationKind.QUIET, saved?.first)
        assertEquals(14, saved?.second)
        assertNull(saved?.third)
    }
}
