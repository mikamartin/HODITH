package com.secondmonday.hodith.domain

import kotlinx.datetime.TimeZone
import kotlin.math.max

/**
 * Shared across every Trends detector that needs to tell "this shift is still current" apart from
 * "this shift happened, but the evidence for it has gone stale" — a case/tag with naturally long
 * gaps needs a proportionally longer "still current" allowance than one logged daily, so this is
 * always derived from the signal's own rhythm rather than a fixed day count. [CADENCE_RECENCY_FLOOR_DAYS]
 * keeps a fast-cadence signal from getting a near-zero recency window; [CADENCE_RECENCY_MULTIPLIER] is
 * how many typical intervals of grace a signal gets before its most recent instance counts as stale.
 * Both are judgment calls, reviewed and fixed with the product owner — not derived from data.
 */
internal const val CADENCE_RECENCY_FLOOR_DAYS = 3.0
internal const val CADENCE_RECENCY_MULTIPLIER = 2.5

/** Below this many gaps, a "typical interval" doesn't mean anything yet — the same "too thin a sample" reasoning every other descriptive floor in this package uses, at the lowest number for which a median is even defined. */
internal const val CADENCE_MIN_GAP_COUNT = 2

/** A signal's own rhythm: [typicalIntervalDays] (the median inter-arrival gap) and the [recencyThresholdDays] derived from it. */
internal data class CadenceWindow(
    val typicalIntervalDays: Double,
    val recencyThresholdDays: Double,
)

/**
 * The core primitive: [gaps] is an already-computed inter-arrival-gap list in days, e.g.
 * [GapStats.pastGaps] — callers that already have a gap list (gap/streak shift) feed it directly,
 * with no timestamp plumbing needed. `null` below [CADENCE_MIN_GAP_COUNT] gaps.
 *
 * [typicalIntervalDays] is the *median*, not the mean [GapStats.averageGapDays] uses elsewhere in
 * this package — a deliberate divergence. A burst of short gaps pulls a mean down, and the single
 * long trailing silence after it pulls the same mean back up; the median is robust to exactly that
 * "burst, then quiet" shape, which is the one case this whole mechanism exists to catch correctly.
 */
internal fun computeCadenceWindow(
    gaps: List<Long>,
    floorDays: Double = CADENCE_RECENCY_FLOOR_DAYS,
    multiplier: Double = CADENCE_RECENCY_MULTIPLIER,
): CadenceWindow? {
    if (gaps.size < CADENCE_MIN_GAP_COUNT) return null
    val typical = medianOf(gaps)
    return CadenceWindow(typicalIntervalDays = typical, recencyThresholdDays = max(floorDays, multiplier * typical))
}

/**
 * As [computeCadenceWindow], for callers that only have raw occurrence timestamps (case events, or
 * one tag's own occurrences) rather than an already-computed gap list — sorts, diffs into
 * calendar-day gaps via [daysBetween], then delegates. `null` below [CADENCE_MIN_GAP_COUNT] + 1
 * timestamps (too few to produce [CADENCE_MIN_GAP_COUNT] gaps).
 */
internal fun computeCadenceWindowFromTimestamps(
    timestampsMillis: List<Long>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    floorDays: Double = CADENCE_RECENCY_FLOOR_DAYS,
    multiplier: Double = CADENCE_RECENCY_MULTIPLIER,
): CadenceWindow? {
    val sorted = timestampsMillis.sorted()
    if (sorted.size < CADENCE_MIN_GAP_COUNT + 1) return null
    val gaps = sorted.zipWithNext { a, b -> daysBetween(a, b, zone) }
    return computeCadenceWindow(gaps, floorDays, multiplier)
}

/** Whether [daysSinceLatest] (days from a signal's most recent instance to "now") still falls inside [window]'s recency threshold — the boundary itself counts as still current. */
internal fun isWithinCadenceRecency(
    window: CadenceWindow,
    daysSinceLatest: Long,
): Boolean = daysSinceLatest <= window.recencyThresholdDays

/** Median of a non-empty [Long] list (mean of the two middle values when the count is even). */
private fun medianOf(values: List<Long>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid].toDouble()
}
