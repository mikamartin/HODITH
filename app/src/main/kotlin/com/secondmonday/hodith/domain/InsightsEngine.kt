package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.loggedZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Spec §9: the per-case calendar heatmap, its one-line event-count note, and the Rhythm and
 * Gaps & streaks cards render from the first event (as the Big Picture grid does — an empty
 * calendar doesn't pretend to show a pattern). Frequency over time and the Trend arrow need at
 * least this many events before a per-bucket count or a 30-vs-30-day comparison means anything.
 */
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
 * Spec §10 Trends "went quiet" finding: needs at least this many past gaps before "longer than
 * this Case has ever gone before" means anything — same reasoning as [GAP_SHIFT_MIN_SAMPLE_COUNT],
 * kept as its own named constant since the two detectors ask different questions even though they
 * share a value today.
 */
internal const val QUIET_SIGNAL_MIN_SAMPLE_COUNT = 6

/** How recently the user must have logged *something*, on any Case, for a long current gap to read as "this Case specifically went quiet" rather than "the user stopped using the app." */
internal const val QUIET_SIGNAL_RECENT_ACTIVITY_WINDOW_DAYS = 7

/**
 * Spec §10 Trends "recurrence shape" finding (Story C T3): a heavier sibling to
 * [GAP_BURST_MIN_COEFFICIENT_OF_VARIATION]'s bursts flag — also self-relative to a Case's own
 * [GapStats.averageGapDays] rather than a fixed day count, since a Case logged daily and one logged
 * every few months need entirely different "recurs quickly" thresholds. A past gap counts as
 * "early" once it's at or under [RECURRENCE_SHAPE_EARLY_FRACTION_OF_MEAN] of the Case's own average
 * gap. Early-spike fires once [RECURRENCE_SHAPE_SPIKE_MIN_SHARE] or more of past gaps are early —
 * recurrence usually follows quickly. Dead-zone fires once [RECURRENCE_SHAPE_DEAD_ZONE_MAX_SHARE] or
 * fewer are — recurrence within half the average almost never happens — but only once the gaps also
 * clear [RECURRENCE_SHAPE_DEAD_ZONE_MIN_COEFFICIENT_OF_VARIATION]: without that second gate, a Case
 * with a simply *steady* rhythm (every gap close to the mean, near-zero variance) would trigger
 * dead-zone on every call, since a steady Case trivially has no early gaps either — the gate keeps
 * this finding to Cases with real spread in their gaps but a hard floor below which recurrence
 * doesn't happen, not every Case whose rhythm just happens to be regular. The two directions can
 * never both fire from one call: a share can't clear a ≥0.6 and a ≤0.1 bar at once.
 */
internal const val RECURRENCE_SHAPE_MIN_SAMPLE_COUNT = 6
internal const val RECURRENCE_SHAPE_EARLY_FRACTION_OF_MEAN = 0.5
internal const val RECURRENCE_SHAPE_SPIKE_MIN_SHARE = 0.6
internal const val RECURRENCE_SHAPE_DEAD_ZONE_MAX_SHARE = 0.1
internal const val RECURRENCE_SHAPE_DEAD_ZONE_MIN_COEFFICIENT_OF_VARIATION = 0.3

/**
 * Spec §10 Trends "change point" finding (Story C T5): needs roughly twice
 * [GAP_SHIFT_MIN_SAMPLE_COUNT] past gaps before running at all — unlike [computeGapShift]'s trusted
 * midpoint split, a CUSUM walk searches for the best-supported split itself, which is inherently
 * more overfitting-prone at small sample sizes even though the permutation test downstream corrects
 * the statistics; this floor is a cheap first line of defense, not the only one.
 * [CHANGE_POINT_MIN_SEGMENT_COUNT] is the same "3 is the floor before variance means anything" bar
 * [GAP_BURST_MIN_GAP_COUNT]/[TAG_SHARE_SHIFT_MIN_TAG_COUNT] already use, applied here to each side of
 * a candidate split via the edge margin ([computeChangePoint]'s `margin = max(3, n / 5)`).
 * [CHANGE_POINT_MIN_RELATIVE_DIFFERENCE] is its own named constant rather than a reuse of
 * [SHIFT_MIN_FRACTION] — an argmax-selected split's relative difference is systematically inflated
 * versus a fixed-midpoint split for the same underlying noise (searching many candidates finds the
 * most favorable one), so [GAP_SHIFT]'s 0.3 bar would let too much through to the (comparatively
 * expensive) permutation stage; higher here since the permutation test is this detector's real
 * statistical backstop, not this descriptive floor. [CHANGE_POINT_SIGNIFICANCE_ALPHA] and
 * [CHANGE_POINT_PERMUTATION_ITERATIONS] match [TAG_OUTCOME_SIGNIFICANCE_ALPHA]/
 * [TAG_OUTCOME_PERMUTATION_ITERATIONS] — no reason to diverge from the roster's existing precedent.
 */
internal const val CHANGE_POINT_MIN_SAMPLE_COUNT = 12
internal const val CHANGE_POINT_MIN_SEGMENT_COUNT = 3
internal const val CHANGE_POINT_MIN_RELATIVE_DIFFERENCE = 0.4
internal const val CHANGE_POINT_SIGNIFICANCE_ALPHA = 0.05
internal const val CHANGE_POINT_PERMUTATION_ITERATIONS = 1000

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
    // Event-to-event gaps resolve in the newer event's own offset (the silence *before* an event is
    // naturally described in the zone that event itself happened in); the final gap to "now" has no
    // event on that side, so it keeps resolving via the passed-in [zone] (the device's current zone
    // by default) — "now" has no captured offset by definition.
    val pastGaps = mutableListOf<Long>()
    var reachedSoFar = Long.MIN_VALUE
    for ((index, event) in sorted.withIndex()) {
        if (index > 0) {
            pastGaps += daysBetween(reachedSoFar, event.occurredAt, event.loggedZone()).coerceAtLeast(0L)
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
 * the shift doesn't clear [shiftDirectionFor]'s thresholds. The returned [ShiftResult] carries both
 * half-averages so a caller can state the shift in real numbers, not direction alone.
 */
internal fun computeGapShift(pastGaps: List<Long>): ShiftResult? {
    if (pastGaps.size < GAP_SHIFT_MIN_SAMPLE_COUNT) return null
    val mid = pastGaps.size / 2
    val priorAverage = pastGaps.take(mid).average()
    val recentAverage = pastGaps.takeLast(pastGaps.size - mid).average()
    return shiftDirectionFor(priorAverage, recentAverage)?.let { ShiftResult(it, priorAverage, recentAverage, pastGaps.size) }
}

/** As [computeGapShift], but over [activeDates]' streak run lengths rather than event-to-event gaps. */
internal fun computeStreakShift(activeDates: List<LocalDate>): ShiftResult? {
    val runs = consecutiveRunLengths(activeDates)
    if (runs.size < STREAK_SHIFT_MIN_SAMPLE_COUNT) return null
    val mid = runs.size / 2
    val priorAverage = runs.take(mid).map { it.toDouble() }.average()
    val recentAverage = runs.takeLast(runs.size - mid).map { it.toDouble() }.average()
    return shiftDirectionFor(priorAverage, recentAverage)?.let { ShiftResult(it, priorAverage, recentAverage, runs.size) }
}

/**
 * Spec §10 Trends "went quiet" finding: unlike [computeGapShift]/[computeStreakShift], this isn't a
 * shift between two halves of history — it's whether the Case's *current, still-open* silence
 * ([GapStats.isCurrentGapLongest]) is a record, while the user is demonstrably still using the app
 * elsewhere ([recentlyActiveElsewhere]). `null` below [QUIET_SIGNAL_MIN_SAMPLE_COUNT] past gaps (too
 * little history for "longest ever" to mean anything), when the current gap isn't the record (the
 * ordinary case), or when the user hasn't logged anything else recently (reads as "stopped using
 * the app," not "this Case specifically went quiet"). [GapStats.isCurrentGapLongest] is already
 * `false` while an event is actively running, so no separate guard is needed for that here.
 */
internal fun computeQuietSignal(
    gapStats: GapStats,
    recentlyActiveElsewhere: Boolean,
): QuietSignalResult? {
    if (gapStats.pastGaps.size < QUIET_SIGNAL_MIN_SAMPLE_COUNT) return null
    if (!gapStats.isCurrentGapLongest) return null
    if (!recentlyActiveElsewhere) return null
    return QuietSignalResult(
        currentGapDays = gapStats.currentGapDays,
        longestPastGapDays = gapStats.pastGaps.max(),
        sampleCount = gapStats.pastGaps.size,
    )
}

/**
 * Spec §10 Trends "recurrence shape" finding (Story C T3): whether this Case's past gaps cluster
 * into an early-spike pattern (usually recurs within [RECURRENCE_SHAPE_EARLY_FRACTION_OF_MEAN] of
 * its own average gap) or a dead-zone pattern (almost never does, and the gaps have real spread
 * rather than just being steady) — `null` below [RECURRENCE_SHAPE_MIN_SAMPLE_COUNT] past gaps, when
 * the average gap is `0` (nothing to be "early" relative to), or when neither direction's bar is
 * cleared. Distinct from [GapStats.isBursty] (one coefficient-of-variation flag on the whole
 * distribution's spread): this asks specifically about the *short* end of that distribution and
 * states it as a share plus a real day boundary, not a single unitless number.
 */
internal fun computeRecurrenceShape(gapStats: GapStats): RecurrenceShapeResult? {
    val pastGaps = gapStats.pastGaps
    if (pastGaps.size < RECURRENCE_SHAPE_MIN_SAMPLE_COUNT) return null
    val mean = gapStats.averageGapDays
    if (mean <= 0.0) return null

    val thresholdDays = mean * RECURRENCE_SHAPE_EARLY_FRACTION_OF_MEAN
    val earlyShare = pastGaps.count { it <= thresholdDays }.toDouble() / pastGaps.size
    val direction =
        when {
            earlyShare >= RECURRENCE_SHAPE_SPIKE_MIN_SHARE -> ShiftDirection.UP
            earlyShare <= RECURRENCE_SHAPE_DEAD_ZONE_MAX_SHARE &&
                coefficientOfVariation(pastGaps) >= RECURRENCE_SHAPE_DEAD_ZONE_MIN_COEFFICIENT_OF_VARIATION -> ShiftDirection.DOWN
            else -> return null
        }
    return RecurrenceShapeResult(
        direction = direction,
        thresholdDays = thresholdDays,
        earlyShare = earlyShare,
        sampleCount = pastGaps.size,
    )
}

/**
 * Spec §10 Trends "change point" finding (Story C T5): where in [GapStats.pastGaps] a real shift
 * happened, found by a CUSUM walk rather than assumed at the midpoint the way [computeGapShift] does
 * — the cumulative sum of each gap's deviation from the series mean, [cusumStatistic], is walked
 * across every candidate split inside the edge margin (each side needs at least
 * [CHANGE_POINT_MIN_SEGMENT_COUNT] gaps), and the split with the largest absolute cumulative
 * deviation is kept. That split only becomes a finding once its two segments clear a descriptive
 * floor ([CHANGE_POINT_MIN_RELATIVE_DIFFERENCE], via [relativeDifferenceInMeans] — cheap, checked
 * before the comparatively expensive permutation run) and [timelineShufflePValue]'s significance
 * test, which reshuffles the gap sequence's own order and re-walks the same CUSUM search each time —
 * so the resulting null distribution already accounts for the observed statistic being a maximum
 * over many candidate splits, not one fixed test (the standard fix for a change-point statistic's own
 * "look-elsewhere" multiple-comparisons problem).
 *
 * [eventsWithTags] is needed only for its event dates, to resolve the split's calendar date — every
 * other input here comes from [gapStats] alone. [gapStats] and [eventsWithTags] are independent
 * parameters that happen to always be derived from the same event list at the one real call site;
 * this function defends against a mismatched pair (its date lookup would otherwise misread) by
 * checking [GapStats.pastGaps]' size against the sorted event list's before indexing into it.
 */
internal fun computeChangePoint(
    gapStats: GapStats,
    eventsWithTags: List<EventWithTags>,
): ChangePointResult? {
    val pastGaps = gapStats.pastGaps
    if (pastGaps.size < CHANGE_POINT_MIN_SAMPLE_COUNT) return null

    val sorted = eventsWithTags.sortedBy { it.event.occurredAt }
    if (pastGaps.size != sorted.size - 1) return null

    val mean = pastGaps.average()
    if (mean <= 0.0) return null

    val margin = max(CHANGE_POINT_MIN_SEGMENT_COUNT, pastGaps.size / 5)
    if (margin * 2 > pastGaps.size) return null

    val (_, splitIndex) = cusumStatistic(pastGaps, margin)

    val priorGaps = pastGaps.take(splitIndex).map { it.toDouble() }
    val recentGaps = pastGaps.drop(splitIndex).map { it.toDouble() }
    val relativeDifference = relativeDifferenceInMeans(priorGaps, recentGaps)
    if (abs(relativeDifference) < CHANGE_POINT_MIN_RELATIVE_DIFFERENCE) return null

    val caseId = sorted.firstOrNull()?.event?.caseId ?: return null
    val seed = timelineShuffleSeedFor(caseId, pastGaps.size)
    val pValue =
        timelineShufflePValue(pastGaps, CHANGE_POINT_PERMUTATION_ITERATIONS, seed) { shuffled ->
            cusumStatistic(shuffled, margin).first
        }
    if (pValue >= CHANGE_POINT_SIGNIFICANCE_ALPHA) return null

    val priorAverage = priorGaps.average()
    val recentAverage = recentGaps.average()
    val splitEvent = sorted[splitIndex].event
    val changePointDate = Instant.ofEpochMilli(splitEvent.occurredAt).atZone(splitEvent.loggedZone()).toLocalDate()

    return ChangePointResult(
        direction = if (recentAverage > priorAverage) ShiftDirection.UP else ShiftDirection.DOWN,
        changePointDate = changePointDate,
        priorAverageDays = priorAverage,
        recentAverageDays = recentAverage,
        sampleCount = pastGaps.size,
    )
}

/**
 * The CUSUM statistic behind [computeChangePoint]: walks the cumulative sum of each of [gaps]'
 * deviations from their own mean, restricted to split points at least [margin] gaps from either end
 * (so both segments have real support), and returns the largest absolute cumulative deviation
 * reached plus the 1-indexed gap count where it was reached (`gaps[0 until splitIndex]` is the prior
 * segment, `gaps[splitIndex until size]` the recent one). Ties keep the first index reached, since
 * Kotlin's iteration order here is a plain left-to-right walk — a deliberate, deterministic choice,
 * not an arbitrary one: it's the earliest point the evidence for a split becomes maximal.
 */
private fun cusumStatistic(
    gaps: List<Long>,
    margin: Int,
): Pair<Double, Int> {
    val mean = gaps.average()
    var cumulative = 0.0
    var bestStatistic = 0.0
    var bestSplitIndex = margin
    for (index in gaps.indices) {
        cumulative += gaps[index] - mean
        val k = index + 1
        if (k < margin || k > gaps.size - margin) continue
        if (abs(cumulative) > bestStatistic) {
            bestStatistic = abs(cumulative)
            bestSplitIndex = k
        }
    }
    return bestStatistic to bestSplitIndex
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
