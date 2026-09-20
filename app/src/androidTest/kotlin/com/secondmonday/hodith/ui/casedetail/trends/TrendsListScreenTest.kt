package com.secondmonday.hodith.ui.casedetail.trends

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [TrendsListScreen] is a stateless composable driven by plain data + callbacks, same pattern as
 * [com.secondmonday.hodith.ui.logsheet.LogDetailScreenTest]. Covers the title naming both the
 * section and the Case, the back arrow, that every (already domain-capped) finding renders as its
 * own plank, and the shared info action -- the one place this section keeps a tap-to-open
 * explanation, per the Insights tab's own compact card dropping it (spec §10, Story C T1).
 */
@UiTest
class TrendsListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val findings =
        listOf(
            TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 9, 3.2, 5.8),
            TrendFinding(TrendFindingKind.STREAK_SHIFT, ShiftDirection.DOWN, TrendReliability.PATTERN, 7, 4.0, 2.0),
        )

    private fun setContent(
        findings: List<TrendFinding> = this.findings,
        caseIcon: String = "☕",
        caseName: String = "Coffee",
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                TrendsListScreen(findings = findings, caseIcon = caseIcon, caseName = caseName, onBack = onBack)
            }
        }
    }

    @Test
    fun title_namesBothTheSectionAndTheCase() {
        setContent(caseIcon = "☕", caseName = "Coffee")

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrends).assertExists()
        composeTestRule.onNodeWithText("☕ Coffee").assertExists()
    }

    @Test
    fun backArrow_invokesOnBack() {
        var backed = false
        setContent(onBack = { backed = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.backButtonDescription).performClick()

        assertTrue(backed)
    }

    @Test
    fun everyFinding_rendersItsOwnPlank_withReliabilityTag() {
        setContent()

        composeTestRule
            .onNodeWithText(PlainVoice.insightsGapShiftSentence(ShiftDirection.UP, "3.2 days", "5.8 days"))
            .assertExists()
        composeTestRule
            .onNodeWithText(PlainVoice.insightsStreakShiftSentence(ShiftDirection.DOWN, "4 days", "2 days"))
            .assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReliabilityHintLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReliabilityPatternLabel).assertExists()
    }

    @Test
    fun infoAction_opensSharedExplanationDialog() {
        setContent()

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseSectionInfoDescription).performClick()

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendsInfoTitle).assertExists()
    }

    @Test
    fun wentQuietFinding_rendersItsOwnPlank() {
        val wentQuiet = TrendFinding(TrendFindingKind.WENT_QUIET, ShiftDirection.UP, TrendReliability.HINT, 6, 5.0, 20.0)
        setContent(findings = listOf(wentQuiet))

        composeTestRule
            .onNodeWithText(PlainVoice.insightsWentQuietSentence(currentGapLabel = "20 days", longestPastGapLabel = "5 days"))
            .assertExists()
    }

    @Test
    fun recurrenceShapeFinding_rendersItsOwnPlank() {
        val recurrenceShape = TrendFinding(TrendFindingKind.RECURRENCE_SHAPE, ShiftDirection.DOWN, TrendReliability.HINT, 10, 7.0, 0.0)
        setContent(findings = listOf(recurrenceShape))

        composeTestRule
            .onNodeWithText(PlainVoice.insightsRecurrenceShapeSentence(ShiftDirection.DOWN, thresholdLabel = "7 days", shareLabel = "0%"))
            .assertExists()
    }
}
