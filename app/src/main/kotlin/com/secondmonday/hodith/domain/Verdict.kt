package com.secondmonday.hodith.domain

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
 */
data class VerdictResult(
    val tier: ConfidenceTier,
    val eventCount: Int,
    val windowDays: Long,
    val observedRate: Double,
    val expectedRate: Double,
    val comparisonBand: ComparisonBand?,
)
