package com.secondmonday.hodith.ui.triggers

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.TriggerRow
import com.secondmonday.hodith.viewmodel.TriggersUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * [TriggersScreen] is a stateless composable driven entirely by plain data + callbacks,
 * same pattern as `ArchivedCasesScreenTest`/`CaseDetailScreenTest` — no Hilt/Activity/Room needed.
 */
@UiTest
class TriggersScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val atLeastRow =
        TriggerRow(id = 1L, kind = TriggerKind.AT_LEAST, threshold = 5, windowDays = 7, enabled = true, firedDaysAgo = null)

    private fun setContent(
        uiState: TriggersUiState = TriggersUiState(triggers = listOf(atLeastRow), isLoading = false),
        onCreateTrigger: (TriggerKind, Int, Int?) -> Unit = { _, _, _ -> },
        onSetEnabled: (Long, Boolean) -> Unit = { _, _ -> },
        onDeleteTrigger: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                TriggersScreen(
                    uiState = uiState,
                    onBack = {},
                    onCreateTrigger = onCreateTrigger,
                    onSetEnabled = onSetEnabled,
                    onDeleteTrigger = onDeleteTrigger,
                )
            }
        }
    }

    @Test
    fun emptyState_showsWhenNoTriggers() {
        setContent(uiState = TriggersUiState(triggers = emptyList(), isLoading = false))

        composeTestRule.onNodeWithText(PlainVoice.triggersEmptyTitle).assertExists()
    }

    @Test
    fun row_showsSummaryAndKindLabel() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.triggerSummary(TriggerKind.AT_LEAST, 5, 7)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.triggerKindLabel(TriggerKind.AT_LEAST)).assertExists()
    }

    @Test
    fun row_firedBadge_showsOnlyWhenTriggerHasFired() {
        setContent(uiState = TriggersUiState(triggers = listOf(atLeastRow.copy(firedDaysAgo = 3L)), isLoading = false))

        composeTestRule.onNodeWithText(PlainVoice.triggerFiredAgo(3L)).assertExists()
    }

    @Test
    fun toggle_invokesOnSetEnabled() {
        val summary = PlainVoice.triggerSummary(TriggerKind.AT_LEAST, 5, 7)
        var toggled: Pair<Long, Boolean>? = null
        setContent(onSetEnabled = { id, enabled -> toggled = id to enabled })

        composeTestRule.onNodeWithContentDescription(PlainVoice.triggerToggleDescription(summary)).performClick()

        assertEquals(1L to false, toggled)
    }

    @Test
    fun delete_opensConfirmDialog_confirmInvokesCallback() {
        val summary = PlainVoice.triggerSummary(TriggerKind.AT_LEAST, 5, 7)
        var deletedId: Long? = null
        setContent(onDeleteTrigger = { deletedId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.triggerDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.triggersDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.triggersDeleteConfirmAction).performClick()

        assertEquals(1L, deletedId)
    }

    @Test
    fun delete_cancelDoesNotInvokeCallback() {
        val summary = PlainVoice.triggerSummary(TriggerKind.AT_LEAST, 5, 7)
        var deletedId: Long? = null
        setContent(onDeleteTrigger = { deletedId = it })

        composeTestRule.onNodeWithContentDescription(PlainVoice.triggerDeleteDescription(summary)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.triggersDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.triggersDeleteCancelAction).performClick()

        assertNull(deletedId)
    }

    @Smoke
    @Test
    fun create_fromEmptyState_defaultAtLeast_savesWithDefaultThresholdAndWindow() {
        var saved: Triple<TriggerKind, Int, Int?>? = null
        setContent(
            uiState = TriggersUiState(triggers = emptyList(), isLoading = false),
            onCreateTrigger = { kind, threshold, windowDays -> saved = Triple(kind, threshold, windowDays) },
        )

        composeTestRule.onNodeWithText(PlainVoice.triggersEmptyCta).performClick()
        composeTestRule.onNodeWithText(PlainVoice.triggersCreateTitle).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.triggersSaveButton).performClick()

        assertEquals(Triple(TriggerKind.AT_LEAST, 5, 7), saved)
    }

    @Test
    fun create_switchToSilentFor_savesWithNullWindow() {
        var saved: Triple<TriggerKind, Int, Int?>? = null
        setContent(
            uiState = TriggersUiState(triggers = emptyList(), isLoading = false),
            onCreateTrigger = { kind, threshold, windowDays -> saved = Triple(kind, threshold, windowDays) },
        )

        composeTestRule.onNodeWithText(PlainVoice.triggersEmptyCta).performClick()
        composeTestRule.onNodeWithText(PlainVoice.triggerKindLabel(TriggerKind.SILENT_FOR)).performClick()

        // Confirm the form actually switched to the Silent-For layout before saving.
        composeTestRule.onNodeWithText(PlainVoice.triggersSilentLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.triggersWindowLabel).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.triggersSaveButton).performClick()

        assertEquals(TriggerKind.SILENT_FOR, saved?.first)
        assertEquals(14, saved?.second)
        assertNull(saved?.third)
    }
}
