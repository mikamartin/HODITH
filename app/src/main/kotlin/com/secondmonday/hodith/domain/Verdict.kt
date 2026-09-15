package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.HunchEntity
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
 * Result of [computeVerdict]. Never stored as its own row. For an active Hunch it's always
 * recomputed live from the Hunch and its Case's events; for a resolved Hunch its fields are
 * snapshotted onto `HunchEntity`'s `resolved*` columns at resolution time and read back from
 * there, so a history entry stays frozen rather than drifting as Events are later edited or
 * deleted (see `viewmodel/HunchTabState.kt`). [comparisonBand] is null exactly when [tier] is
 * [ConfidenceTier.NO_VERDICT]; there isn't yet enough observation to say anything about the Hunch.
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

/** Copies [result] onto this Hunch's `resolved*` snapshot columns. Doesn't touch [HunchEntity.resolvedAt] — set that separately. */
fun HunchEntity.withResolvedVerdictSnapshot(result: VerdictResult): HunchEntity =
    copy(
        resolvedTier = result.tier,
        resolvedEventCount = result.eventCount,
        resolvedActiveDayCount = result.activeDayCount,
        resolvedWindowDays = result.windowDays,
        resolvedObservedRate = result.observedRate,
        resolvedExpectedRate = result.expectedRate,
        resolvedComparisonBand = result.comparisonBand,
        resolvedVerdictSnapshotTaken = true,
    )

/** This Hunch's frozen verdict snapshot, or null if [HunchEntity.resolvedVerdictSnapshotTaken] is false. */
fun HunchEntity.resolvedVerdictSnapshotOrNull(): VerdictResult? {
    if (!resolvedVerdictSnapshotTaken) return null
    return VerdictResult(
        tier = resolvedTier ?: return null,
        metric = metric,
        eventCount = resolvedEventCount ?: return null,
        activeDayCount = resolvedActiveDayCount ?: return null,
        windowDays = resolvedWindowDays ?: return null,
        observedRate = resolvedObservedRate ?: return null,
        expectedRate = resolvedExpectedRate ?: return null,
        comparisonBand = resolvedComparisonBand,
    )
}
