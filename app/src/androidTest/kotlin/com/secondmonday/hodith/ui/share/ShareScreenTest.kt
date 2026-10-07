package com.secondmonday.hodith.ui.share

import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HistoryShareSelection
import com.secondmonday.hodith.viewmodel.HistoryShareUiState
import com.secondmonday.hodith.viewmodel.ShareUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

/** 12 events, 5 days apart: enough for the Insights picker to offer its sections. */
private fun events(): List<EventWithTags> =
    (0..55L step 5).map { day -> EventWithTags(testEvent(caseId = 1L, occurredAt = millisAtDay(day)), emptyList()) }

/**
 * The Share screen's host: title, tabs, the shared name field, the preview heading, and which callback each tab's share
 * button fires. Tab-level content (the picker, the History controls) is covered by [InsightShareTabTest] and [HistoryShareTabTest].
 */
@UiTest
class ShareScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        events: List<EventWithTags> = emptyList(),
        onInsightShare: () -> Unit = {},
        onHistoryShare: () -> Unit = {},
    ) {
        val case = testCase(id = 1L)
        val now = millisAtDay(60)
        composeTestRule.setHodithContent {
            ShareScreen(
                insightState = ShareUiState(case = case, events = events, isLoading = false),
                historyState =
                    HistoryShareUiState(
                        case = case,
                        events = events,
                        selection = HistoryShareSelection(dateTo = now),
                        isLoading = false,
                    ),
                now = now,
                graphicsLayer = rememberGraphicsLayer(),
                onBack = {},
                onSectionToggle = { _, _ -> },
                onSectionMove = { _, _, _ -> },
                onInsightShareClick = onInsightShare,
                onDateFromPicked = {},
                onDateToPicked = {},
                onFieldToggle = { _, _ -> },
                onHistorySortOrderSelect = {},
                onHistoryShareClick = onHistoryShare,
            )
        }
    }

    private fun openTab(label: String) {
        composeTestRule.onNodeWithText(label).performClick()
    }

    private fun nameFieldText(): String =
        composeTestRule
            .onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    private fun shareButton() = composeTestRule.onNode(hasText(PlainVoice.shareOpenDescription) and hasClickAction())

    @Smoke
    @Test
    fun screen_isTitledShare_withSummaryOpenFirst() {
        setContent()

        // In Plain the title and the Share button both read "Share".
        composeTestRule.onAllNodesWithText(PlainVoice.shareScreenTitle).assertCountEquals(2)
        composeTestRule.onNodeWithText(PlainVoice.shareTabSummaryLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareTabInsightsLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareTabHistoryLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.sharePreviewLabel).assertExists()
    }

    @Test
    fun previewHeading_isOnEveryTab() {
        setContent(events = events())

        listOf(PlainVoice.shareTabSummaryLabel, PlainVoice.shareTabInsightsLabel, PlainVoice.shareTabHistoryLabel).forEach { label ->
            openTab(label)
            composeTestRule.onNodeWithText(PlainVoice.sharePreviewLabel).assertExists()
        }
    }

    @Test
    fun includeHeading_isOnInsightsAndHistory_notOnSummary() {
        setContent(events = events())

        composeTestRule.onAllNodesWithText(PlainVoice.shareSectionsPickerLabel).assertCountEquals(0)
        openTab(PlainVoice.shareTabInsightsLabel)
        composeTestRule.onAllNodesWithText(PlainVoice.shareSectionsPickerLabel).assertCountEquals(1)
        openTab(PlainVoice.shareTabHistoryLabel)
        composeTestRule.onAllNodesWithText(PlainVoice.shareSectionsPickerLabel).assertCountEquals(1)
    }

    @Test
    fun nameField_typedOnSummary_carriesAcrossEveryTab() {
        setContent(events = events())

        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Sam")

        openTab(PlainVoice.shareTabInsightsLabel)
        assertEquals("Sam", nameFieldText())
        openTab(PlainVoice.shareTabHistoryLabel)
        assertEquals("Sam", nameFieldText())
        openTab(PlainVoice.shareTabSummaryLabel)
        assertEquals("Sam", nameFieldText())
    }

    @Test
    fun shareButton_onSummaryAndInsights_firesInsightShareOnly() {
        var insightShares = 0
        var historyShares = 0
        setContent(events = events(), onInsightShare = { insightShares++ }, onHistoryShare = { historyShares++ })

        shareButton().performScrollTo().performClick()
        openTab(PlainVoice.shareTabInsightsLabel)
        shareButton().performScrollTo().performClick()

        assertEquals(2, insightShares)
        assertEquals(0, historyShares)
    }

    @Test
    fun shareButton_onHistory_firesHistoryShareOnly() {
        var insightShares = 0
        var historyShares = 0
        setContent(events = events(), onInsightShare = { insightShares++ }, onHistoryShare = { historyShares++ })

        openTab(PlainVoice.shareTabHistoryLabel)
        shareButton().performScrollTo().performClick()

        assertEquals(0, insightShares)
        assertEquals(1, historyShares)
    }
}
