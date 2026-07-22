package com.secondmonday.hodith.ui.archivedcases

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.SeriousVoice
import com.secondmonday.hodith.viewmodel.ArchivedCaseRow
import com.secondmonday.hodith.viewmodel.ArchivedCasesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * [ArchivedCasesScreen] is a stateless composable driven entirely by plain data + callbacks,
 * so these tests exercise it directly with `createComposeRule()`, same pattern as
 * `HomeScreenTest`/`CaseDetailScreenTest` — no Hilt/Activity/Room needed.
 */
@UiTest
class ArchivedCasesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val row = ArchivedCaseRow(caseId = 1L, icon = "🐛", name = "Old Case", eventCount = 5)

    private fun setContent(
        uiState: ArchivedCasesUiState = ArchivedCasesUiState(cases = listOf(row), isLoading = false),
        onUnarchive: (Long) -> Unit = {},
        onDeleteForever: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides SeriousVoice) {
                ArchivedCasesScreen(
                    uiState = uiState,
                    onBack = {},
                    onUnarchive = onUnarchive,
                    onDeleteForever = onDeleteForever,
                )
            }
        }
    }

    @Test
    fun emptyState_showsWhenNoArchivedCases() {
        setContent(uiState = ArchivedCasesUiState(cases = emptyList(), isLoading = false))

        composeTestRule.onNodeWithText(SeriousVoice.archivedCasesEmptyState).assertExists()
    }

    @Test
    fun row_showsNameAndEventCount() {
        setContent()

        composeTestRule.onNodeWithText(row.name).assertExists()
        composeTestRule.onNodeWithText(SeriousVoice.archivedCaseEventCount(row.eventCount)).assertExists()
    }

    @Smoke
    @Test
    fun unarchive_firesImmediately_noDialog() {
        var unarchivedId: Long? = null
        setContent(onUnarchive = { unarchivedId = it })

        composeTestRule.onNodeWithContentDescription(SeriousVoice.unarchiveCaseDescription(row.name)).performClick()

        assertEquals(row.caseId, unarchivedId)
    }

    @Test
    fun delete_opensConfirmDialog_confirmInvokesCallback() {
        var deletedId: Long? = null
        setContent(onDeleteForever = { deletedId = it })

        composeTestRule.onNodeWithContentDescription(SeriousVoice.deleteCaseForeverDescription(row.name)).performClick()
        composeTestRule.onNodeWithText(SeriousVoice.deleteCaseForeverConfirmTitle).assertExists()
        composeTestRule.onNodeWithText(SeriousVoice.deleteCaseForeverConfirmAction).performClick()

        assertEquals(row.caseId, deletedId)
    }

    @Test
    fun delete_cancelDoesNotInvokeCallback() {
        var deletedId: Long? = null
        setContent(onDeleteForever = { deletedId = it })

        composeTestRule.onNodeWithContentDescription(SeriousVoice.deleteCaseForeverDescription(row.name)).performClick()
        composeTestRule.onNodeWithText(SeriousVoice.deleteCaseForeverCancelAction).performClick()

        assertNull(deletedId)
    }
}
