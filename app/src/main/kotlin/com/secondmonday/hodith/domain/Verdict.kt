package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.VerdictMetric

/** Spec §8: how much observation a Hunch has behind it. */
enum class ConfidenceTier {
    NO_VERDICT,
    PRELIMINARY,
    CONFIDENT,
}

/** Spec §8: observed ÷ expected, bucketed for direction-aware rendering in the Voice layer. */
enum class ComparisonBand {
    MUCH_LESS,
    LESS,
    ABOUT_RIGHT,
    MORE,
    MUCH_MORE,
}

/**
 * Result of [computeVerdict]. Never persisted — recomputed from the Hunch and its Case's events
 * every time it's shown. [comparisonBand] is null exactly when [tier] is [ConfidenceTier.NO_VERDICT];
 * there isn't yet enough observation to say anything about the Hunch.
 *
 * [eventCount] and [activeDayCount] are both over the observation window (events outside it are
 * already excluded). [eventCount] is the raw in-window event tally; [activeDayCount] is the count
 * of distinct calendar days any in-window event's active span touched. [metric] says which of the
 * two the tier, rate, and card copy are built from — the other is carried for reference only.
 */
data class VerdictResult(
    val tier: ConfidenceTier,
    val metric: VerdictMetric,
    val eventCount: Int,
    val activeDayCount: Int,
    val windowDays: Long,
    val observedRate: Double,
    val expectedRate: Double,
    val comparisonBand: ComparisonBand?,
)
