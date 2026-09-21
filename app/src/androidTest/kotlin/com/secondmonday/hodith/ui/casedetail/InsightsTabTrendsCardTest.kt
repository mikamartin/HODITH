package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.GapsDisplay
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.RhythmCellDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.StatsSections
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

/**
 * Drives the Trends section's cap/reveal guardrail directly against a synthetic
 * [InsightsTabState.Ready] rather than through real event fixtures — only two real detectors
 * exist (gap shift, streak shift), so real data can never produce more than
 * [com.secondmonday.hodith.domain.TrendsEngineTest]'s two findings, never enough to exercise the
 * "show more" cap until T2+ ships more detectors. `stats.trends` is set directly here instead.
 */
@UiTest
class InsightsTabTrendsCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val case = testCase()

    private fun syntheticFinding(index: Int) =
        TrendFinding(
            kind = if (index % 2 == 0) TrendFindingKind.GAP_SHIFT else TrendFindingKind.STREAK_SHIFT,
            direction = ShiftDirection.UP,
            reliability = TrendReliability.HINT,
            sampleCount = 6 + index,
            priorValue = 3.0,
            recentValue = 5.0,
        )

    // RhythmDisplay.cells is documented as always all 28 day-of-week x time-of-day cells --
    // RhythmCard indexes into it with a plain `.first { }`, so a synthetic Ready state below still
    // needs the full grid, not an empty list.
    private val emptyRhythmCells =
        DayOfWeek.entries.flatMap { day ->
            TimeOfDay.entries.map { timeOfDay -> RhythmCellDisplay(day, timeOfDay, HeatmapLevel.EMPTY, count = 0) }
        }

    private fun readyState(trends: List<TrendFinding>) =
        InsightsTabState.Ready(
            heatmapMonths = emptyList(),
            stats =
                StatsSections(
                    frequency = null,
                    rhythm = RhythmDisplay(cells = emptyRhythmCells, plottedByStart = false),
                    gaps = GapsDisplay(0, 0, 0.0, false, 0, 0.0),
                    trend = null,
                    duration = null,
                    intensity = null,
                    tags = emptyList(),
                    totalEventCount = 0,
                    trends = trends,
                ),
        )

    private fun setContent(
        trends: List<TrendFinding>,
        onOpenTrends: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                InsightsTabContent(
                    state = readyState(trends),
                    case = case,
                    events = emptyList(),
                    now = 0L,
                    voice = PlainVoice,
                    frequencyGranularityOverride = null,
                    onFrequencyGranularityChange = {},
                    onEditEvent = {},
                    onOpenTrends = onOpenTrends,
                )
            }
        }
    }

    @Test
    fun trendsCard_hidden_whenNoFindings() {
        setContent(trends = emptyList())

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrends).assertDoesNotExist()
    }

    @Test
    fun trendsCard_noShowMoreLink_atExactlyTheDefaultVisibleCount() {
        setContent(trends = List(3) { syntheticFinding(it) })

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrends).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsTrendsShowMoreAction).assertDoesNotExist()
    }

    @Test
    fun trendsCard_showMoreLink_appearsAndInvokesCallback_whenMoreThanTheDefaultVisibleCount() {
        var opened = false
        setContent(trends = List(5) { syntheticFinding(it) }, onOpenTrends = { opened = true })

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendsShowMoreAction).performClick()

        assert(opened) { "onOpenTrends was not invoked by the show-more link" }
    }

    @Test
    fun trendsCard_rendersWentQuietSentence() {
        val wentQuiet = TrendFinding(TrendFindingKind.WENT_QUIET, ShiftDirection.UP, TrendReliability.HINT, 6, 5.0, 20.0)
        setContent(trends = listOf(wentQuiet))

        composeTestRule
            .onNodeWithText(PlainVoice.insightsWentQuietSentence(currentGapLabel = "20 days", longestPastGapLabel = "5 days"))
            .assertExists()
    }

    @Test
    fun trendsCard_rendersRecurrenceShapeSentence() {
        val recurrenceShape = TrendFinding(TrendFindingKind.RECURRENCE_SHAPE, ShiftDirection.UP, TrendReliability.HINT, 11, 3.0, 0.8)
        setContent(trends = listOf(recurrenceShape))

        composeTestRule
            .onNodeWithText(PlainVoice.insightsRecurrenceShapeSentence(ShiftDirection.UP, thresholdLabel = "3 days", shareLabel = "80%"))
            .assertExists()
    }

    @Test
    fun trendsCard_rendersTagOutcomeSentence_forIntensity() {
        val tagOutcome =
            TrendFinding(
                kind = TrendFindingKind.TAG_OUTCOME,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 40,
                priorValue = 3.0,
                recentValue = 4.5,
                tagName = "aura",
                outcome = TagOutcome.INTENSITY,
            )
        setContent(trends = listOf(tagOutcome))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTagOutcomeSentence(
                    "aura",
                    TagOutcome.INTENSITY,
                    ShiftDirection.UP,
                    relativeDifferenceLabel = "50%",
                    withoutTagLabel = "3.0",
                    withTagLabel = "4.5",
                ),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTagOutcomeSentence_forDuration() {
        // Distinct from the intensity case above specifically to catch a swapped formatter (minutes
        // vs. a 1-decimal score) or a swapped Voice argument -- same direction/relative-difference
        // shape, different unit, so only the label text distinguishes them.
        val tagOutcome =
            TrendFinding(
                kind = TrendFindingKind.TAG_OUTCOME,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 40,
                priorValue = 30.0,
                recentValue = 45.0,
                tagName = "aura",
                outcome = TagOutcome.DURATION,
            )
        setContent(trends = listOf(tagOutcome))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTagOutcomeSentence(
                    "aura",
                    TagOutcome.DURATION,
                    ShiftDirection.UP,
                    relativeDifferenceLabel = "50%",
                    withoutTagLabel = "30m",
                    withTagLabel = "45m",
                ),
            ).assertExists()
    }
}
