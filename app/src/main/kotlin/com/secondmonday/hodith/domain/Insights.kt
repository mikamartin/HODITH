package com.secondmonday.hodith.domain

/**
 * Spec §10 "gaps & streaks" stat card: how long since the last event, compared against the
 * longest gap ever seen across the Case's full history. [averageGapDays] and [isBursty] serve the
 * same card; [pastGaps] (each consecutive event-to-event gap, in days) backs the Trend card's
 * gap-shift note ([computeGapShift]).
 */
data class GapStats(
    val currentGapDays: Long,
    val longestGapDays: Long,
    val isCurrentGapLongest: Boolean,
    val averageGapDays: Double,
    val isBursty: Boolean,
    val pastGaps: List<Long>,
)

/**
 * Spec §10 "gaps & streaks" stat card: a streak is a run of consecutive calendar days that each
 * have at least one event. [longestStreakDays] is the longest such run; [averageStreakDays] the
 * mean run length across the Case's full history.
 */
data class StreakStats(
    val longestStreakDays: Int,
    val averageStreakDays: Double,
)

/** Which way a gap or streak length has shifted between the earlier and more recent half of a Case's history. */
enum class ShiftDirection {
    UP,
    DOWN,
}

/**
 * Spec §10 heatmap shading: a day's event count bucketed relative to the Case's own busiest day,
 * into 20 shaded tiers (plus [EMPTY]) — ordinal order matters, [heatmapLevelFor] indexes into
 * [entries] directly rather than branching on each one by name. Most consumers (calendar heatmap,
 * intensity) only ever bucket into the first 10 tiers; Rhythm alone uses the full 20 for finer
 * shading (see [RHYTHM_TIER_COUNT]).
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
    L11,
    L12,
    L13,
    L14,
    L15,
    L16,
    L17,
    L18,
    L19,
    L20,
}
