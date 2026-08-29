package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.GapStats
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS
import com.secondmonday.hodith.domain.RHYTHM_TIER_COUNT
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.computeDurationStats
import com.secondmonday.hodith.domain.computeFrequencyStats
import com.secondmonday.hodith.domain.computeGapShift
import com.secondmonday.hodith.domain.computeGapStats
import com.secondmonday.hodith.domain.computeIntensityStats
import com.secondmonday.hodith.domain.computeRhythmStats
import com.secondmonday.hodith.domain.computeStreakShift
import com.secondmonday.hodith.domain.computeStreakStats
import com.secondmonday.hodith.domain.computeTagBreakdown
import com.secondmonday.hodith.domain.computeTrendStats
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.domain.pickFrequencyGranularity
import com.secondmonday.hodith.domain.weeksInGrid
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * What the Case Detail Insights tab renders (spec §9-10's visuals half), derived fresh from raw
 * data on every read — mirrors [hunchTabState]'s pure-mapping pattern. [NotEnoughData] covers the
 * spec's "friendly placeholder, never an empty chart pretending to mean something" rule.
 */
sealed interface InsightsTabState {
    data object NotEnoughData : InsightsTabState

    data class Ready(
        val heatmapMonths: List<HeatmapMonth>,
        val stats: StatsSections,
    ) : InsightsTabState
}

/**
 * Spec §10's seven stat sections. [trend], [duration], and [intensity] are absent when not
 * applicable. [totalEventCount] gives the tag breakdown a denominator, so an individual tag's
 * count reads against the Case's whole history rather than floating on its own.
 */
data class StatsSections(
    val frequency: FrequencyDisplay,
    val rhythm: RhythmDisplay,
    val gaps: GapsDisplay,
    val trend: TrendDisplay?,
    val duration: DurationDisplay?,
    val intensity: IntensityDisplay?,
    val tags: List<TagBreakdownEntry>,
    val totalEventCount: Int,
)

/** One bar of the frequency-over-time chart. [heightFraction] is relative to the busiest bucket shown. */
data class FrequencyBar(
    val periodStart: LocalDate,
    val count: Int,
    val heightFraction: Float,
)

data class FrequencyDisplay(
    val granularity: FrequencyGranularity,
    val bars: List<FrequencyBar>,
)

/** [level] reuses the heatmap's shared shading scale, relative to this Case's own busiest rhythm cell. */
data class RhythmCellDisplay(
    val dayOfWeek: DayOfWeek,
    val timeOfDay: TimeOfDay,
    val level: HeatmapLevel,
)

/** Always all 28 day-of-week x time-of-day cells, in [DayOfWeek]/[TimeOfDay] enum order. */
data class RhythmDisplay(
    val cells: List<RhythmCellDisplay>,
)

data class GapsDisplay(
    val longestGapDays: Long,
    val currentGapDays: Long,
    val averageGapDays: Double,
    val isBursty: Boolean,
    val longestStreakDays: Int,
    val averageStreakDays: Double,
)

/** [gapShiftDirection]/[streakShiftDirection] are `null` when no noticeable shift was found (or gated off, same as [direction]). */
data class TrendDisplay(
    val direction: TrendDirection,
    val recentCount: Int,
    val priorCount: Int,
    val gapShiftDirection: ShiftDirection?,
    val streakShiftDirection: ShiftDirection?,
)

data class DurationDisplay(
    val averageMinutes: Double,
    val longestMinutes: Long,
    val totalMinutes: Long,
)

/** [maxCount] is the busiest single intensity bucket, for normalizing the distribution's mini-bars. */
data class IntensityDisplay(
    val averageIntensity: Double,
    val distribution: Map<Int, Int>,
    val maxCount: Int,
)

/** One month of the calendar heatmap. `null` day entries are out-of-month padding (spec §9's grid rule). */
data class HeatmapMonth(
    val month: YearMonth,
    val weeks: List<List<HeatmapDay?>>,
)

data class HeatmapDay(
    val date: LocalDate,
    val level: HeatmapLevel,
)

internal fun insightsTabState(
    case: CaseEntity,
    eventsWithTags: List<EventWithTags>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    frequencyGranularityOverride: FrequencyGranularity? = null,
): InsightsTabState {
    val events = eventsWithTags.map { it.event }
    if (events.size < INSIGHTS_MIN_EVENTS) return InsightsTabState.NotEnoughData

    val countsByDay = events.groupingBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.eachCount()
    val maxDailyCount = countsByDay.values.maxOrNull() ?: 0
    val gapStats = computeGapStats(events, now, zone, eventActiveNow = ongoingEventIn(case, events) != null)

    return InsightsTabState.Ready(
        heatmapMonths = heatmapMonths(case, countsByDay, maxDailyCount, now, zone),
        stats = statsSections(case, eventsWithTags, events, gapStats, countsByDay.keys.toList(), now, zone, frequencyGranularityOverride),
    )
}

/** Maps spec §10's seven pure domain stats onto display-ready models, gating duration/intensity on the Case's config. */
private fun statsSections(
    case: CaseEntity,
    eventsWithTags: List<EventWithTags>,
    events: List<EventEntity>,
    gapStats: GapStats,
    activeDates: List<LocalDate>,
    now: Long,
    zone: ZoneId,
    frequencyGranularityOverride: FrequencyGranularity?,
): StatsSections {
    val spanDays = observationSpanDays(events, case.createdAt, now, zone)

    val frequencyStats =
        computeFrequencyStats(
            events,
            now,
            spanDays,
            granularity = frequencyGranularityOverride ?: pickFrequencyGranularity(spanDays),
            zone = zone,
        )
    val maxBucketCount = frequencyStats.buckets.maxOf { it.count }.coerceAtLeast(1)
    val frequency =
        FrequencyDisplay(
            granularity = frequencyStats.granularity,
            bars =
                frequencyStats.buckets.map { bucket ->
                    FrequencyBar(bucket.periodStart, bucket.count, bucket.count.toFloat() / maxBucketCount)
                },
        )

    val rhythmStats = computeRhythmStats(events, zone)
    val rhythm =
        RhythmDisplay(
            cells =
                rhythmStats.cells.map { cell ->
                    val level = heatmapLevelFor(cell.count, rhythmStats.maxCount, tierCount = RHYTHM_TIER_COUNT)
                    RhythmCellDisplay(cell.dayOfWeek, cell.timeOfDay, level)
                },
        )

    val streakStats = computeStreakStats(activeDates)
    val gaps =
        GapsDisplay(
            longestGapDays = gapStats.longestGapDays,
            currentGapDays = gapStats.currentGapDays,
            averageGapDays = gapStats.averageGapDays,
            isBursty = gapStats.isBursty,
            longestStreakDays = streakStats.longestStreakDays,
            averageStreakDays = streakStats.averageStreakDays,
        )

    val trend =
        computeTrendStats(events, now, spanDays)?.let {
            TrendDisplay(
                direction = it.direction,
                recentCount = it.recentCount,
                priorCount = it.priorCount,
                gapShiftDirection = computeGapShift(gapStats.pastGaps),
                streakShiftDirection = computeStreakShift(activeDates),
            )
        }

    val duration =
        if (case.durationMode != DurationMode.NONE) {
            computeDurationStats(events)?.let { DurationDisplay(it.averageMinutes, it.longestMinutes, it.totalMinutes) }
        } else {
            null
        }

    val intensity =
        if (case.intensityEnabled) {
            computeIntensityStats(events)?.let { stats ->
                IntensityDisplay(stats.averageIntensity, stats.distribution, stats.distribution.values.max())
            }
        } else {
            null
        }

    return StatsSections(
        frequency = frequency,
        rhythm = rhythm,
        gaps = gaps,
        trend = trend,
        duration = duration,
        intensity = intensity,
        tags = computeTagBreakdown(eventsWithTags),
        totalEventCount = events.size,
    )
}

/**
 * Stacks a month grid per month from the Case's earliest activity (creation or first retro-logged
 * event, whichever is earlier) through the current month, each day shaded relative to this Case's
 * own busiest day — reuses [weeksInGrid]'s Monday-start padding so the layout matches Big Picture.
 */
private fun heatmapMonths(
    case: CaseEntity,
    countsByDay: Map<LocalDate, Int>,
    maxDailyCount: Int,
    now: Long,
    zone: ZoneId,
): List<HeatmapMonth> {
    val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val currentMonth = YearMonth.from(nowDate)
    val caseCreatedDate = Instant.ofEpochMilli(case.createdAt).atZone(zone).toLocalDate()
    val firstEventDate = countsByDay.keys.minOrNull()
    val earliestMonth = YearMonth.from(if (firstEventDate != null) minOf(firstEventDate, caseCreatedDate) else caseCreatedDate)

    val months = generateSequence(earliestMonth) { it.plusMonths(1) }.takeWhile { !it.isAfter(currentMonth) }.toList()
    return months.map { month ->
        HeatmapMonth(
            month = month,
            weeks =
                weeksInGrid(month)
                    .map { week ->
                        week.map { date ->
                            if (date.isAfter(nowDate) || date.month != month.month) {
                                null
                            } else {
                                HeatmapDay(date = date, level = heatmapLevelFor(countsByDay[date] ?: 0, maxDailyCount))
                            }
                        }
                    }
                    // weeksInGrid always returns full-month rows; trim trailing rows that are
                    // entirely in the future so an in-progress month doesn't end in blank rows.
                    .dropLastWhile { week -> week.all { it == null } },
        )
    }
}
