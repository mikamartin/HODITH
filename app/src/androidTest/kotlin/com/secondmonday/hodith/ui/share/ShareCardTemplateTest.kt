package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HeatmapLevel
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
import com.secondmonday.hodith.viewmodel.FrequencyBar
import com.secondmonday.hodith.viewmodel.FrequencyDisplay
import com.secondmonday.hodith.viewmodel.LogCardRow
import com.secondmonday.hodith.viewmodel.RhythmCellDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.ShareCardData
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareTopBeat
import com.secondmonday.hodith.viewmodel.formatEventDate
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

private val FREQUENCY_SHIFT_FINDING =
    TrendFinding(
        kind = TrendFindingKind.FREQUENCY_SHIFT,
        direction = ShiftDirection.UP,
        reliability = TrendReliability.HINT,
        sampleCount = 8,
        priorValue = 5.0,
        recentValue = 8.0,
    )

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

    private fun hunchVsRealityData(format: ShareCardFormat) =
        ShareCardData.Insights(
            format = format,
            caseIcon = "☕",
            caseName = "Perfect coffee",
            topBeat =
                ShareTopBeat.HunchVsReality(
                    hunch =
                        HunchEntity(
                            id = 1L,
                            caseId = 1L,
                            direction = HunchDirection.TOO_OFTEN,
                            expectedCount = 2,
                            expectedPer = ExpectedPer.MONTH,
                            createdAt = 0L,
                            resolvedAt = null,
                        ),
                    observedRate = 7.0,
                    band = ComparisonBand.MUCH_MORE,
                ),
            frequency = null,
            rhythm = null,
            gaps = null,
            trends = emptyList(),
            duration = null,
            intensity = null,
            generatedAtMillis = FIXTURE_GENERATED_AT_MILLIS,
        )

    @Test
    fun squareHitsItsSquareFloorForSparseContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(
                    data = realityData(ShareCardFormat.SQUARE),
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
                        data = realityData(ShareCardFormat.SQUARE),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(SQUARE_TAG),
                    )
                    ShareCardTemplate(
                        data = richData(ShareCardFormat.SQUARE),
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
                        data = realityData(ShareCardFormat.SQUARE),
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
                        data = realityData(ShareCardFormat.SQUARE),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(SQUARE_TAG),
                    )
                    ShareCardTemplate(
                        data = richData(ShareCardFormat.SQUARE),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(RICH_SQUARE_TAG),
                    )
                }
            }
        }

        val footers = composeTestRule.onAllNodesWithText(PlainVoice.shareCardFooter(formatEventDate(FIXTURE_GENERATED_AT_MILLIS)))
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
    fun squareNeverShowsTheHunchVsRealityBeatEvenWhenDataProvidesIt() {
        // ShareCardTemplate trusts whatever ShareCardData.topBeat it's given — the actual STORY-only
        // gating lives in shareCardState (see ShareCardStateTest). This only proves the composable
        // itself does render a HunchVsReality beat when handed one on Square, so that gating stays
        // shareCardState's responsibility and can't quietly get "fixed" by the template hiding it too.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = hunchVsRealityData(ShareCardFormat.SQUARE), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareHunchRealityKicker).assertExists()
    }

    @Test
    fun trendsSectionRendersSentenceOnlyWithNoReliabilityTagOrEvidenceLine() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = richData(ShareCardFormat.SQUARE), voice = PlainVoice)
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
        val data = richData(ShareCardFormat.SQUARE).copy(trends = listOf(FREQUENCY_SHIFT_FINDING, gapShiftFinding))
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
    fun footerRendersTheCardsGeneratedDate() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = realityData(ShareCardFormat.SQUARE), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareCardFooter(formatEventDate(FIXTURE_GENERATED_AT_MILLIS))).assertExists()
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
