package com.secondmonday.hodith.ui.settings.tags

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.TAG_FILTER_THRESHOLD
import com.secondmonday.hodith.domain.TagSummary
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.ManageTagsUiState
import com.secondmonday.hodith.viewmodel.PendingTagAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Stateless screen test, same shape as [com.secondmonday.hodith.ui.settings.SettingsScreenTest]:
 * the ViewModel's filter threshold and warning resolution are unit-tested, so these cover what the
 * screen renders from that state and which callbacks each control fires.
 */
@UiTest
class ManageTagsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val voice = PlainVoice

    private fun summary(
        id: Long,
        name: String,
        count: Int,
    ) = TagSummary(TagEntity(id = id, name = name), eventCount = count)

    private fun setContent(
        uiState: ManageTagsUiState,
        onRenameRequested: (TagSummary, String) -> Unit = { _, _ -> },
        onDeleteRequested: (TagSummary) -> Unit = {},
        onConfirmPending: () -> Unit = {},
        onDismissPending: () -> Unit = {},
        onQueryChange: (String) -> Unit = {},
    ) {
        composeTestRule.setHodithContent(theme = AppTheme.PLAIN) {
            ManageTagsScreen(
                uiState = uiState,
                onBack = {},
                onQueryChange = onQueryChange,
                onRenameRequested = onRenameRequested,
                onDeleteRequested = onDeleteRequested,
                onConfirmPending = onConfirmPending,
                onDismissPending = onDismissPending,
            )
        }
    }

    @Test
    fun emptyState_showsWhenThereAreNoTags() {
        setContent(ManageTagsUiState(isLoading = false))

        composeTestRule.onNodeWithText(voice.manageTagsEmptyState).assertExists()
    }

    @Test
    fun filterField_isAbsentAtTheThreshold() {
        val tags = (1..TAG_FILTER_THRESHOLD).map { summary(it.toLong(), "tag$it", 0) }
        setContent(ManageTagsUiState(isLoading = false, tags = tags, visibleTags = tags, showFilter = false))

        composeTestRule.onNodeWithText(voice.manageTagsFilterPlaceholder).assertDoesNotExist()
    }

    @Test
    fun filterField_isShownAboveTheThreshold_andReportsTyping() {
        val tags = (1..TAG_FILTER_THRESHOLD + 1).map { summary(it.toLong(), "tag$it", 0) }
        var typed = ""
        setContent(
            ManageTagsUiState(isLoading = false, tags = tags, visibleTags = tags, showFilter = true),
            onQueryChange = { typed = it },
        )

        composeTestRule.onNode(hasSetTextAction()).performTextInput("c")

        assertEquals("c", typed)
    }

    @Test
    fun noMatchesMessage_showsWhenTheFilterHidesEveryTag() {
        val tags = listOf(summary(1, "coffee", 2))
        setContent(ManageTagsUiState(isLoading = false, tags = tags, visibleTags = emptyList(), showFilter = true, query = "zzz"))

        composeTestRule.onNodeWithText(voice.manageTagsNoMatches).assertExists()
    }

    @Test
    fun rows_showNameCountAndBothControls() {
        setContent(
            ManageTagsUiState(isLoading = false, tags = listOf(summary(1, "coffee", 1)), visibleTags = listOf(summary(1, "coffee", 1))),
        )

        composeTestRule.onNodeWithText("coffee").assertExists()
        composeTestRule.onNodeWithText(voice.manageTagsEventCount(1)).assertExists()
        composeTestRule.onNodeWithContentDescription(voice.manageTagsEditDescription("coffee")).assertExists()
        composeTestRule.onNodeWithContentDescription(voice.manageTagsDeleteDescription("coffee")).assertExists()
    }

    @Test
    fun deleteControl_opensWarning_andConfirmFiresOnlyOnConfirm() {
        val coffee = summary(1, "coffee", 3)
        var confirmed = false
        setContent(
            ManageTagsUiState(
                isLoading = false,
                tags = listOf(coffee),
                visibleTags = listOf(coffee),
                pending = PendingTagAction.Delete(coffee.tag, eventCount = 3),
            ),
            onConfirmPending = { confirmed = true },
        )

        composeTestRule.onNodeWithText(voice.manageTagsDeleteConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(voice.manageTagsDeleteConfirmBody("coffee", 3)).assertExists()

        composeTestRule.onNodeWithText(voice.manageTagsDeleteConfirmAction).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun mergeWarning_statesTheOverlapCount() {
        val espresso = summary(1, "espresso", 4)
        val coffee = summary(2, "coffee", 9)
        setContent(
            ManageTagsUiState(
                isLoading = false,
                tags = listOf(coffee, espresso),
                visibleTags = listOf(coffee, espresso),
                pending =
                    PendingTagAction.Merge(
                        tag = espresso.tag,
                        target = coffee.tag,
                        sourceEventCount = 4,
                        overlapCount = 2,
                    ),
            ),
        )

        composeTestRule.onNodeWithText(voice.manageTagsMergeConfirmTitle).assertExists()
        composeTestRule
            .onNodeWithText(voice.manageTagsMergeConfirmBody("espresso", "coffee", sourceEventCount = 4, overlapCount = 2))
            .assertExists()
    }

    @Test
    fun cancelOnAWarning_dismissesWithoutConfirming() {
        val coffee = summary(1, "coffee", 1)
        var dismissed = false
        var confirmed = false
        setContent(
            ManageTagsUiState(
                isLoading = false,
                tags = listOf(coffee),
                visibleTags = listOf(coffee),
                pending = PendingTagAction.Delete(coffee.tag, eventCount = 1),
            ),
            onConfirmPending = { confirmed = true },
            onDismissPending = { dismissed = true },
        )

        composeTestRule.onNodeWithText(voice.manageTagsCancelAction).performClick()

        assertTrue(dismissed)
        assertEquals(false, confirmed)
    }

    @Test
    fun editControl_opensRenameDialog_andSaveRequestsTheTypedName() {
        val coffee = summary(1, "coffee", 2)
        var requested: Pair<TagSummary, String>? = null
        setContent(
            ManageTagsUiState(isLoading = false, tags = listOf(coffee), visibleTags = listOf(coffee)),
            onRenameRequested = { tag, name -> requested = tag to name },
        )

        composeTestRule.onNodeWithContentDescription(voice.manageTagsEditDescription("coffee")).performClick()
        composeTestRule.onNodeWithText(voice.manageTagsRenameDialogTitle).assertExists()
        composeTestRule.onNode(hasSetTextAction() and hasText("coffee")).performTextReplacement("espresso")
        composeTestRule.onNodeWithText(voice.manageTagsRenameSaveAction).performClick()

        assertEquals(coffee to "espresso", requested)
    }

    @Test
    fun renameSave_isDisabledForABlankName() {
        val coffee = summary(1, "coffee", 2)
        setContent(ManageTagsUiState(isLoading = false, tags = listOf(coffee), visibleTags = listOf(coffee)))

        composeTestRule.onNodeWithContentDescription(voice.manageTagsEditDescription("coffee")).performClick()
        composeTestRule.onNode(hasSetTextAction() and hasText("coffee")).performTextReplacement("   ")

        composeTestRule.onNodeWithText(voice.manageTagsRenameSaveAction).assertIsNotEnabled()
    }
}
