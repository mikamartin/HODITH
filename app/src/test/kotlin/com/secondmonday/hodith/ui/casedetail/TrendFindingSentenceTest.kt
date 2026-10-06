package com.secondmonday.hodith.ui.casedetail

import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.formatMinutesDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * [trendFindingSentence] maps each [TrendFindingKind] to its [com.secondmonday.hodith.ui.voice.Voice]
 * sentence, with the right arguments in the right order. Each case builds the expected sentence
 * from the same voice call with explicit arguments, so a swapped prior/recent or a dropped
 * formatter fails here instead of only in a UI test.
 */
class TrendFindingSentenceTest {
    private val voice = PlainVoice
    private val locale = Locale.US

    private fun finding(
        kind: TrendFindingKind,
        direction: ShiftDirection = ShiftDirection.UP,
        prior: Double = 3.0,
        recent: Double = 10.0,
        tagName: String? = null,
        outcome: TagOutcome? = null,
        weekday: DayOfWeek? = null,
        timeOfDay: TimeOfDay? = null,
        tagNames: List<String> = emptyList(),
    ) = TrendFinding(
        kind = kind,
        direction = direction,
        reliability = TrendReliability.PATTERN,
        sampleCount = 20,
        priorValue = prior,
        recentValue = recent,
        tagName = tagName,
        outcome = outcome,
        changePointDate = LocalDate.of(2026, 3, 1),
        weekday = weekday,
        timeOfDay = timeOfDay,
        tagNames = tagNames,
    )

    @Test
    fun wentQuiet_readsRecentThenPriorDays() {
        assertEquals(
            voice.insightsWentQuietSentence(formatDays(10.0), formatDays(3.0)),
            trendFindingSentence(finding(TrendFindingKind.WENT_QUIET), voice, locale),
        )
    }

    @Test
    fun gapShift_readsPriorThenRecentDays() {
        assertEquals(
            voice.insightsGapShiftSentence(ShiftDirection.UP, formatDays(3.0), formatDays(10.0)),
            trendFindingSentence(finding(TrendFindingKind.GAP_SHIFT), voice, locale),
        )
    }

    @Test
    fun streakShift_readsPriorThenRecentDays() {
        assertEquals(
            voice.insightsStreakShiftSentence(ShiftDirection.DOWN, formatDays(3.0), formatDays(10.0)),
            trendFindingSentence(finding(TrendFindingKind.STREAK_SHIFT, direction = ShiftDirection.DOWN), voice, locale),
        )
    }

    @Test
    fun frequencyShift_roundsPriorAndRecentToWholeNumbers() {
        assertEquals(
            voice.insightsTrendSentence(TrendDirection.UP, 10, 3),
            trendFindingSentence(finding(TrendFindingKind.FREQUENCY_SHIFT, prior = 2.6, recent = 9.6), voice, locale),
        )
    }

    @Test
    fun tagShareShift_formatsPriorAndRecentAsPercentages() {
        assertEquals(
            voice.insightsTagShareShiftSentence("work", ShiftDirection.UP, formatPercent(0.2), formatPercent(0.5)),
            trendFindingSentence(finding(TrendFindingKind.TAG_SHARE_SHIFT, prior = 0.2, recent = 0.5, tagName = "work"), voice, locale),
        )
    }

    @Test
    fun tagCombo_joinsTagNamesAndRoundsCounts() {
        assertEquals(
            voice.insightsTagComboSentence("work + late", 4, 9),
            trendFindingSentence(
                finding(TrendFindingKind.TAG_COMBO, prior = 4.0, recent = 9.0, tagNames = listOf("work", "late")),
                voice,
                locale,
            ),
        )
    }

    @Test
    fun recurrenceShape_readsDaysThenPercentage() {
        assertEquals(
            voice.insightsRecurrenceShapeSentence(ShiftDirection.UP, formatDays(3.0), formatPercent(0.4)),
            trendFindingSentence(finding(TrendFindingKind.RECURRENCE_SHAPE, prior = 3.0, recent = 0.4), voice, locale),
        )
    }

    @Test
    fun tagOutcome_intensity_usesIntensityLabelsAndRelativeDifference() {
        val prior = 2.0
        val recent = 3.0
        assertEquals(
            voice.insightsTagOutcomeSentence(
                "work",
                TagOutcome.INTENSITY,
                ShiftDirection.UP,
                formatPercent(abs((recent - prior) / prior)),
                formatIntensity(prior),
                formatIntensity(recent),
            ),
            trendFindingSentence(
                finding(TrendFindingKind.TAG_OUTCOME, prior = prior, recent = recent, tagName = "work", outcome = TagOutcome.INTENSITY),
                voice,
                locale,
            ),
        )
    }

    @Test
    fun tagOutcome_duration_usesDurationLabels() {
        assertEquals(
            voice.insightsTagOutcomeSentence(
                "work",
                TagOutcome.DURATION,
                ShiftDirection.DOWN,
                formatPercent(abs((30.0 - 60.0) / 60.0)),
                formatMinutesDuration(60L),
                formatMinutesDuration(30L),
            ),
            trendFindingSentence(
                finding(
                    TrendFindingKind.TAG_OUTCOME,
                    direction = ShiftDirection.DOWN,
                    prior = 60.0,
                    recent = 30.0,
                    tagName = "work",
                    outcome = TagOutcome.DURATION,
                ),
                voice,
                locale,
            ),
        )
    }

    @Test
    fun changePoint_formatsTheDateAsAnApproximateMonth() {
        assertEquals(
            voice.insightsChangePointSentence(
                ShiftDirection.UP,
                formatApproximateMonth(LocalDate.of(2026, 3, 1)),
                formatDays(3.0),
                formatDays(10.0),
            ),
            trendFindingSentence(finding(TrendFindingKind.CHANGE_POINT), voice, locale),
        )
    }

    @Test
    fun trendSlope_intensity_usesIntensityLabels() {
        assertEquals(
            voice.insightsTrendSlopeSentence(TagOutcome.INTENSITY, ShiftDirection.UP, formatIntensity(3.0), formatIntensity(10.0)),
            trendFindingSentence(finding(TrendFindingKind.TREND_SLOPE, outcome = TagOutcome.INTENSITY), voice, locale),
        )
    }

    @Test
    fun timeOfDaySplit_readsDayThenEveningLabels() {
        assertEquals(
            voice.insightsTimeOfDaySplitSentence(TagOutcome.INTENSITY, ShiftDirection.UP, formatIntensity(3.0), formatIntensity(10.0)),
            trendFindingSentence(finding(TrendFindingKind.TIME_OF_DAY_SPLIT, outcome = TagOutcome.INTENSITY), voice, locale),
        )
    }

    @Test
    fun tagTiming_onAWeekday_namesTheWeekdayPlural() {
        assertEquals(
            voice.insightsTagTimingSentence("work", "on Mondays", formatPercent(0.12), formatPercent(0.3)),
            trendFindingSentence(
                finding(TrendFindingKind.TAG_TIMING, prior = 0.12, recent = 0.3, tagName = "work", weekday = DayOfWeek.MONDAY),
                voice,
                locale,
            ),
        )
    }

    @Test
    fun tagTiming_inATimeOfDayBucket_namesTheBucketInLowerCase() {
        assertEquals(
            voice.insightsTagTimingSentence(
                "work",
                "in the ${rhythmTimeOfDayLabel(voice, TimeOfDay.EVENING).lowercase()}",
                formatPercent(0.12),
                formatPercent(0.3),
            ),
            trendFindingSentence(
                finding(TrendFindingKind.TAG_TIMING, prior = 0.12, recent = 0.3, tagName = "work", timeOfDay = TimeOfDay.EVENING),
                voice,
                locale,
            ),
        )
    }

    @Test
    fun weekdayWeekendSplit_givesTheWeekdayShareAsTheRemainder() {
        assertEquals(
            voice.insightsWeekdayWeekendSentence(ShiftDirection.UP, weekdayLabel = formatPercent(0.4), weekendLabel = formatPercent(0.6)),
            trendFindingSentence(finding(TrendFindingKind.WEEKDAY_WEEKEND_SPLIT, recent = 0.6), voice, locale),
        )
    }
}
