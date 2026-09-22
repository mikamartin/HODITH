package com.secondmonday.hodith.domain

import java.time.DayOfWeek
import java.time.LocalDate

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
 * [computeRecurrenceShape]: whether this Case's past gaps form an early-spike or dead-zone pattern.
 * [thresholdDays] is the self-relative early-gap boundary this Case's own average gap produced;
 * [earlyShare] the fraction of [sampleCount] past gaps that landed at or under it.
 */
data class RecurrenceShapeResult(
    val direction: ShiftDirection,
    val thresholdDays: Double,
    val earlyShare: Double,
    val sampleCount: Int,
)

/** Spec §10 Trends "tag → outcome" finding (Story C T4): which per-event measure a tag is compared against. */
enum class TagOutcome {
    INTENSITY,
    DURATION,
}

/**
 * [computeTagOutcomeFindings]: whether a tag's presence shifts one outcome's mean, backed by a
 * label-shuffle permutation test rather than a threshold check — the reason [TagOutcomeResult],
 * unlike every other result here, carries no `Hint`-only guarantee: a result only exists once it has
 * already cleared the permutation test's significance bar, so every one becomes a `Pattern` finding.
 * [withoutTagMean]/[withTagMean] are the two group means being compared (in [outcome]'s own unit —
 * a 1–5 intensity score, or minutes for duration), not a shift over time. [sampleCount] is the total
 * events behind both groups combined.
 */
data class TagOutcomeResult(
    val tagName: String,
    val outcome: TagOutcome,
    val direction: ShiftDirection,
    val withoutTagMean: Double,
    val withTagMean: Double,
    val sampleCount: Int,
)

/**
 * [computeChangePoint]: the best-supported split point a CUSUM walk over [GapStats.pastGaps] found,
 * once it's cleared a timeline-shuffle permutation test — unlike [ShiftResult] (a fixed-midpoint
 * split), the split location itself is part of what was computed, so [changePointDate] carries the
 * calendar date of the event that starts the recent segment. [priorAverageDays]/[recentAverageDays]
 * are the two segments' own average gap length, the same unit [ShiftResult] uses. [sampleCount] is
 * the total past gaps behind both segments combined.
 */
data class ChangePointResult(
    val direction: ShiftDirection,
    val changePointDate: LocalDate,
    val priorAverageDays: Double,
    val recentAverageDays: Double,
    val sampleCount: Int,
)

/**
 * [computeTrendSlopeFindings] (Story C T6): a real slope over time in one outcome's per-event
 * values, backed by a permutation test on an OLS-slope statistic rather than a threshold check —
 * like [TagOutcomeResult], a result only exists once it's already cleared significance, so every one
 * is a `Pattern` finding. [priorValue]/[recentValue] are the time-ordered first-half/second-half
 * averages the slope was computed from (real observed numbers, not a fitted-line extrapolation), in
 * [outcome]'s own unit. [sampleCount] is the total events behind both halves combined.
 */
data class TrendSlopeResult(
    val outcome: TagOutcome,
    val direction: ShiftDirection,
    val priorValue: Double,
    val recentValue: Double,
    val sampleCount: Int,
)

/**
 * [computeTimeOfDaySplitFindings] (Story C T6): whether one outcome's per-event values differ
 * between day ([TimeOfDay.MORNING]/[TimeOfDay.AFTERNOON]) and evening
 * ([TimeOfDay.EVENING]/[TimeOfDay.NIGHT]) events, backed by the same label-shuffle permutation test
 * [TagOutcomeResult] uses (day/evening standing in for untagged/tagged) — a result only exists once
 * it's already cleared significance, so every one is a `Pattern` finding. [direction]
 * [ShiftDirection.UP] means the evening mean is higher, [ShiftDirection.DOWN] means the day mean is.
 * [dayMean]/[eveningMean] are in [outcome]'s own unit. [sampleCount] is the day-group and
 * evening-group counts combined.
 */
data class TimeOfDaySplitResult(
    val outcome: TagOutcome,
    val direction: ShiftDirection,
    val dayMean: Double,
    val eveningMean: Double,
    val sampleCount: Int,
)

/** Spec §10 Trends "tag timing" finding (Story C T7): which calendar dimension a tag's events are tested for clustering in. */
enum class TagTimingDimension { WEEKDAY, TIME_OF_DAY }

/**
 * [computeTagTimingFindings]: whether a tag's own events cluster into one bucket of [dimension]
 * beyond the Case's overall rhythm there. Exactly one of [weekday]/[timeOfDay] is set, matching
 * [dimension]. [baselineShare]/[taggedShare] are that one bucket's share of the Case's own events
 * overall vs. the tag's own events (fractions, 0.0-1.0) — like [TagOutcomeResult], a result only
 * exists once it's already cleared a permutation significance test, so every one is a `Pattern`
 * finding. [sampleCount] is the tagged-event count the peak bucket was computed from.
 */
data class TagTimingResult(
    val tagName: String,
    val dimension: TagTimingDimension,
    val weekday: DayOfWeek? = null,
    val timeOfDay: TimeOfDay? = null,
    val baselineShare: Double,
    val taggedShare: Double,
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
