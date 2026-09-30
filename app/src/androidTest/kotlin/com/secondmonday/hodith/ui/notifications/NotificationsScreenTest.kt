package com.secondmonday.hodith.ui.notifications

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.overlapsRect
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.NotificationRow
import com.secondmonday.hodith.viewmodel.NotificationsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * [NotificationsScreen] is a stateless composable driven entirely by plain data + callbacks,
 * same pattern as `ArchivedCasesScreenTest`/`CaseDetailScreenTest` — no Hilt/Activity/Room needed.
 */
@UiTest
class NotificationsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val oftenRow =
        NotificationRow(id = 1L, kind = NotificationKind.OFTEN, threshold = 5, windowDays = 7, enabled = true, firedDaysAgo = null)

    private fun setContent(
        uiState: NotificationsUiState = NotificationsUiState(notifications = listOf(oftenRow), isLoading = false),
        onCreateNotification: (NotificationKind, Int, Int?) -> Unit = { _, _, _ -> },
        onSetEnabled: (Long, Boolean) -> Unit = { _, _ -> },
        onDeleteNotification: (Long) -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            NotificationsScreen(
                uiState = uiState,
                onBack = {},
                onCreateNotification = onCreateNotification,
                onSetEnabled = onSetEnabled,
                onDeleteNotification = onDeleteNotification,
            )
        }
    }

    @Test
    fun emptyState_showsWhenNoNotifications() {
        setContent(uiState = NotificationsUiState(notifications = emptyList(), isLoading = false))

        composeTestRule.onNodeWithText(PlainVoice.notificationsEmptyTitle).assertExists()
    }

    @Test
    fun row_showsSummaryAndKindLabel() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.notificationSummary(NotificationKind.OFTEN, 5, 7)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationKindLabel(NotificationKind.OFTEN)).assertExists()
    }

    @Test
    fun row_firedBadge_showsOnlyWhenNotificationHasFired() {
        setContent(uiState = NotificationsUiState(notifications = listOf(oftenRow.copy(firedDaysAgo = 3L)), isLoading = false))

        composeTestRule.onNodeWithText(PlainVoice.notificationFiredAgo(3L)).assertExists()
    }

    @Test
    fun toggle_invokesOnSetEnabled() {
        val summary = PlainVoice.notificationSummary(NotificationKind.OFTEN, 5, 7)
        var toggled: Pair<Long, Boolean>? = null
        setContent(onSetEnabled = { id, enabled -> toggled = id to enabled })

        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationToggleDescription(summary)).performClick()

        assertEquals(1L to false, toggled)
    }

    @Test
    fun delete_opensConfirmDialog_confirmInvokesCallback() {
        val summary = PlainVoice.notificationSummary(NotificationKind.OFTEN, 5, 7)
        var deletedId: Long? = null
        setContent(onDeleteNotification = { deletedId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteConfirmAction).performClick()

        assertEquals(1L, deletedId)
    }

    @Test
    fun delete_cancelDoesNotInvokeCallback() {
        val summary = PlainVoice.notificationSummary(NotificationKind.OFTEN, 5, 7)
        var deletedId: Long? = null
        setContent(onDeleteNotification = { deletedId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.notificationDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsDeleteCancelAction).performClick()

        assertNull(deletedId)
    }

    @Smoke
    @Test
    fun create_fromEmptyState_defaultOften_savesWithDefaultThresholdAndWindow() {
        var saved: Triple<NotificationKind, Int, Int?>? = null
        setContent(
            uiState = NotificationsUiState(notifications = emptyList(), isLoading = false),
            onCreateNotification = { kind, threshold, windowDays -> saved = Triple(kind, threshold, windowDays) },
        )

        composeTestRule.onNodeWithText(PlainVoice.notificationsEmptyCta).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationsCreateTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsSaveButton).performClick()

        assertEquals(Triple(NotificationKind.OFTEN, 5, 7), saved)
    }

    @Test
    fun create_switchToQuiet_savesWithNullWindow() {
        var saved: Triple<NotificationKind, Int, Int?>? = null
        setContent(
            uiState = NotificationsUiState(notifications = emptyList(), isLoading = false),
            onCreateNotification = { kind, threshold, windowDays -> saved = Triple(kind, threshold, windowDays) },
        )

        composeTestRule.onNodeWithText(PlainVoice.notificationsEmptyCta).performClick()
        composeTestRule.onNodeWithText(PlainVoice.notificationKindLabel(NotificationKind.QUIET)).performClick()

        // Confirm the form actually switched to the Quiet layout before saving.
        composeTestRule.onNodeWithText(PlainVoice.notificationsQuietLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.notificationsWindowLabel).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.notificationsSaveButton).performClick()

        assertEquals(NotificationKind.QUIET, saved?.first)
        assertEquals(14, saved?.second)
        assertNull(saved?.third)
    }

    @Test
    fun fullScreenNotificationList_lastRowsToggle_doesNotOverlapFab() {
        val rows = (1..30).map { i -> oftenRow.copy(id = i.toLong(), threshold = i) }
        setContent(uiState = NotificationsUiState(notifications = rows, isLoading = false))
        // The LazyColumn only composes visible rows, so scroll its container to the last index
        // before looking up that row's node — it doesn't exist in the semantics tree until then.
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(rows.lastIndex)
        val lastSummary = PlainVoice.notificationSummary(NotificationKind.OFTEN, rows.last().threshold, rows.last().windowDays)

        val fabBounds =
            composeTestRule.onNodeWithContentDescription(PlainVoice.notificationsFabDescription).fetchSemanticsNode().boundsInRoot
        val lastToggleBounds =
            composeTestRule
                .onNodeWithContentDescription(PlainVoice.notificationToggleDescription(lastSummary))
                .fetchSemanticsNode()
                .boundsInRoot

        assertFalse(fabBounds.overlapsRect(lastToggleBounds))
    }
}
