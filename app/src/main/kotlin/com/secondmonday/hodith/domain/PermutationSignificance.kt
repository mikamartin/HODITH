package com.secondmonday.hodith.domain

import kotlin.math.abs
import kotlin.random.Random

/**
 * Spec §10 Trends: shared Monte Carlo significance engine behind any detector that asks "how likely
 * is an effect this large under a null a random shuffle could produce". [observedStatistic] is the
 * real effect the caller already computed; [iterations] repeated calls to [permutedStatistic] (each
 * handed the same seeded [Random], so the sequence is reproducible) generate the null distribution.
 * The result is a two-tailed p-value: the share of permuted statistics at least as extreme (by
 * absolute value) as the observed one. Label-shuffle ([labelShufflePValue], Story C T4) and
 * timeline-shuffle ([timelineShufflePValue], Story C T5) differ only in what [permutedStatistic]
 * does with the [Random] it's given — this function doesn't know or care which.
 */
internal fun permutationPValue(
    observedStatistic: Double,
    iterations: Int,
    seed: Long,
    permutedStatistic: (Random) -> Double,
): Double {
    val random = Random(seed)
    val extremeCount = (0 until iterations).count { abs(permutedStatistic(random)) >= abs(observedStatistic) }
    return extremeCount.toDouble() / iterations
}

/**
 * Label-shuffle permutation test (Story C T4's tag→outcome detector): pools [groupA] and [groupB]
 * together and repeatedly reshuffles which pooled value belongs to which group (preserving each
 * group's original size), recomputing [statistic] each time, to test whether the real group split's
 * statistic is more extreme than chance alone would produce.
 */
internal fun labelShufflePValue(
    groupA: List<Double>,
    groupB: List<Double>,
    iterations: Int,
    seed: Long,
    statistic: (List<Double>, List<Double>) -> Double = ::relativeDifferenceInMeans,
): Double {
    val observed = statistic(groupA, groupB)
    val pooled = groupA + groupB
    val sizeA = groupA.size
    return permutationPValue(observed, iterations, seed) { random ->
        val shuffled = pooled.shuffled(random)
        statistic(shuffled.subList(0, sizeA), shuffled.subList(sizeA, shuffled.size))
    }
}

/**
 * Relative (%) difference in means from [a] to [b] — the test statistic Story C T4's feasibility
 * ruling settled on for both its kept outcomes. `0.0` when both means are `0.0` (no difference to
 * report); [Double.POSITIVE_INFINITY] if only [a]'s mean is `0.0` (any nonzero [b] is an infinite
 * relative jump from a zero baseline) — a defensive edge neither real outcome (a 1–5 intensity score,
 * or a positive duration) should ever actually hit, the same guard shape [tagShareShiftDirectionFor]
 * already uses for its own zero-baseline case.
 */
internal fun relativeDifferenceInMeans(
    a: List<Double>,
    b: List<Double>,
): Double {
    val meanA = a.average()
    val meanB = b.average()
    if (meanA == 0.0) return if (meanB == 0.0) 0.0 else Double.POSITIVE_INFINITY
    return (meanB - meanA) / meanA
}

/**
 * Deterministic seed for a permutation test, keyed off exactly what Story C T4's feasibility ruling
 * specifies — [caseId], [tagName], [outcome], and [eventCount] (the sample size actually behind this
 * (tag, outcome) computation) — so a finding doesn't flicker in/out across app opens with unchanged
 * data, but does change whenever the underlying data does. A simple rolling hash, not real randomness.
 */
internal fun permutationSeedFor(
    caseId: Long,
    tagName: String,
    outcome: TagOutcome,
    eventCount: Int,
): Long {
    var hash = caseId
    hash = hash * 31 + tagName.hashCode()
    hash = hash * 31 + outcome.ordinal
    hash = hash * 31 + eventCount
    return hash
}

/**
 * Timeline-shuffle permutation test (Story C T5's change-point detector, the future this file's own
 * doc comment anticipated): repeatedly reshuffles the *order* of [values] (a Case's own past gaps,
 * not a cross-sectional group split) and recomputes [statistic] each time. Since [statistic] is
 * expected to re-run its own search for the best-supported split (as [cusumStatistic] does), the
 * resulting null distribution already accounts for the observed statistic being a maximum over many
 * candidate split points, not one fixed test — the standard fix for a change-point statistic's own
 * multiple-comparisons ("look-elsewhere") problem.
 */
internal fun timelineShufflePValue(
    values: List<Long>,
    iterations: Int,
    seed: Long,
    statistic: (List<Long>) -> Double,
): Double {
    val observed = statistic(values)
    return permutationPValue(observed, iterations, seed) { random -> statistic(values.shuffled(random)) }
}

/**
 * Deterministic seed for [timelineShufflePValue], the same rolling-hash shape [permutationSeedFor]
 * uses — keyed on [caseId] and [gapCount] (the past-gap count actually behind this computation),
 * since a change-point candidate has no tag or outcome to key on.
 */
internal fun timelineShuffleSeedFor(
    caseId: Long,
    gapCount: Int,
): Long {
    var hash = caseId
    hash = hash * 31 + gapCount
    return hash
}

/**
 * Deterministic seed for [computeTrendSlopeFindings]' own direct [permutationPValue] call (Story C
 * T6): the same rolling-hash shape [permutationSeedFor] uses, keyed on [caseId], [outcome], and
 * [sampleCount] — a slope candidate has no tag to key on, but is still per-outcome like tag → outcome.
 */
internal fun trendSlopeSeedFor(
    caseId: Long,
    outcome: TagOutcome,
    sampleCount: Int,
): Long {
    var hash = caseId
    hash = hash * 31 + outcome.ordinal
    hash = hash * 31 + sampleCount
    return hash
}

/**
 * Deterministic seed for [computeTimeOfDaySplitFindings]' [labelShufflePValue] call (Story C T6),
 * the same shape as [trendSlopeSeedFor] — day/evening split has no tag either, just [caseId],
 * [outcome], and [sampleCount].
 */
internal fun timeOfDaySplitSeedFor(
    caseId: Long,
    outcome: TagOutcome,
    sampleCount: Int,
): Long {
    var hash = caseId
    hash = hash * 31 + outcome.ordinal
    hash = hash * 31 + sampleCount
    return hash
}
