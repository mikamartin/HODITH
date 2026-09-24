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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
    fun trendsCard_wentQuietLeading_showsOnlyWentQuietPlusShowMoreLink() {
        val wentQuiet = TrendFinding(TrendFindingKind.WENT_QUIET, ShiftDirection.UP, TrendReliability.HINT, 6, 5.0, 20.0)
        var opened = false
        setContent(trends = listOf(wentQuiet) + List(5) { syntheticFinding(it) }, onOpenTrends = { opened = true })

        composeTestRule
            .onNodeWithText(PlainVoice.insightsWentQuietSentence(currentGapLabel = "20 days", longestPastGapLabel = "5 days"))
            .assertExists()
        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsGapShiftSentence(ShiftDirection.UP, priorAverageLabel = "3 days", recentAverageLabel = "5 days"),
            ).assertDoesNotExist()

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendsShowMoreAction).performClick()

        assert(opened) { "onOpenTrends was not invoked by the show-more link" }
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

    @Test
    fun trendsCard_rendersChangePointSentence() {
        val changePoint =
            TrendFinding(
                kind = TrendFindingKind.CHANGE_POINT,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 20,
                priorValue = 3.0,
                recentValue = 9.0,
                changePointDate = LocalDate.of(2026, 3, 14),
            )
        setContent(trends = listOf(changePoint))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsChangePointSentence(
                    ShiftDirection.UP,
                    dateLabel = "mid-March",
                    priorLabel = "3 days",
                    recentLabel = "9 days",
                ),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTrendSlopeSentence_forIntensity() {
        val trendSlope =
            TrendFinding(
                kind = TrendFindingKind.TREND_SLOPE,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 20,
                priorValue = 2.0,
                recentValue = 4.5,
                outcome = TagOutcome.INTENSITY,
            )
        setContent(trends = listOf(trendSlope))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTrendSlopeSentence(TagOutcome.INTENSITY, ShiftDirection.UP, priorLabel = "2.0", recentLabel = "4.5"),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTrendSlopeSentence_forDuration() {
        // Distinct from the intensity case above specifically to catch a swapped formatter (minutes
        // vs. a 1-decimal score) or a swapped Voice argument.
        val trendSlope =
            TrendFinding(
                kind = TrendFindingKind.TREND_SLOPE,
                direction = ShiftDirection.DOWN,
                reliability = TrendReliability.PATTERN,
                sampleCount = 20,
                priorValue = 50.0,
                recentValue = 20.0,
                outcome = TagOutcome.DURATION,
            )
        setContent(trends = listOf(trendSlope))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTrendSlopeSentence(TagOutcome.DURATION, ShiftDirection.DOWN, priorLabel = "50m", recentLabel = "20m"),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTimeOfDaySplitSentence_eveningHigher() {
        val timeOfDaySplit =
            TrendFinding(
                kind = TrendFindingKind.TIME_OF_DAY_SPLIT,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 40,
                priorValue = 2.0,
                recentValue = 4.0,
                outcome = TagOutcome.INTENSITY,
            )
        setContent(trends = listOf(timeOfDaySplit))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTimeOfDaySplitSentence(TagOutcome.INTENSITY, ShiftDirection.UP, dayLabel = "2.0", eveningLabel = "4.0"),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTimeOfDaySplitSentence_dayHigher() {
        // The other direction, so the sentence doesn't just hardcode "evening worse" regardless of
        // the actual finding.
        val timeOfDaySplit =
            TrendFinding(
                kind = TrendFindingKind.TIME_OF_DAY_SPLIT,
                direction = ShiftDirection.DOWN,
                reliability = TrendReliability.PATTERN,
                sampleCount = 40,
                priorValue = 45.0,
                recentValue = 15.0,
                outcome = TagOutcome.DURATION,
            )
        setContent(trends = listOf(timeOfDaySplit))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTimeOfDaySplitSentence(TagOutcome.DURATION, ShiftDirection.DOWN, dayLabel = "45m", eveningLabel = "15m"),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTagTimingSentence_weekday() {
        val tagTiming =
            TrendFinding(
                kind = TrendFindingKind.TAG_TIMING,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 30,
                priorValue = 0.40,
                recentValue = 1.0,
                tagName = "focus",
                weekday = DayOfWeek.TUESDAY,
            )
        setContent(trends = listOf(tagTiming))

        // Mirrors TrendFindingContent's own bucketPhrase construction (DayOfWeek.getDisplayName(TextStyle.FULL, locale) + "s").
        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTagTimingSentence(
                    "focus",
                    bucketPhrase = "on ${DayOfWeek.TUESDAY.getDisplayName(TextStyle.FULL, Locale.US)}s",
                    baselineLabel = "40%",
                    taggedLabel = "100%",
                ),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersTagTimingSentence_timeOfDay() {
        val tagTiming =
            TrendFinding(
                kind = TrendFindingKind.TAG_TIMING,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 20,
                priorValue = 0.25,
                recentValue = 0.80,
                tagName = "focus",
                timeOfDay = TimeOfDay.EVENING,
            )
        setContent(trends = listOf(tagTiming))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsTagTimingSentence("focus", bucketPhrase = "in the evening", baselineLabel = "25%", taggedLabel = "80%"),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersWeekdayWeekendSentence_weekendHeavy() {
        val weekdayWeekend =
            TrendFinding(
                kind = TrendFindingKind.WEEKDAY_WEEKEND_SPLIT,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                sampleCount = 100,
                priorValue = 2.0 / 7.0,
                recentValue = 0.60,
            )
        setContent(trends = listOf(weekdayWeekend))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsWeekdayWeekendSentence(ShiftDirection.UP, weekdayLabel = "40%", weekendLabel = "60%"),
            ).assertExists()
    }

    @Test
    fun trendsCard_rendersWeekdayWeekendSentence_weekdayHeavy() {
        val weekdayWeekend =
            TrendFinding(
                kind = TrendFindingKind.WEEKDAY_WEEKEND_SPLIT,
                direction = ShiftDirection.DOWN,
                reliability = TrendReliability.PATTERN,
                sampleCount = 100,
                priorValue = 2.0 / 7.0,
                recentValue = 0.05,
            )
        setContent(trends = listOf(weekdayWeekend))

        composeTestRule
            .onNodeWithText(
                PlainVoice.insightsWeekdayWeekendSentence(ShiftDirection.DOWN, weekdayLabel = "95%", weekendLabel = "5%"),
            ).assertExists()
    }
}
