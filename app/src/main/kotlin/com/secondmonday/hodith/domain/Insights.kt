package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import java.time.LocalDate

/**
 * Spec §9 dot timeline: the lookback window ending at "now" and the events that fall inside it.
 * [events] is sorted ascending by [EventEntity.occurredAt].
 */
data class TimelineWindow(
    val windowDays: Long,
    val events: List<EventEntity>,
)

/**
 * A calendar day's events collapsed into one dot timeline entry — several events on the same day
 * render as a single, more-heavily-shaded dot rather than several overlapping ones (spec §9).
 * [representativeMillis] is that day's earliest event, used to position the dot in time.
 */
data class TimelineDayGroup(
    val date: LocalDate,
    val representativeMillis: Long,
    val count: Int,
)

/**
 * Spec §9's "current gap annotated" rule: how long since the last event, compared against the
 * longest gap ever seen across the Case's full history (not just [TimelineWindow]'s events).
 * [averageGapDays] and [isBursty] serve spec §10's "gaps & clusters" stat card.
 */
data class GapStats(
    val currentGapDays: Long,
    val longestGapDays: Long,
    val isCurrentGapLongest: Boolean,
    val averageGapDays: Double,
    val isBursty: Boolean,
)

/**
 * Spec §9/§10 heatmap shading: a day's event count bucketed relative to the Case's own busiest day,
 * into 10 shaded tiers (plus [EMPTY]) — ordinal order matters, [heatmapLevelFor] indexes into
 * [entries] directly rather than branching on each one by name.
 */
enum class HeatmapLevel {
    EMPTY,
    L1,
    L2,
    L3,
    L4,
    L5,
    L6,
    L7,
    L8,
    L9,
    L10,
}
