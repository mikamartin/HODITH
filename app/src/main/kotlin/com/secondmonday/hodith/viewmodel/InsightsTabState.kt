package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.domain.GapStats
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS
import com.secondmonday.hodith.domain.TimelineWindow
import com.secondmonday.hodith.domain.computeGapStats
import com.secondmonday.hodith.domain.computeTimelineWindow
import com.secondmonday.hodith.domain.groupEventsByDay
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.domain.weeksInGrid
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val MILLIS_PER_DAY = 86_400_000L

/** A gap token never collapses to zero width — an always-visible sliver still reads as "no wait here". */
private const val MIN_GAP_WEIGHT = 0.02f

/**
 * What the Case Detail Insights tab renders (spec §9-10's visuals half), derived fresh from raw
 * data on every read — mirrors [hunchTabState]'s pure-mapping pattern. [NotEnoughData] covers the
 * spec's "friendly placeholder, never an empty chart pretending to mean something" rule.
 */
sealed interface InsightsTabState {
    data object NotEnoughData : InsightsTabState

    data class Ready(
        val timeline: TimelineDisplay,
        val heatmapMonths: List<HeatmapMonth>,
    ) : InsightsTabState
}

/** [tokens] alternate leading-gap/dot/gap/.../dot/trailing-gap; the "now" tick is drawn after the last token. */
data class TimelineDisplay(
    val tokens: List<TimelineToken>,
    val windowDays: Long,
    val currentGapDays: Long,
    val isCurrentGapLongest: Boolean,
)

sealed interface TimelineToken {
    /** [level] is shared with the heatmap's shading scale, so "darker" means the same thing in both visuals. */
    data class Dot(
        val level: HeatmapLevel,
    ) : TimelineToken

    /** [weight] is this gap's share of the timeline's total width, for a Row's `Modifier.weight`. */
    data class Gap(
        val weight: Float,
    ) : TimelineToken
}

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
    events: List<EventEntity>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): InsightsTabState {
    if (events.size < INSIGHTS_MIN_EVENTS) return InsightsTabState.NotEnoughData

    val countsByDay = events.groupingBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }.eachCount()
    val maxDailyCount = countsByDay.values.maxOrNull() ?: 0

    return InsightsTabState.Ready(
        timeline =
            timelineDisplay(
                computeTimelineWindow(events, now, zone),
                computeGapStats(events, now, zone),
                maxDailyCount,
                now,
                zone,
            ),
        heatmapMonths = heatmapMonths(case, countsByDay, maxDailyCount, now, zone),
    )
}

private fun timelineDisplay(
    window: TimelineWindow,
    gapStats: GapStats,
    maxDailyCount: Int,
    now: Long,
    zone: ZoneId,
): TimelineDisplay {
    val windowStart = now - window.windowDays * MILLIS_PER_DAY
    val totalSpan = (now - windowStart).toFloat().coerceAtLeast(1f)

    val tokens = mutableListOf<TimelineToken>()
    var cursor = windowStart
    for (group in groupEventsByDay(window.events, zone)) {
        tokens += TimelineToken.Gap(((group.representativeMillis - cursor) / totalSpan).coerceAtLeast(MIN_GAP_WEIGHT))
        tokens += TimelineToken.Dot(heatmapLevelFor(group.count, maxDailyCount))
        cursor = group.representativeMillis
    }
    tokens += TimelineToken.Gap(((now - cursor) / totalSpan).coerceAtLeast(MIN_GAP_WEIGHT))

    return TimelineDisplay(
        tokens = tokens,
        windowDays = window.windowDays,
        currentGapDays = gapStats.currentGapDays,
        isCurrentGapLongest = gapStats.isCurrentGapLongest,
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
                weeksInGrid(month).map { week ->
                    week.map { date ->
                        if (date.isAfter(nowDate) || date.month != month.month) {
                            null
                        } else {
                            HeatmapDay(date = date, level = heatmapLevelFor(countsByDay[date] ?: 0, maxDailyCount))
                        }
                    }
                },
        )
    }
}
