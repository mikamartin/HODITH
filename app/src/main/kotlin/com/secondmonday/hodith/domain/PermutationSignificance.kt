package com.secondmonday.hodith.domain

import kotlin.math.abs
import kotlin.random.Random

/**
 * Spec §10 Trends: shared Monte Carlo significance engine behind any detector that asks "how likely
 * is an effect this large under a null a random shuffle could produce". [observedStatistic] is the
 * real effect the caller already computed; [iterations] repeated calls to [permutedStatistic] (each
 * handed the same seeded [Random], so the sequence is reproducible) generate the null distribution.
 * The result is a two-tailed p-value: the share of permuted statistics at least as extreme (by
 * absolute value) as the observed one. Label-shuffle ([labelShufflePValue], Story C T4) and a future
 * timeline-shuffle (T5) differ only in what [permutedStatistic] does with the [Random] it's given —
 * this function doesn't know or care which.
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
