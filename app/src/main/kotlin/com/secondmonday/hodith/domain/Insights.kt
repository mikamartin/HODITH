package com.secondmonday.hodith.domain

/**
 * Spec §10 "gaps & streaks" stat card: how long since the last event, compared against the
 * longest gap ever seen across the Case's full history. [averageGapDays] and [isBursty] serve the
 * same card; [pastGaps] (each consecutive event-to-event gap, in days) backs the Trend card's
 * gap-shift note ([computeGapShift]).
 *
 * While an event is running on the Case, [currentGapDays] is `0` and the active stretch is kept
 * out of [longestGapDays] / [isCurrentGapLongest] — see [computeGapStats]'s `eventActiveNow`.
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
 * [computeGapShift]/[computeStreakShift]: a detected shift plus the two half-averages (in days) it
 * was computed from, so a caller can back the shift's sentence with real numbers rather than
 * direction alone. [sampleCount] is the total gaps (or streak runs) behind both halves combined —
 * the same count [GAP_SHIFT_MIN_SAMPLE_COUNT]/[STREAK_SHIFT_MIN_SAMPLE_COUNT] gate on.
 */
data class ShiftResult(
    val direction: ShiftDirection,
    val priorAverageDays: Double,
    val recentAverageDays: Double,
    val sampleCount: Int,
)

/** [computeQuietSignal]: the Case's still-open current gap, next to the longest past gap it beat, plus [sampleCount] past gaps behind that record. */
data class QuietSignalResult(
    val currentGapDays: Long,
    val longestPastGapDays: Long,
    val sampleCount: Int,
)

/**
 * [computeTagShareShift]: one tag's share of the Case's own events (fraction 0.0–1.0) in the
 * earlier vs. more recent half of its history. Unlike [ShiftResult], this is one of *several*
 * results a single call can return — one per tag that clears the bar — so [sampleCount] is the
 * total events behind the half/half split (the same for every tag from one call), not the tag's
 * own occurrence count.
 */
data class TagShareShiftResult(
    val tagName: String,
    val direction: ShiftDirection,
    val priorShare: Double,
    val recentShare: Double,
    val sampleCount: Int,
)

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
