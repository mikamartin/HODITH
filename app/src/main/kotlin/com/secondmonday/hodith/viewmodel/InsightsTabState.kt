package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.GapStats
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS
import com.secondmonday.hodith.domain.QUIET_SIGNAL_RECENT_ACTIVITY_WINDOW_DAYS
import com.secondmonday.hodith.domain.RHYTHM_TIER_COUNT
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.activeSpanEnd
import com.secondmonday.hodith.domain.computeDurationStats
import com.secondmonday.hodith.domain.computeFrequencyStats
import com.secondmonday.hodith.domain.computeGapStats
import com.secondmonday.hodith.domain.computeIntensityStats
import com.secondmonday.hodith.domain.computeRhythmStats
import com.secondmonday.hodith.domain.computeStreakStats
import com.secondmonday.hodith.domain.computeTagBreakdown
import com.secondmonday.hodith.domain.computeTrendFindings
import com.secondmonday.hodith.domain.computeTrendStats
import com.secondmonday.hodith.domain.datesCovered
import com.secondmonday.hodith.domain.daysBetween
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.domain.pickFrequencyGranularity
import com.secondmonday.hodith.domain.spansMultipleDays
import com.secondmonday.hodith.domain.weeksInGrid
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * What the Case Detail Insights tab renders (spec §9-10's visuals half), derived fresh from raw
 * data on every read — mirrors [hunchTabState]'s pure-mapping pattern. [NothingLogged] covers the
 * spec's "friendly placeholder, never an empty chart pretending to mean something" rule; from the
 * first event the tab is [Ready] and the calendar heatmap has a cell to show (spec §9).
 */
sealed interface InsightsTabState {
    /** Zero events logged: a flat invitation, never a countdown toward [INSIGHTS_MIN_EVENTS]. */
    data object NothingLogged : InsightsTabState

    /**
     * At least one event. [stats] is always present, but with a single event
     * [StatsSections.frequency] and [StatsSections.trend] are `null` (below [INSIGHTS_MIN_EVENTS]
     * a per-bucket count or a 30-vs-30-day comparison has nothing to say) — the tab then shows
     * only the one-event count note, the Rhythm and Gaps cards, and the heatmap.
     */
    data class Ready(
        val heatmapMonths: List<HeatmapMonth>,
        val stats: StatsSections,
    ) : InsightsTabState
}

/**
 * Spec §10's stat sections, plus Story C T1's Trends section. [frequency], [duration], and
 * [intensity] are absent when not applicable — [frequency] when the Case has a multi-day event
 * (spec §9), since a per-day/week/month count can't say "how often" without double-counting a long
 * event. [totalEventCount] gives the tag breakdown a denominator, so an individual tag's count
 * reads against the Case's whole history rather than floating on its own. [trends], like [tags], is
 * always a non-null `List` — empty (not null) means nothing was found, not "not yet computed."
 *
 * [trend] is no longer read by the Insights tab itself — the former standalone Trend arrow card is
 * gone, its 30-vs-30-day comparison now one more [trends] finding
 * ([com.secondmonday.hodith.domain.TrendFindingKind.FREQUENCY_SHIFT]). The field stays only because
 * `ShareCardState` still sources its own mini trend arrow from it (PROGRESS.md's "Replace the share
 * card's old trend arrow with real Trends findings" item retires this field once Share moves to
 * [trends] too).
 */
data class StatsSections(
    val frequency: FrequencyDisplay?,
    val rhythm: RhythmDisplay,
    val gaps: GapsDisplay,
    val trend: TrendDisplay?,
    val duration: DurationDisplay?,
    val intensity: IntensityDisplay?,
    val tags: List<TagBreakdownEntry>,
    val totalEventCount: Int,
    val trends: List<TrendFinding>,
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

/**
 * [level] reuses the heatmap's shared shading scale, relative to this Case's own busiest rhythm
 * cell. [count] is the raw event count for this day-of-week/time-of-day bucket, kept alongside the
 * derived [level] so a tap can gate on "has events" and a drill-down can be built without
 * recomputing [com.secondmonday.hodith.domain.computeRhythmStats].
 */
data class RhythmCellDisplay(
    val dayOfWeek: DayOfWeek,
    val timeOfDay: TimeOfDay,
    val level: HeatmapLevel,
    val count: Int,
)

/**
 * Always all 28 day-of-week x time-of-day cells, in [DayOfWeek]/[TimeOfDay] enum order.
 * [plottedByStart] is true when the Case has a multi-day event: the grid plots each event's
 * start (it always has), and the card says so by titling itself "Start times" (spec §9).
 */
data class RhythmDisplay(
    val cells: List<RhythmCellDisplay>,
    val plottedByStart: Boolean,
)

data class GapsDisplay(
    val longestGapDays: Long,
    val currentGapDays: Long,
    val averageGapDays: Double,
    val isBursty: Boolean,
    val longestStreakDays: Int,
    val averageStreakDays: Double,
)

data class TrendDisplay(
    val direction: TrendDirection,
    val recentCount: Int,
    val priorCount: Int,
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
    mostRecentActivityAcrossCasesAt: Long? = null,
): InsightsTabState {
    // Spec §9 active span: each event counts on every calendar day it was active, not just its start
    // day. A finished event runs occurredAt..endedAt; a still-running START_STOP event runs to now
    // (matching ongoingEventsIn's rule that only START_STOP can be ongoing); a point event stays on
    // its single day. A Case that no longer tracks duration (durationMode NONE) renders every event
    // as a point whatever endedAt is stored — collapse it here so spanEnd / hasMultiDayEvent /
    // computeGapStats all see point events with no further branching. The stored value is untouched
    // (eventsWithTags still carries it) and the Event duration card is separately gated off below.
    val events =
        eventsWithTags
            .map { it.event }
            .let { list -> if (case.durationMode.tracksDuration) list else list.map { it.copy(endedAt = null) } }
    if (events.isEmpty()) return InsightsTabState.NothingLogged

    fun spanEnd(event: EventEntity) = activeSpanEnd(event, case.durationMode, now)

    // A still-running event's open end is "now," not a captured instant, so it resolves via the
    // live current zone rather than the event's own (possibly stale, pre-travel) offset — matching
    // BigPictureGrid's private coveredDates, which makes the same call for the same reason. Every
    // other event's span resolves in its own captured offset, so it places on the calendar day it
    // actually happened, not wherever the device currently is.
    val ongoingEvents = ongoingEventsIn(case, events).toSet()

    fun endZoneFor(event: EventEntity) = if (event in ongoingEvents) zone else event.loggedZone()

    val countsByDay =
        events
            .flatMap { event -> datesCovered(event.occurredAt, spanEnd(event), event.loggedZone(), endZoneFor(event)) }
            .groupingBy { it }
            .eachCount()
    val maxDailyCount = countsByDay.values.maxOrNull() ?: 0
    val gapStats = computeGapStats(events, now, zone, eventActiveNow = ongoingEvents.isNotEmpty())

    // Any event whose active span crosses a calendar-day boundary makes "how often" ambiguous:
    // frequency-over-time is hidden and the rhythm grid is relabelled to "Start times" (spec §9).
    // A same-day duration event doesn't trip this.
    val hasMultiDayEvent = events.any { spansMultipleDays(it.occurredAt, spanEnd(it), it.loggedZone(), endZoneFor(it)) }

    return InsightsTabState.Ready(
        heatmapMonths = heatmapMonths(case, countsByDay, maxDailyCount, now, zone),
        stats =
            statsSections(
                case,
                eventsWithTags,
                events,
                gapStats,
                countsByDay.keys.toList(),
                hasMultiDayEvent,
                now,
                zone,
                frequencyGranularityOverride,
                mostRecentActivityAcrossCasesAt,
            ),
    )
}

/** Maps spec §10's seven pure domain stats onto display-ready models, gating duration/intensity on the Case's config. */
private fun statsSections(
    case: CaseEntity,
    eventsWithTags: List<EventWithTags>,
    events: List<EventEntity>,
    gapStats: GapStats,
    activeDates: List<LocalDate>,
    hasMultiDayEvent: Boolean,
    now: Long,
    zone: ZoneId,
    frequencyGranularityOverride: FrequencyGranularity?,
    mostRecentActivityAcrossCasesAt: Long?,
): StatsSections {
    val spanDays = observationSpanDays(events, case.createdAt, now, zone)
    // A single event has no bucket-to-bucket shape and no earlier half to compare against, so
    // Frequency and Trend stay hidden until there are at least this many (spec §10).
    val belowStatsMinimum = events.size < INSIGHTS_MIN_EVENTS

    val frequency =
        if (hasMultiDayEvent || belowStatsMinimum) {
            null
        } else {
            val frequencyStats =
                computeFrequencyStats(
                    events,
                    now,
                    spanDays,
                    granularity = frequencyGranularityOverride ?: pickFrequencyGranularity(spanDays),
                    zone = zone,
                )
            val maxBucketCount = frequencyStats.buckets.maxOf { it.count }.coerceAtLeast(1)
            FrequencyDisplay(
                granularity = frequencyStats.granularity,
                bars =
                    frequencyStats.buckets.map { bucket ->
                        FrequencyBar(bucket.periodStart, bucket.count, bucket.count.toFloat() / maxBucketCount)
                    },
            )
        }

    val rhythmStats = computeRhythmStats(events)
    val rhythm =
        RhythmDisplay(
            cells =
                rhythmStats.cells.map { cell ->
                    val level = heatmapLevelFor(cell.count, rhythmStats.maxCount, tierCount = RHYTHM_TIER_COUNT)
                    RhythmCellDisplay(cell.dayOfWeek, cell.timeOfDay, level, cell.count)
                },
            plottedByStart = hasMultiDayEvent,
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

    // Computed once, shared by `trend` (Share's own mini arrow, StatsSections' doc comment) and
    // `trends`' FREQUENCY_SHIFT finding below.
    val trendStatsResult = if (belowStatsMinimum) null else computeTrendStats(events, now, spanDays)
    val trend =
        trendStatsResult?.let {
            TrendDisplay(
                direction = it.direction,
                recentCount = it.recentCount,
                priorCount = it.priorCount,
            )
        }

    val duration =
        if (case.durationMode.tracksDuration) {
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
        trends =
            computeTrendFindings(
                gapStats,
                activeDates,
                trendStatsResult,
                eventsWithTags,
                recentlyActiveElsewhere =
                    mostRecentActivityAcrossCasesAt != null &&
                        daysBetween(mostRecentActivityAcrossCasesAt, now, zone) <= QUIET_SIGNAL_RECENT_ACTIVITY_WINDOW_DAYS,
                // Story C T6: only an outcome whose stat card is already shown is eligible for a
                // trend-slope/time-of-day-split finding -- `duration`/`intensity` above are the same
                // gated values the cards themselves render from.
                statsShownOutcomes =
                    setOfNotNull(
                        TagOutcome.INTENSITY.takeIf { intensity != null },
                        TagOutcome.DURATION.takeIf { duration != null },
                    ),
            ),
    )
}

/**
 * Stacks a month grid per month from the Case's earliest activity (creation or first retro-logged
 * event, whichever is earlier) through the current month, each day shaded by how many events were
 * active that day (spec §9 active span) relative to this Case's own busiest day — reuses
 * [weeksInGrid]'s Monday-start padding so the layout matches Big Picture.
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
