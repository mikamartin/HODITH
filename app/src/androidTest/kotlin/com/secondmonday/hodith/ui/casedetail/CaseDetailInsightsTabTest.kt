package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Drives [CaseDetailScreen]'s Insights tab (dot timeline, calendar heatmap, seven stat cards),
 * same stateless pattern as `CaseDetailScreenTest`'s Log/Hunch coverage but split into its own
 * class given the number of gating scenarios. Boundary values below (2 events, 56-day trend span,
 * 3-month heatmap default) mirror HODITH_SPEC.md §9-10 and the `domain` constants they're drawn
 * from (`INSIGHTS_MIN_EVENTS`, `TREND_MIN_SPAN_DAYS`) — those constants are `internal` and not
 * visible from this module's `androidTest` source set, so the values are restated here rather
 * than imported, same as `CaseDetailScreenTest`'s 24h stale-event threshold. This exercises card
 * presence/absence and the two interactive toggles (granularity, "show more months") — the
 * underlying math is already covered exhaustively by `StatsEngineTest` and `InsightsEngineTest`
 * on the JVM.
 */
@UiTest
class CaseDetailInsightsTabTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 7, 23)
    private val now = today.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun daysAgo(days: Long): Long = now - days * 24 * 60 * 60_000L

    private fun setInsightsTabContent(
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
        caseCreatedAt: Long = daysAgo(30),
        events: List<EventWithTags> = emptyList(),
    ) {
        val case = testCase(durationMode = durationMode, intensityEnabled = intensityEnabled, createdAt = caseCreatedAt)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                CaseDetailScreen(
                    uiState = CaseDetailUiState(case = case, events = events, isLoading = false),
                    onBack = {},
                    onEditCase = {},
                    onDeleteEvent = {},
                    newEventDraft = {
                        LogDraft(
                            occurredAt = now,
                            intensity = null,
                            durationMinutes = "",
                            note = "",
                            tags = emptyList(),
                            endedAt = null,
                            existingEndedAt = null,
                        )
                    },
                    onSaveEvent = { _, _, _ -> },
                    onStopEvent = {},
                    onDismissStalePrompt = {},
                    nowMillis = { now },
                    onAddHunch = { _, _, _ -> },
                    onResolveHunch = {},
                    onDismissHunchNudge = {},
                )
            }
        }
        composeTestRule.onNodeWithText(PlainVoice.caseDetailInsightsTabLabel).performClick()
    }

    // The Log tab's LazyColumn keys items by event id, so each fixture needs a distinct one even
    // though these tests only ever look at the Insights tab.
    private var nextEventId = 1L

    private fun eventAt(
        daysAgo: Long,
        caseId: Long = 0L,
        endedAt: Long? = null,
        intensity: Int? = null,
        tags: List<TagEntity> = emptyList(),
    ) = EventWithTags(
        event =
            testEvent(
                caseId = caseId,
                id = nextEventId++,
                occurredAt = daysAgo(daysAgo),
                endedAt = endedAt,
                intensity = intensity,
            ),
        tags = tags,
    )

    @Test
    fun belowInsightsMinEvents_showsNotEnoughDataPlaceholder_notAnEmptyChart() {
        setInsightsTabContent(events = listOf(eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsNotEnoughDataMessage).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTimeline).assertDoesNotExist()
    }

    @Smoke
    @Test
    fun atInsightsMinEvents_showsCoreCards_butNotOptionalOnes() {
        setInsightsTabContent(events = listOf(eventAt(2), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTimeline).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelHeatmap).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelFrequency).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelRhythm).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelGaps).assertExists()

        // Short observation span, NONE duration mode, intensity disabled, no tags logged.
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrend).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTags).assertDoesNotExist()
    }

    @Test
    fun trendCard_hiddenJustBelowEightWeekSpan() {
        // spec §10: trend needs >= 8 weeks (56 days) of observation.
        setInsightsTabContent(caseCreatedAt = daysAgo(55), events = listOf(eventAt(2), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrend).assertDoesNotExist()
    }

    @Test
    fun trendCard_shownAtEightWeekSpan_withDirectionAwareSentence() {
        // 3 events in the last 30 days vs. 1 in the 30 before -> more recently, i.e. UP.
        setInsightsTabContent(
            caseCreatedAt = daysAgo(56),
            events = listOf(eventAt(5), eventAt(10), eventAt(20), eventAt(45)),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrend).assertExists()
        composeTestRule
            .onNodeWithText(PlainVoice.insightsTrendSentence(TrendDirection.UP, recentCount = 3, priorCount = 1))
            .assertExists()
    }

    @Test
    fun durationCard_absentWhenDurationModeNone_evenWithADurationOnAnEvent() {
        setInsightsTabContent(
            durationMode = DurationMode.NONE,
            events = listOf(eventAt(2, endedAt = daysAgo(2) + 60_000L), eventAt(1)),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
    }

    @Test
    fun durationCard_presentWhenDurationModeSetAndAnEventHasADuration() {
        setInsightsTabContent(
            durationMode = DurationMode.START_STOP,
            events = listOf(eventAt(2, endedAt = daysAgo(2) + 60_000L), eventAt(1)),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertExists()
    }

    @Test
    fun intensityCard_absentWhenDisabled_evenWithAnIntensityOnAnEvent() {
        setInsightsTabContent(intensityEnabled = false, events = listOf(eventAt(2, intensity = 4), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
    }

    @Test
    fun intensityCard_presentWhenEnabledAndAnEventHasAnIntensity() {
        setInsightsTabContent(intensityEnabled = true, events = listOf(eventAt(2, intensity = 4), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertExists()
    }

    @Test
    fun tagsCard_presentOnlyWhenAnEventCarriesATag() {
        setInsightsTabContent(
            events = listOf(eventAt(2, tags = listOf(TagEntity(id = 1L, name = "flare-up"))), eventAt(1)),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTags).assertExists()
        composeTestRule.onNodeWithText("flare-up").assertExists()
    }

    @Test
    fun frequencyGranularityToggle_day_switchesBucketLabelFormat() {
        setInsightsTabContent(events = listOf(eventAt(2), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsFrequencyGranularityWeek).performScrollTo().performClick()

        // Both the first and last bucket's period labels switch format, hence two matches.
        composeTestRule.onAllNodesWithText("Week of", substring = true).assertCountEquals(2)
    }

    @Test
    fun heatmapShowMore_revealsMonthsBeyondTheDefaultThreeMonthWindow() {
        // spec §9: three most recent months shown by default, full history behind a toggle.
        val earliestMonth = today.minusMonths(4)
        setInsightsTabContent(
            caseCreatedAt =
                earliestMonth
                    .withDayOfMonth(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli(),
            events = listOf(eventAt(2), eventAt(1)),
        )

        composeTestRule.onNodeWithText(monthYearLabel(earliestMonth)).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsHeatmapShowMoreAction).performScrollTo().performClick()
        composeTestRule.onNodeWithText(monthYearLabel(earliestMonth)).assertExists()
    }

    // Mirrors InsightsTab.kt's private YearMonth.monthYearLabel() formatting, so the expected text matches exactly.
    private fun monthYearLabel(date: LocalDate): String = "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
}
