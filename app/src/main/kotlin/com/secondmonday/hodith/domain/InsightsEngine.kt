package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Spec §9: below this many events, a Case's timeline/heatmap have no gap or pattern to show yet. */
internal const val INSIGHTS_MIN_EVENTS = 2

/** Spec §9 dot timeline: default lookback window before density-based shrinking. */
internal const val TIMELINE_DEFAULT_WINDOW_DAYS = 35L

/** Spec §9 dot timeline: the window shrinks (never grows) to keep at most this many dots on screen. */
internal const val TIMELINE_MAX_DOTS = 24

/** Spec §9 dot timeline: the window never shrinks below this floor, even for very dense logging. */
internal const val TIMELINE_MIN_WINDOW_DAYS = 1L

/** Spec §10 heatmap: 4-level shading, bucketed by ratio to the Case's own busiest day in range. */
internal const val HEATMAP_L1_MAX_RATIO = 0.25
internal const val HEATMAP_L2_MAX_RATIO = 0.5
internal const val HEATMAP_L3_MAX_RATIO = 0.75

/**
 * Picks the dot timeline's lookback window (spec §9): starts at [TIMELINE_DEFAULT_WINDOW_DAYS],
 * then shrinks toward [TIMELINE_MIN_WINDOW_DAYS] until at most [TIMELINE_MAX_DOTS] events fall
 * inside it, so a dense Case still reads as individual bursts rather than a solid smear of dots.
 */
internal fun computeTimelineWindow(
    events: List<EventEntity>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): TimelineWindow {
    val sorted = events.sortedBy { it.occurredAt }
    val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val defaultCutoffMillis =
        nowDate
            .minusDays(TIMELINE_DEFAULT_WINDOW_DAYS)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    val withinDefault = sorted.filter { it.occurredAt >= defaultCutoffMillis }

    if (withinDefault.size <= TIMELINE_MAX_DOTS) {
        return TimelineWindow(windowDays = TIMELINE_DEFAULT_WINDOW_DAYS, events = withinDefault)
    }

    val capped = withinDefault.takeLast(TIMELINE_MAX_DOTS)
    val earliestDate = Instant.ofEpochMilli(capped.first().occurredAt).atZone(zone).toLocalDate()
    val shrunkDays = ChronoUnit.DAYS.between(earliestDate, nowDate).coerceAtLeast(TIMELINE_MIN_WINDOW_DAYS)
    return TimelineWindow(windowDays = shrunkDays, events = capped)
}

/**
 * Current gap vs. the longest gap ever observed across the Case's full history — [events] need
 * not be limited to [TimelineWindow], since "the longest stretch since it started" (spec §9) is
 * an all-time comparison, not a windowed one.
 */
internal fun computeGapStats(
    events: List<EventEntity>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): GapStats {
    val sorted = events.sortedBy { it.occurredAt }

    fun daysBetween(
        fromMillis: Long,
        toMillis: Long,
    ): Long {
        val fromDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val toDate = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(fromDate, toDate)
    }

    val pastGaps = sorted.zipWithNext { a, b -> daysBetween(a.occurredAt, b.occurredAt) }
    val currentGapDays = sorted.lastOrNull()?.let { daysBetween(it.occurredAt, now) } ?: 0L
    val longestPastGap = pastGaps.maxOrNull() ?: 0L

    return GapStats(
        currentGapDays = currentGapDays,
        longestGapDays = maxOf(longestPastGap, currentGapDays),
        isCurrentGapLongest = currentGapDays >= longestPastGap,
    )
}

/**
 * Collapses [events] onto their calendar days, in ascending date order — the dot timeline draws
 * one dot per [TimelineDayGroup] rather than one per event, so a day with several events shows as
 * a single, more heavily shaded dot instead of a cluster of overlapping ones.
 */
internal fun groupEventsByDay(
    events: List<EventEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TimelineDayGroup> =
    events
        .sortedBy { it.occurredAt }
        .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, dayEvents) ->
            TimelineDayGroup(date = date, representativeMillis = dayEvents.first().occurredAt, count = dayEvents.size)
        }

/** Buckets [count] relative to [maxCountInRange] into one of [HeatmapLevel]'s four shaded tiers. */
internal fun heatmapLevelFor(
    count: Int,
    maxCountInRange: Int,
): HeatmapLevel {
    if (count <= 0 || maxCountInRange <= 0) return HeatmapLevel.EMPTY
    val ratio = count.toDouble() / maxCountInRange
    return when {
        ratio <= HEATMAP_L1_MAX_RATIO -> HeatmapLevel.L1
        ratio <= HEATMAP_L2_MAX_RATIO -> HeatmapLevel.L2
        ratio <= HEATMAP_L3_MAX_RATIO -> HeatmapLevel.L3
        else -> HeatmapLevel.L4
    }
}
