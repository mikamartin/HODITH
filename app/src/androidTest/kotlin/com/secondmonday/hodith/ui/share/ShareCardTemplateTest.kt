package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.HeroRate
import com.secondmonday.hodith.domain.HeroRateComparison
import com.secondmonday.hodith.domain.RateUnit
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.casedetail.formatDays
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.DurationDisplay
import com.secondmonday.hodith.viewmodel.FrequencyBar
import com.secondmonday.hodith.viewmodel.FrequencyDisplay
import com.secondmonday.hodith.viewmodel.GapsDisplay
import com.secondmonday.hodith.viewmodel.IntensityDisplay
import com.secondmonday.hodith.viewmodel.LogCardRow
import com.secondmonday.hodith.viewmodel.RhythmCellDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.ShareCardData
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareTopBeat
import com.secondmonday.hodith.viewmodel.formatCardTimestamp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

private const val STORY_TAG = "story_card"
private const val SQUARE_TAG = "square_card"
private const val RICH_SQUARE_TAG = "rich_square_card"
private const val BOUNDS_TOLERANCE_DP = 1f

/** Arbitrary fixed instant — only needs to be self-consistent between what a fixture is built with and what a test formats to compare against, not any particular real date. */
private const val FIXTURE_GENERATED_AT_MILLIS = 0L

/** What the footer renders for [FIXTURE_GENERATED_AT_MILLIS] under the default 12-hour clock. */
private val FIXTURE_FOOTER_TIMESTAMP = formatCardTimestamp(FIXTURE_GENERATED_AT_MILLIS, TimeFormat.TWELVE_HOUR.is24Hour)

private val FREQUENCY_SHIFT_FINDING =
    TrendFinding(
        kind = TrendFindingKind.FREQUENCY_SHIFT,
        direction = ShiftDirection.UP,
        reliability = TrendReliability.HINT,
        sampleCount = 8,
        priorValue = 5.0,
        recentValue = 8.0,
    )

private val SAMPLE_RATE =
    HeroRate(
        value = 2.1,
        unit = RateUnit.WEEK,
        belowOnePerMonth = false,
        comparison = HeroRateComparison(direction = TrendDirection.UP, priorValue = 1.4),
    )

private val SAMPLE_GAPS =
    GapsDisplay(
        longestGapDays = 9,
        currentGapDays = 2,
        averageGapDays = 3.1,
        isBursty = false,
        longestStreakDays = 0,
        averageStreakDays = 0.0,
        shortestGapDays = 1,
    )

private val SAMPLE_DURATION = DurationDisplay(averageMinutes = 130.0, longestMinutes = 400, totalMinutes = 4030, shortestMinutes = 25)

private val SAMPLE_INTENSITY =
    IntensityDisplay(averageIntensity = 3.4, distribution = mapOf(1 to 2, 2 to 6, 3 to 11, 4 to 9, 5 to 3), maxCount = 11)

/** Regression coverage for spec §13's sizing rules (Square keeps its 1:1 floor, Story sizes freely) and the overflow-clip bug they replaced. */
@UiTest
class ShareCardTemplateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun realityData(format: ShareCardFormat) =
        ShareCardData.Insights(
            format = format,
            caseIcon = "☕",
            caseName = "Perfect coffee",
            topBeat = ShareTopBeat.Reality(eventCount = 14, observedDays = 60),
            frequency = null,
            rhythm = null,
            gaps = null,
            trends = emptyList(),
            duration = null,
            intensity = null,
            generatedAtMillis = FIXTURE_GENERATED_AT_MILLIS,
        )

    /** Enough sections to reliably exceed Square's floor, so its no-clip behavior is actually exercised. */
    private fun richData(format: ShareCardFormat) =
        ShareCardData.Insights(
            format = format,
            caseIcon = "☕",
            caseName = "Perfect coffee",
            topBeat = ShareTopBeat.Reality(eventCount = 14, observedDays = 60),
            frequency =
                FrequencyDisplay(
                    granularity = FrequencyGranularity.WEEK,
                    bars = listOf(3, 5, 2, 7, 4, 9).map { FrequencyBar(LocalDate.now(), it, it / 9f) },
                ),
            rhythm =
                RhythmDisplay(
                    cells =
                        DayOfWeek.entries.flatMap { day ->
                            TimeOfDay.entries.map { tod -> RhythmCellDisplay(day, tod, HeatmapLevel.L2, count = 0) }
                        },
                    plottedByStart = false,
                ),
            gaps = null,
            trends = listOf(FREQUENCY_SHIFT_FINDING),
            duration = null,
            intensity = null,
            generatedAtMillis = FIXTURE_GENERATED_AT_MILLIS,
        )

    /** The Square preset's data shape: a Summary beat, no Frequency/Trends, and whichever panels the Case's settings called for. */
    private fun squareData(
        rate: HeroRate? = null,
        eventCount: Int = 14,
        gaps: GapsDisplay? = null,
        duration: DurationDisplay? = null,
        intensity: IntensityDisplay? = null,
        rhythm: RhythmDisplay? = null,
        quietForDays: Long? = null,
    ) = ShareCardData.Insights(
        format = ShareCardFormat.SQUARE,
        caseIcon = "🤕",
        caseName = "Headaches",
        topBeat = ShareTopBeat.Summary(eventCount = eventCount, observedDays = 60, rate = rate),
        frequency = null,
        rhythm = rhythm,
        gaps = gaps,
        trends = emptyList(),
        duration = duration,
        intensity = intensity,
        quietForDays = quietForDays,
        generatedAtMillis = FIXTURE_GENERATED_AT_MILLIS,
    )

    /** Every Square panel at once, so its no-clip behavior is exercised at the tallest the preset gets. */
    private fun richSquareData() =
        squareData(
            rate = SAMPLE_RATE,
            gaps = SAMPLE_GAPS,
            duration = SAMPLE_DURATION,
            intensity = SAMPLE_INTENSITY,
            quietForDays = 14,
        )

    @Test
    fun squareHitsItsSquareFloorForSparseContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(
                    data = squareData(),
                    voice = PlainVoice,
                    modifier = Modifier.testTag(SQUARE_TAG),
                )
            }
        }

        val bounds = composeTestRule.onNodeWithTag(SQUARE_TAG).getUnclippedBoundsInRoot()
        val width = (bounds.right - bounds.left).value
        val height = (bounds.bottom - bounds.top).value

        assertTrue(
            "Expected Square's floor to be ~1:1 (width=$width, height=$height)",
            kotlin.math.abs(width - height) < BOUNDS_TOLERANCE_DP,
        )
    }

    @Test
    fun squareGrowsPastItsFloorForRichContentInsteadOfClippingIt() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                Column {
                    ShareCardTemplate(
                        data = squareData(),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(SQUARE_TAG),
                    )
                    ShareCardTemplate(
                        data = richSquareData(),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(RICH_SQUARE_TAG),
                    )
                }
            }
        }

        val sparseHeight = composeTestRule.onNodeWithTag(SQUARE_TAG).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val richHeight = composeTestRule.onNodeWithTag(RICH_SQUARE_TAG).getUnclippedBoundsInRoot().let { it.bottom - it.top }

        assertTrue(
            "Expected rich Square ($richHeight) taller than sparse Square ($sparseHeight) — a fixed height here means content is being clipped, not measured",
            richHeight > sparseHeight,
        )
    }

    @Test
    fun storySizesToContentAndIsShorterThanSquaresFloorForSparseContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                Column {
                    ShareCardTemplate(
                        data = realityData(ShareCardFormat.STORY),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(STORY_TAG),
                    )
                    ShareCardTemplate(
                        data = squareData(),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(SQUARE_TAG),
                    )
                }
            }
        }

        val storyHeight = composeTestRule.onNodeWithTag(STORY_TAG).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val squareHeight = composeTestRule.onNodeWithTag(SQUARE_TAG).getUnclippedBoundsInRoot().let { it.bottom - it.top }

        // Story sizes purely to its sparse (header + one beat + footer) content, so it should land
        // well under Square's 1:1 floor.
        assertTrue("Expected sparse Story ($storyHeight) shorter than sparse Square's floor ($squareHeight)", storyHeight < squareHeight)
    }

    /** The footer's own bottom padding keeps a fixed gap above the card edge — a growing gap on rich content would mean overflow got cropped. */
    @Smoke
    @Test
    fun footerGapAboveTheCardEdgeStaysConstantRegardlessOfContentAmount() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                Column {
                    ShareCardTemplate(
                        data = squareData(),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(SQUARE_TAG),
                    )
                    ShareCardTemplate(
                        data = richSquareData(),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(RICH_SQUARE_TAG),
                    )
                }
            }
        }

        val footers = composeTestRule.onAllNodesWithText(PlainVoice.shareCardFooter(FIXTURE_FOOTER_TIMESTAMP))
        val sparseGap =
            composeTestRule.onNodeWithTag(SQUARE_TAG).getUnclippedBoundsInRoot().bottom - footers[0].getUnclippedBoundsInRoot().bottom
        val richGap =
            composeTestRule.onNodeWithTag(RICH_SQUARE_TAG).getUnclippedBoundsInRoot().bottom -
                footers[1].getUnclippedBoundsInRoot().bottom

        assertTrue(
            "Expected the footer's trailing gap to stay constant (sparse=$sparseGap, rich=$richGap) — " +
                "a growing gap means rich content is landing short of the card's true bottom edge",
            kotlin.math.abs((sparseGap - richGap).value) < BOUNDS_TOLERANCE_DP,
        )
    }

    @Test
    fun trendsSectionRendersSentenceOnlyWithNoReliabilityTagOrEvidenceLine() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = richData(ShareCardFormat.STORY), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendSentence(TrendDirection.UP, 8, 5)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendReliabilityHintLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsFrequencyShiftEvidenceLabel()).assertDoesNotExist()
    }

    @Test
    fun trendsSectionRendersEachSelectedFindingAsItsOwnLine() {
        val gapShiftFinding =
            TrendFinding(
                kind = TrendFindingKind.GAP_SHIFT,
                direction = ShiftDirection.DOWN,
                reliability = TrendReliability.HINT,
                sampleCount = 12,
                priorValue = 6.0,
                recentValue = 3.0,
            )
        val data = richData(ShareCardFormat.STORY).copy(trends = listOf(FREQUENCY_SHIFT_FINDING, gapShiftFinding))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = data, voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.insightsTrendSentence(TrendDirection.UP, 8, 5)).assertExists()
        composeTestRule
            .onNodeWithText(PlainVoice.insightsGapShiftSentence(ShiftDirection.DOWN, formatDays(6.0), formatDays(3.0)))
            .assertExists()
    }

    @Test
    fun footerRendersTheCardsGeneratedDateAndTime() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareCardFooter(FIXTURE_FOOTER_TIMESTAMP)).assertExists()
    }

    // ---- Square preset ----

    @Test
    fun squareSummaryShowsTheObservedLineTheRateAndTheTrendPill() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(rate = SAMPLE_RATE), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareObservedLine(60, 14)).assertExists()
        composeTestRule.onNodeWithText("2.1/week").assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareSquareTrendFrom("1.4")).assertExists()
    }

    @Test
    fun squareSummaryWithoutARateLeadsWithTheEventCount() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(rate = null, eventCount = 4), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareObservedDays(60)).assertExists()
        composeTestRule.onNodeWithText("4 events").assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareSquareObservedLine(60, 4)).assertDoesNotExist()
    }

    @Test
    fun squareSummaryShowsTheBelowOneMarkerForAnUnderOneAMonthRate() {
        val rare = SAMPLE_RATE.copy(value = 0.0, belowOnePerMonth = true, comparison = null)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(rate = rare.copy(unit = RateUnit.MONTH)), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareRateBelowOneMarker + PlainVoice.shareRatePerMonthUnit).assertExists()
    }

    @Test
    fun squareSummaryShowsTheSameAsLabelWhenTheRateDidNotMove() {
        val steady = SAMPLE_RATE.copy(comparison = HeroRateComparison(direction = TrendDirection.FLAT, priorValue = 2.1))
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(rate = steady), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareTrendSame).assertExists()
    }

    @Test
    fun squareGapsPanelShowsMinAvgMaxAndTheQuietLabel() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(gaps = SAMPLE_GAPS, quietForDays = 14), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareGapsTitle).assertExists()
        composeTestRule.onNodeWithText("1d").assertExists()
        composeTestRule.onNodeWithText("3.1d").assertExists()
        composeTestRule.onNodeWithText("9d").assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareSquareQuietLabel("14d")).assertExists()
    }

    @Test
    fun squareGapsPanelHasNoQuietLabelUnlessTheCaseWentQuiet() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(gaps = SAMPLE_GAPS, quietForDays = null), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareQuietLabel("14d")).assertDoesNotExist()
    }

    @Test
    fun squareGapsPanelExplainsItselfWhenThereIsNoGapYet() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(gaps = SAMPLE_GAPS.copy(shortestGapDays = null)), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareGapsNeedMoreEvents).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareStatMinLabel).assertDoesNotExist()
    }

    @Test
    fun squareShowsDurationAndIntensityPanelsWhenGivenThem() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(
                    data = squareData(gaps = SAMPLE_GAPS, duration = SAMPLE_DURATION, intensity = SAMPLE_INTENSITY),
                    voice = PlainVoice,
                )
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareDurationTitle).assertExists()
        composeTestRule.onNodeWithText("25m").assertExists()
        composeTestRule.onNodeWithText("2h 10m").assertExists()
        composeTestRule.onNodeWithText("6h 40m").assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareSquareIntensityAverage("3.4")).assertExists()
    }

    @Test
    fun squareShowsAWholeIntensityAverageWithoutATrailingZero() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(intensity = SAMPLE_INTENSITY.copy(averageIntensity = 3.0)), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareSquareIntensityAverage("3")).assertExists()
    }

    @Test
    fun squareHidesRhythmUnlessGivenItAndOmitsStorysSections() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = squareData(gaps = SAMPLE_GAPS), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelRhythm).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrends).assertDoesNotExist()
    }

    @Test
    fun squareKeepsTheFigureAndTheTrendPillApartAtLargeTextSizes() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalVoice provides PlainVoice, LocalDensity provides Density(density.density, fontScale = 2f)) {
                ShareCardTemplate(data = squareData(rate = SAMPLE_RATE, gaps = SAMPLE_GAPS), voice = PlainVoice)
            }
        }

        val figure = composeTestRule.onNodeWithText("2.1/week").getUnclippedBoundsInRoot()
        val pill = composeTestRule.onNodeWithText(PlainVoice.shareSquareTrendFrom("1.4")).getUnclippedBoundsInRoot()

        // Side by side, or the pill dropped below the figure: either way the two never overlap.
        val sideBySide = figure.right <= pill.left
        val stacked = figure.bottom <= pill.top
        assertTrue("Expected the pill beside or below the figure (figure=$figure, pill=$pill)", sideBySide || stacked)
    }

    private fun logData(
        rows: List<LogCardRow>,
        truncatedTotalCount: Int? = null,
    ) = ShareCardData.Log(
        format = ShareCardFormat.STORY,
        caseIcon = "🤕",
        caseName = "Migraine",
        generatedAtMillis = FIXTURE_GENERATED_AT_MILLIS,
        rangeLabel = "All time",
        rows = rows,
        truncatedTotalCount = truncatedTotalCount,
    )

    @Test
    fun logCardRendersKickerRangeAndEachRow() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(
                    data = logData(rows = listOf(LogCardRow(timestamp = "Wed, Sep 9 · 8:14 PM", detail = "5h 20m · Intensity 4/5"))),
                    voice = PlainVoice,
                )
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareLogCardKicker).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareLogRangeAllTimeLabel).assertExists()
        composeTestRule.onNodeWithText("Wed, Sep 9 · 8:14 PM").assertExists()
        composeTestRule.onNodeWithText("5h 20m · Intensity 4/5").assertExists()
    }

    @Test
    fun logCardShowsTruncationNoteOnlyWhenCapped() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                Column {
                    ShareCardTemplate(
                        data = logData(rows = listOf(LogCardRow("Wed, Sep 9 · 8:14 PM", null)), truncatedTotalCount = null),
                        voice = PlainVoice,
                    )
                    ShareCardTemplate(
                        data = logData(rows = listOf(LogCardRow("Wed, Sep 9 · 8:14 PM", null)), truncatedTotalCount = 42),
                        voice = PlainVoice,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareLogTruncationNote(1, 42)).assertExists()
    }

    @Test
    fun logCardShowsEmptyRangeMessageWhenNoRowsMatch() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = logData(rows = emptyList()), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareLogEmptyRangeMessage).assertExists()
    }
}
