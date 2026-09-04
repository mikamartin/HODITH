package com.secondmonday.hodith.ui.casedetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDraft
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Drives [CaseDetailScreen]'s Insights tab (seven stat cards, then the calendar heatmap),
 * same stateless pattern as `CaseDetailScreenTest`'s Log/Hunch coverage but split into its own
 * class given the number of gating scenarios. Boundary values below (2 events, 56-day trend span,
 * 3-month heatmap default) mirror HODITH_SPEC.md §9-10 and the `domain` constants they're drawn
 * from (`INSIGHTS_MIN_EVENTS`, `TREND_MIN_SPAN_DAYS`) — those constants are `internal` and not
 * visible from this module's `androidTest` source set, so the values are restated here rather
 * than imported, same as `CaseDetailScreenTest`'s 24h stale-event threshold. This exercises card
 * presence/absence, the two interactive toggles (granularity, "show more months"), and — for
 * Duration/Intensity/Frequency/Gaps & Streaks — the exact rendered numeric text: the underlying
 * math is covered exhaustively by `StatsEngineTest` and `InsightsEngineTest` on the JVM, but only
 * this class catches a wiring or formatting bug between correct state and the displayed `Text`.
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
                    onEditEvent = { _, _ -> },
                    onOpenTriggers = {},
                    onOpenShare = {},
                    newEventDraft = {
                        LogDraft(
                            occurredAt = now,
                            intensity = null,
                            durationAmount = "",
                            durationUnit = DurationUnit.MINUTES,
                            note = "",
                            tags = emptyList(),
                            endedAt = null,
                            existingEndedAt = null,
                        )
                    },
                    onSaveEvent = {},
                    onStopEvent = {},
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
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelFrequency).assertDoesNotExist()
    }

    @Smoke
    @Test
    fun atInsightsMinEvents_showsCoreCards_butNotOptionalOnes() {
        setInsightsTabContent(events = listOf(eventAt(2), eventAt(1)))

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
    fun gapsCard_showsLongestAndAverageStreakLabelsAlongsideGapLabels() {
        setInsightsTabContent(events = listOf(eventAt(2), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsGapsLongestLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsGapsCurrentLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsStreakLongestLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsStreakAverageLabel).assertExists()
    }

    @Test
    fun gapsCard_currentGapReadsZero_whileAnEventIsRunning() {
        // Past gaps 3, 2 days; the newest event (25 days ago) is still open, so the Case is running
        // right now. Current gap is 0, and the 25-day active stretch is kept out of the longest gap
        // -- without the fix it would be max(3, 25) = 25.
        setInsightsTabContent(
            durationMode = DurationMode.START_STOP,
            events =
                listOf(
                    eventAt(30, endedAt = daysAgo(30) + 60 * 60_000L),
                    eventAt(27, endedAt = daysAgo(27) + 60 * 60_000L),
                    eventAt(25, endedAt = null),
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsGapsCurrentLabel).assertExists()
        composeTestRule.onNodeWithText("0 days").assertExists()
        // Longest gap is the 3-day past gap, not the 25-day still-running stretch.
        composeTestRule.onNodeWithText("3 days").assertExists()
    }

    @Test
    fun trendCard_showsGapShiftNote_whenAverageGapWidensNoticeably() {
        // Past gaps in chronological order: 4, 4, 4, 20, 20, 20 -- clearly widening in the second half.
        setInsightsTabContent(
            caseCreatedAt = daysAgo(100),
            events = listOf(97L, 93L, 89L, 85L, 65L, 45L, 25L).map { eventAt(it) },
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTrend).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsGapShiftSentence(ShiftDirection.UP)).assertExists()
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
    fun frequencyCard_hiddenAndRhythmRelabelled_whenAnEventSpansMultipleDays() {
        // A 3-day event (10 -> 7 days ago) makes a per-bucket count ambiguous (spec §9).
        setInsightsTabContent(
            durationMode = DurationMode.START_STOP,
            events = listOf(eventAt(10, endedAt = daysAgo(7)), eventAt(1)),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelFrequency).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelRhythmStarts).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelRhythm).assertDoesNotExist()
    }

    @Test
    fun frequencyCard_staysVisible_forANoneCaseWithAStoredMultiDayEndedAt() {
        // Same 3-day endedAt, but the Case no longer tracks duration (spec §9): every event is a
        // point, so "how often" is answerable again and the frequency card stays.
        setInsightsTabContent(
            durationMode = DurationMode.NONE,
            events = listOf(eventAt(10, endedAt = daysAgo(7)), eventAt(1)),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelFrequency).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelRhythm).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelRhythmStarts).assertDoesNotExist()
    }

    @Test
    fun frequencyGranularityToggle_day_switchesBucketLabelFormat() {
        setInsightsTabContent(events = listOf(eventAt(2), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsFrequencyGranularityWeek).performScrollTo().performClick()

        // Both the first and last bucket's period labels switch to the weekly wrapper, hence two matches.
        val weekWrapper = PlainVoice.insightsFrequencyWeekAxisLabel("").trim()
        composeTestRule.onAllNodesWithText(weekWrapper, substring = true).assertCountEquals(2)
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

    @Test
    fun durationCard_showsAverageLongestAndTotalAsFormattedDurations() {
        // 90min + 30min -> average (90+30)/2=60 -> "1h 0m", longest 90 -> "1h 30m", total 120 -> "2h 0m".
        setInsightsTabContent(
            durationMode = DurationMode.START_STOP,
            events =
                listOf(
                    eventAt(2, endedAt = daysAgo(2) + 90 * 60_000L),
                    eventAt(1, endedAt = daysAgo(1) + 30 * 60_000L),
                ),
        )

        composeTestRule.onNodeWithText("1h 0m").assertExists()
        composeTestRule.onNodeWithText("1h 30m").assertExists()
        composeTestRule.onNodeWithText("2h 0m").assertExists()
    }

    @Test
    fun intensityCard_showsAverageIntensityToOneDecimal() {
        // Intensities 2 and 3 -> average (2+3)/2 = 2.5.
        setInsightsTabContent(intensityEnabled = true, events = listOf(eventAt(2, intensity = 2), eventAt(1, intensity = 3)))

        composeTestRule.onNodeWithText("2.5").assertExists()
    }

    @Test
    fun frequencyCard_showsPerBucketEventCounts() {
        // 33 events on one day, 34 on another -> those bars read "33"/"34". Deliberately >31 (the
        // heatmap's max day-of-month) so neither literal can collide with a heatmap day cell
        // elsewhere on the same tab; a smaller pair of counts would be ambiguous with those cells.
        setInsightsTabContent(
            events = List(33) { eventAt(2) } + List(34) { eventAt(1) },
        )

        composeTestRule.onNodeWithText("33").assertExists()
        composeTestRule.onNodeWithText("34").assertExists()
    }

    @Test
    fun gapsCard_showsExactGapAndStreakDayCounts() {
        // Active days at daysAgo 24,23,22 (a 3-day run), 14, 4 -- "now" sits 4 days past the last one.
        // Streak runs: [3,1,1] -> longest 3, average (3+1+1)/3 = 1.6667 -> "1.7 days".
        // Gaps between consecutive events: 1, 1, 8, 10 (past), current = 4.
        // Longest gap = max(past, current) = 10; average of past gaps = (1+1+8+10)/4 = 5.0; current = 4.
        setInsightsTabContent(events = listOf(24L, 23L, 22L, 14L, 4L).map { eventAt(it) })

        composeTestRule.onNodeWithText("10 days").assertExists()
        composeTestRule.onNodeWithText("4 days").assertExists()
        composeTestRule.onNodeWithText("5 days").assertExists()
        composeTestRule.onNodeWithText("3 days").assertExists()
        composeTestRule.onNodeWithText("1.7 days").assertExists()
    }

    @Test
    fun gapsCard_infoIcon_opensAndDismissesDefinitions() {
        // Frequency and Gaps & streaks are the only two cards with an info icon, in that order,
        // so the Gaps one is the last node carrying the shared info-icon description.
        setInsightsTabContent(events = listOf(eventAt(2), eventAt(1)))

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelGaps).performScrollTo()
        composeTestRule
            .onAllNodesWithContentDescription(PlainVoice.caseSectionInfoDescription)
            .onLast()
            .performClick()
        composeTestRule.onNodeWithText(PlainVoice.insightsGapsInfoTitle).assertExists()

        composeTestRule.onNodeWithText(PlainVoice.infoDialogDismissAction).performClick()
        composeTestRule.onNodeWithText(PlainVoice.insightsGapsInfoTitle).assertDoesNotExist()
    }

    // Mirrors InsightsTab.kt's private YearMonth.monthYearLabel() formatting, so the expected text matches exactly.
    private fun monthYearLabel(date: LocalDate): String = "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
}
