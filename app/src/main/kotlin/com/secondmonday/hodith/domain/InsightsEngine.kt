package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** Spec §9: below this many events, a Case's visuals/stats have no gap or pattern to show yet. */
internal const val INSIGHTS_MIN_EVENTS = 2

/** Spec §10 heatmap: number of non-empty shaded tiers most consumers bucket into ([HeatmapLevel.L1]..[HeatmapLevel.L10]), by ratio to the Case's own busiest day in range. */
internal const val HEATMAP_TIER_COUNT = 10

/** Spec §10 Rhythm heatmap: Rhythm alone buckets into twice as many tiers as [HEATMAP_TIER_COUNT], for finer shading between nearby counts. */
internal const val RHYTHM_TIER_COUNT = 20

/**
 * Spec §10 "tends to come in bursts" flag: past gaps need at least this many data points before
 * variance is meaningful, and their coefficient of variation (stddev ÷ mean) must clear this bar —
 * above 1.0 means the spread is wider than the average gap itself, i.e. long quiet stretches
 * punctuated by clusters, rather than a steady rhythm.
 */
internal const val GAP_BURST_MIN_GAP_COUNT = 3
internal const val GAP_BURST_MIN_COEFFICIENT_OF_VARIATION = 1.0

/**
 * Spec §10 Trend card's gap/streak shift note: needs at least this many past gaps (or streak runs)
 * before a first-half-vs-second-half comparison is meaningful — same reasoning as
 * [GAP_BURST_MIN_GAP_COUNT], just a higher bar since a half/half split halves the sample further.
 */
internal const val GAP_SHIFT_MIN_SAMPLE_COUNT = 6
internal const val STREAK_SHIFT_MIN_SAMPLE_COUNT = 6

/** A shift only counts as "noticeable" once it clears both a relative and an absolute floor — the former ignores small-value swings, the latter ignores day-scale noise on already-long gaps/streaks. */
internal const val SHIFT_MIN_FRACTION = 0.3
internal const val SHIFT_MIN_ABSOLUTE_DAYS = 1.0

/**
 * Current gap vs. the longest gap ever observed across the Case's full history — the "current gap
 * annotated" rule (spec §10's gaps & streaks card): "how long since the last event ended" compared
 * against "the longest stretch since it started".
 *
 * A gap is silence, so it is measured from when an event *ended*: for a duration event the quiet
 * stretch starts at [EventEntity.endedAt], not [EventEntity.occurredAt], so an event that ran for
 * days and stopped today leaves a current gap of `0`, not "days since it began". Point events (no
 * `endedAt`) end where they start, so their behaviour is unchanged. Overlapping durations are
 * handled by measuring each gap from the furthest end reached so far, not the previous event's
 * end — a short event nested inside a longer one doesn't split one silence into two.
 *
 * When [eventActiveNow] is set (the Case has an event running right now — spec §6), there is no
 * silence to measure: [GapStats.currentGapDays] is `0`, that still-running stretch is left out of
 * [GapStats.longestGapDays], and it can't be flagged as [GapStats.isCurrentGapLongest]. The
 * past gaps ([GapStats.pastGaps], [GapStats.averageGapDays], [GapStats.isBursty]) are unaffected
 * by the flag — the gap that ended when the running event started is real history.
 */
internal fun computeGapStats(
    events: List<EventEntity>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    eventActiveNow: Boolean = false,
): GapStats {
    val sorted = events.sortedBy { it.occurredAt }

    // Each past gap is the silence *before* an event: its start minus the furthest point any
    // earlier event reached (its endedAt, or its own start). Tracking the running furthest-reach
    // rather than just the previous event's end is what makes overlapping durations behave — a
    // short event nested inside a longer one (two overlapping family sick days, say) mustn't
    // invent a gap the longer event was still filling. A start that predates the reach floors to 0.
    // A stored endedAt earlier than its own occurredAt (a bad value from an old round-trip, spec §6)
    // is floored to the start, matching how datesCovered / spansMultipleDays treat a reversed span.
    val pastGaps = mutableListOf<Long>()
    var reachedSoFar = Long.MIN_VALUE
    for ((index, event) in sorted.withIndex()) {
        if (index > 0) {
            pastGaps += daysBetween(reachedSoFar, event.occurredAt, zone).coerceAtLeast(0L)
        }
        reachedSoFar = maxOf(reachedSoFar, event.occurredAt, event.endedAt ?: event.occurredAt)
    }
    val timeSinceLastEvent = if (sorted.isEmpty()) 0L else daysBetween(reachedSoFar, now, zone).coerceAtLeast(0L)
    val currentGapDays = if (eventActiveNow) 0L else timeSinceLastEvent
    val longestPastGap = pastGaps.maxOrNull() ?: 0L

    return GapStats(
        // With currentGapDays pinned to 0 while an event runs, maxOf leaves the active stretch out.
        currentGapDays = currentGapDays,
        longestGapDays = maxOf(longestPastGap, currentGapDays),
        isCurrentGapLongest = !eventActiveNow && currentGapDays >= longestPastGap,
        averageGapDays = if (pastGaps.isEmpty()) 0.0 else pastGaps.average(),
        isBursty = pastGaps.size >= GAP_BURST_MIN_GAP_COUNT && coefficientOfVariation(pastGaps) > GAP_BURST_MIN_COEFFICIENT_OF_VARIATION,
        pastGaps = pastGaps,
    )
}

/** Population coefficient of variation (stddev ÷ mean) of a set of gap lengths; 0 for an all-zero or empty set. */
private fun coefficientOfVariation(gaps: List<Long>): Double {
    val mean = gaps.average()
    if (mean == 0.0) return 0.0
    val variance = gaps.sumOf { (it - mean) * (it - mean) } / gaps.size
    return sqrt(variance) / mean
}

/**
 * Spec §10 gaps & streaks card: a streak is a run of consecutive calendar days each covered by at
 * least one event's active span (spec §9). [activeDates] is that set of covered days from the
 * caller and need not be sorted or distinct.
 */
internal fun computeStreakStats(activeDates: List<LocalDate>): StreakStats {
    val runs = consecutiveRunLengths(activeDates)
    return StreakStats(
        longestStreakDays = runs.maxOrNull() ?: 0,
        averageStreakDays = if (runs.isEmpty()) 0.0 else runs.average(),
    )
}

/** Each maximal run of back-to-back calendar days in [dates], as its day-count — e.g. `[3, 1, 2]` for three runs of those lengths. */
private fun consecutiveRunLengths(dates: List<LocalDate>): List<Int> {
    val sorted = dates.distinct().sorted()
    if (sorted.isEmpty()) return emptyList()

    val runs = mutableListOf<Int>()
    var runLength = 1
    for (i in 1 until sorted.size) {
        if (sorted[i].toEpochDay() == sorted[i - 1].toEpochDay() + 1) {
            runLength++
        } else {
            runs += runLength
            runLength = 1
        }
    }
    runs += runLength
    return runs
}

/**
 * Spec §10 Trend card: whether [pastGaps]' average has shifted noticeably between the earlier and
 * more recent half of the Case's history — `null` below [GAP_SHIFT_MIN_SAMPLE_COUNT] gaps, or when
 * the shift doesn't clear [shiftDirectionFor]'s thresholds.
 */
internal fun computeGapShift(pastGaps: List<Long>): ShiftDirection? {
    if (pastGaps.size < GAP_SHIFT_MIN_SAMPLE_COUNT) return null
    val mid = pastGaps.size / 2
    return shiftDirectionFor(
        firstAvg = pastGaps.take(mid).average(),
        secondAvg = pastGaps.takeLast(pastGaps.size - mid).average(),
    )
}

/** As [computeGapShift], but over [activeDates]' streak run lengths rather than event-to-event gaps. */
internal fun computeStreakShift(activeDates: List<LocalDate>): ShiftDirection? {
    val runs = consecutiveRunLengths(activeDates)
    if (runs.size < STREAK_SHIFT_MIN_SAMPLE_COUNT) return null
    val mid = runs.size / 2
    return shiftDirectionFor(
        firstAvg = runs.take(mid).map { it.toDouble() }.average(),
        secondAvg = runs.takeLast(runs.size - mid).map { it.toDouble() }.average(),
    )
}

/** `null` unless the change from [firstAvg] to [secondAvg] clears both [SHIFT_MIN_FRACTION] and [SHIFT_MIN_ABSOLUTE_DAYS]. */
private fun shiftDirectionFor(
    firstAvg: Double,
    secondAvg: Double,
): ShiftDirection? {
    val delta = secondAvg - firstAvg
    val fraction = if (firstAvg == 0.0) Double.POSITIVE_INFINITY else abs(delta) / firstAvg
    if (abs(delta) < SHIFT_MIN_ABSOLUTE_DAYS || fraction < SHIFT_MIN_FRACTION) return null
    return if (delta > 0) ShiftDirection.UP else ShiftDirection.DOWN
}

/** Buckets [count] relative to [maxCountInRange] into one of [HeatmapLevel]'s [tierCount] shaded tiers. */
internal fun heatmapLevelFor(
    count: Int,
    maxCountInRange: Int,
    tierCount: Int = HEATMAP_TIER_COUNT,
): HeatmapLevel {
    if (count <= 0 || maxCountInRange <= 0) return HeatmapLevel.EMPTY
    val ratio = count.toDouble() / maxCountInRange
    val tier = ceil(ratio * tierCount).toInt().coerceIn(1, tierCount)
    return HeatmapLevel.entries[tier]
}
