package com.secondmonday.hodith.ui.casedetail.trends

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.casedetail.formatApproximateMonth
import com.secondmonday.hodith.ui.casedetail.formatDays
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.BrightVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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
        theme: AppTheme = AppTheme.PLAIN,
    ) {
        composeTestRule.setHodithContent(theme = theme) {
            TrendsListScreen(findings = findings, caseIcon = caseIcon, caseName = caseName, onBack = onBack)
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

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendHeadline(findings[0], bucketPhrase = "")).assertExists()
        composeTestRule.onNodeWithText(formatDays(5.8)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReferenceLine(formatDays(3.2))).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsTrendHeadline(findings[1], bucketPhrase = "")).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReliabilityHintLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReliabilityPatternLabel).assertExists()
    }

    @Test
    fun everyFinding_rendersItsOwnPlank_underBrightTheme() {
        // TrendFindingPlank renders Card-wrapped under Bright instead of Plain's flat content --
        // confirms real finding text still shows through that structural branch.
        setContent(theme = AppTheme.BRIGHT)

        composeTestRule.onNodeWithText(BrightVoice.insightsTrendHeadline(findings[0], bucketPhrase = "")).assertExists()
        composeTestRule.onNodeWithText(formatDays(5.8)).assertExists()
        composeTestRule.onNodeWithText(BrightVoice.trendReferenceLine(formatDays(3.2))).assertExists()
        composeTestRule.onNodeWithText(BrightVoice.insightsTrendHeadline(findings[1], bucketPhrase = "")).assertExists()
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

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendHeadline(wentQuiet, bucketPhrase = "")).assertExists()
    }

    @Test
    fun recurrenceShapeFinding_rendersItsOwnPlank() {
        val recurrenceShape = TrendFinding(TrendFindingKind.RECURRENCE_SHAPE, ShiftDirection.DOWN, TrendReliability.HINT, 10, 7.0, 0.0)
        setContent(findings = listOf(recurrenceShape))

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendHeadline(recurrenceShape, bucketPhrase = "")).assertExists()
        composeTestRule.onNodeWithText("0%").assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendRecurrenceDetailLabel(formatDays(7.0))).assertExists()
    }

    @Test
    fun tagOutcomeFinding_rendersItsOwnPlank_withPatternTag() {
        val tagOutcome =
            TrendFinding(
                kind = TrendFindingKind.TAG_OUTCOME,
                direction = ShiftDirection.DOWN,
                reliability = TrendReliability.PATTERN,
                sampleCount = 50,
                priorValue = 90.0,
                recentValue = 45.0,
                tagName = "decaf",
                outcome = TagOutcome.DURATION,
            )
        setContent(findings = listOf(tagOutcome))

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendHeadline(tagOutcome, bucketPhrase = "")).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendRelativeChange("50%")).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReliabilityPatternLabel).assertExists()
    }

    @Test
    fun changePointFinding_rendersItsOwnPlank() {
        val changePoint =
            TrendFinding(
                kind = TrendFindingKind.CHANGE_POINT,
                direction = ShiftDirection.DOWN,
                reliability = TrendReliability.PATTERN,
                sampleCount = 18,
                priorValue = 10.0,
                recentValue = 4.0,
                changePointDate = LocalDate.of(2026, 7, 22),
            )
        setContent(findings = listOf(changePoint))

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendHeadline(changePoint, bucketPhrase = "")).assertExists()
        composeTestRule
            .onNodeWithText(PlainVoice.trendChangePointDetail(formatApproximateMonth(LocalDate.of(2026, 7, 22))))
            .assertExists()
    }
}
