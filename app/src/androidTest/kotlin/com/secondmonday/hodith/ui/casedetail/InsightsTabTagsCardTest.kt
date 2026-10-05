package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.GapsDisplay
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.RhythmCellDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.StatsSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

/**
 * The Insights tag card in its two states: every row at or below the compact maximum, and the
 * collapsed summary-plus-busiest-few with a "see all" link past it. Driven by a synthetic
 * [InsightsTabState.Ready] like [InsightsTabTrendsCardTest], so the tag counts are exact and the
 * drill-down can be opened without event fixtures.
 */
@UiTest
class InsightsTabTagsCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val case = testCase()

    private val emptyRhythmCells =
        DayOfWeek.entries.flatMap { day ->
            TimeOfDay.entries.map { timeOfDay -> RhythmCellDisplay(day, timeOfDay, HeatmapLevel.EMPTY, count = 0) }
        }

    /** Busiest-first entries, the shape [com.secondmonday.hodith.domain.computeTagBreakdown] returns. */
    private fun tags(vararg entries: Pair<String, Int>) = entries.map { (name, count) -> TagBreakdownEntry(name, count) }

    private fun readyState(
        tags: List<TagBreakdownEntry>,
        totalEventCount: Int,
    ) = InsightsTabState.Ready(
        heatmapMonths = emptyList(),
        stats =
            StatsSections(
                frequency = null,
                rhythm = RhythmDisplay(cells = emptyRhythmCells, plottedByStart = false),
                gaps = GapsDisplay(0, 0, 0.0, false, 0, 0.0),
                duration = null,
                intensity = null,
                tags = tags,
                totalEventCount = totalEventCount,
                trends = emptyList(),
            ),
    )

    private fun setContent(
        tags: List<TagBreakdownEntry>,
        totalEventCount: Int = 40,
        onOpenTags: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                InsightsTabContent(
                    state = readyState(tags, totalEventCount),
                    case = case,
                    events = emptyList(),
                    now = 0L,
                    voice = PlainVoice,
                    frequencyGranularityOverride = null,
                    onFrequencyGranularityChange = {},
                    onEditEvent = {},
                    onOpenTrends = {},
                    onOpenTags = onOpenTags,
                )
            }
        }
    }

    @Test
    fun tagsCard_listsEveryTag_andNoSeeAllLink_atTheCompactMaximum() {
        setContent(
            tags = tags("alpha" to 12, "beta" to 11, "gamma" to 10, "delta" to 4, "epsilon" to 3),
        )

        listOf("alpha", "beta", "gamma", "delta", "epsilon").forEach { name ->
            composeTestRule.onNodeWithText(name).assertExists()
        }
        composeTestRule.onNodeWithText(PlainVoice.insightsTagsSeeAllAction).assertDoesNotExist()
    }

    @Test
    fun tagsCard_summaryShowsTotalEventsAndTotalTags() {
        setContent(
            tags = tags("alpha" to 12, "beta" to 11, "gamma" to 10, "delta" to 4, "epsilon" to 3),
            totalEventCount = 40,
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsTagsTotalLabel).assertExists()
        composeTestRule.onNodeWithText("40").assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsTagsDistinctLabel).assertExists()
        composeTestRule.onNodeWithText("5").assertExists()
    }

    @Test
    fun tagsCard_totalsSitSideBySideInOneRow_eventsThenTags() {
        setContent(
            tags = tags("alpha" to 12, "beta" to 11, "gamma" to 10, "delta" to 4, "epsilon" to 3),
            totalEventCount = 40,
        )

        val events = composeTestRule.onNodeWithText(PlainVoice.insightsTagsTotalLabel).getUnclippedBoundsInRoot()
        val tagsTotal = composeTestRule.onNodeWithText(PlainVoice.insightsTagsDistinctLabel).getUnclippedBoundsInRoot()
        assertEquals(events.top.value, tagsTotal.top.value, 0.5f)
        assertTrue(events.left < tagsTotal.left)
    }

    @Test
    fun tagsCard_pastTheCompactMaximum_showsOnlyTheBusiestThree_andSeeAllLink() {
        setContent(
            tags =
                tags(
                    "alpha" to 12,
                    "beta" to 11,
                    "gamma" to 10,
                    "delta" to 4,
                    "epsilon" to 3,
                    "zeta" to 2,
                ),
            totalEventCount = 40,
        )

        listOf("alpha", "beta", "gamma").forEach { name -> composeTestRule.onNodeWithText(name).assertExists() }
        listOf("delta", "epsilon", "zeta").forEach { name -> composeTestRule.onNodeWithText(name).assertDoesNotExist() }
        composeTestRule.onNodeWithText(PlainVoice.insightsTagsSeeAllAction).assertExists()
        composeTestRule.onNodeWithText("6").assertExists()
    }

    @Test
    fun tagsCard_seeAllLink_invokesOnOpenTags() {
        var opened = false
        setContent(
            tags =
                tags(
                    "alpha" to 12,
                    "beta" to 11,
                    "gamma" to 10,
                    "delta" to 4,
                    "epsilon" to 3,
                    "zeta" to 2,
                ),
            onOpenTags = { opened = true },
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsTagsSeeAllAction).performClick()

        assert(opened) { "onOpenTags was not invoked by the see-all link" }
    }

    @Test
    fun tagRow_tap_opensItsDrillDownDialog() {
        setContent(tags = tags("alpha" to 12, "beta" to 11))

        composeTestRule
            .onNodeWithContentDescription(PlainVoice.insightsTagRowTapDescription("alpha"))
            .performClick()

        composeTestRule.onNodeWithText(PlainVoice.insightsTagDrillDownTitle("alpha")).assertExists()
    }
}
