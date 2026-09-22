package com.secondmonday.hodith.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Spec §10 Trends section: which detector produced a [TrendFinding] — the UI keys its sentence and
 * info dialog off this. [FREQUENCY_SHIFT] absorbs the former standalone Trend arrow card's own
 * 30-vs-30-day comparison — there is no separate arrow section any more, per the resolved design
 * decision that this was one finding among the section's others all along, not a distinct feature.
 * Unlike [GAP_SHIFT]/[STREAK_SHIFT], a flat (no real change) frequency comparison produces no
 * finding at all, the same "silent when nothing moved" rule the other two already follow.
 *
 * [WENT_QUIET] is the one kind not about a shift between two halves of completed history — it's
 * whether the Case's current, still-open silence is a record for that Case, while the user is
 * still active elsewhere (spec's "Case quiet vs. abandoned" resolution: a Trends finding, not a
 * notification, so it never touches check-ins or [com.secondmonday.hodith.data.TriggerKind]).
 * `priorValue`/`recentValue` hold the longest-past-gap/current-gap pair (days), the same
 * days-based convention [GAP_SHIFT] uses, and [TrendFinding.direction] is always [ShiftDirection.UP]
 * (silence only ever grows until a new event closes it).
 *
 * [TAG_SHARE_SHIFT] is the one kind that can produce more than one finding per Case — one per tag
 * whose share of the Case's events shifted noticeably (Story C T2) — so [TrendFinding.tagName] is
 * set (every other kind leaves it `null`), and `priorValue`/`recentValue` hold the tag's share as a
 * fraction (0.0–1.0), not days or a count.
 *
 * [RECURRENCE_SHAPE] (Story C T3) is a distribution-shape claim over the Case's full past-gap
 * history, not a shift between two halves of it — `priorValue` holds the self-relative early-gap day
 * boundary this Case's own average gap produced ([RECURRENCE_SHAPE_EARLY_FRACTION_OF_MEAN] of it),
 * `recentValue` the observed share of past gaps that landed at or under it (0.0–1.0).
 * [TrendFinding.direction] is [ShiftDirection.UP] for an early-spike pattern (recurrence usually
 * follows quickly) and [ShiftDirection.DOWN] for a dead-zone pattern (it almost never does). Distinct
 * from [WENT_QUIET]: that's a live-instance claim about right now, this is a pattern true regardless
 * of current state, and a Case can trigger both at once.
 *
 * [TAG_OUTCOME] (Story C T4) compares a tag's events against the Case's other events on one outcome
 * ([TrendFinding.outcome] — intensity or duration), backed by a label-shuffle permutation test rather
 * than a descriptive threshold — the roster's first kind able to report [TrendReliability.PATTERN].
 * A pair that misses significance produces no finding at all, never [TrendReliability.HINT], so every
 * [TAG_OUTCOME] finding that exists is already [TrendReliability.PATTERN]. Like [TAG_SHARE_SHIFT],
 * [TrendFinding.tagName] is set (every other kind leaves it `null`); `priorValue`/`recentValue` hold
 * the without-tag/with-tag group means in the outcome's own unit (a 1–5 intensity score, or minutes),
 * not a shift over time. Also like [TAG_SHARE_SHIFT], one Case can surface more than one [TAG_OUTCOME]
 * finding (one per qualifying tag/outcome pair).
 *
 * [CHANGE_POINT] (Story C T5) finds *where* in the Case's own gap history ([GapStats.pastGaps]) a
 * real shift happened, rather than assuming it split at the midpoint the way [GAP_SHIFT] does — a
 * CUSUM walk over the gap sequence picks the best-supported split point, backed by a timeline-shuffle
 * permutation test (a sibling of [TAG_OUTCOME]'s label-shuffle one), so like [TAG_OUTCOME] a
 * candidate that misses significance produces no finding at all and every kept [CHANGE_POINT]
 * finding is already [TrendReliability.PATTERN]. `priorValue`/`recentValue` hold the two segments'
 * average gap length in days, the same convention [GAP_SHIFT] uses; unlike every other kind, the
 * calendar date of the split itself matters for the sentence ("since around mid-March"), which is
 * why [TrendFinding.changePointDate] exists — `null` for every other kind. Additive to
 * [FREQUENCY_SHIFT], not a replacement: this measures whether the *typical gap* has shifted, catching
 * slow drift a fixed 30-vs-30-day window can't see, while [FREQUENCY_SHIFT] stays the simple
 * immediate-window signal.
 *
 * [TREND_SLOPE] (Story C T6) is a real slope over time in intensity or duration's own per-event
 * values ([TrendFinding.outcome], the same discriminator [TAG_OUTCOME] uses), backed by a
 * permutation test on an OLS-slope statistic that reuses the shared engine both [TAG_OUTCOME] and
 * [CHANGE_POINT] sit on directly, rather than either of their typed wrappers — the T6 feasibility
 * ruling's "no third bespoke test" call. Like [TAG_OUTCOME]/[CHANGE_POINT], a candidate that misses
 * significance produces no finding at all, so every kept [TREND_SLOPE] finding is already
 * [TrendReliability.PATTERN]. `priorValue`/`recentValue` hold the time-ordered first-half/second-half
 * averages the slope was computed from, in [TrendFinding.outcome]'s own unit — the same "two segment
 * averages" convention [CHANGE_POINT] uses for gap-days, applied here to intensity/duration.
 *
 * [TIME_OF_DAY_SPLIT] (Story C T6, alongside [TREND_SLOPE]) compares the same two outcomes between
 * day and evening events — [timeOfDayFor]'s four buckets collapsed to two (day:
 * `MORNING`+`AFTERNOON`, evening: `EVENING`+`NIGHT`), reusing [TAG_OUTCOME]'s own label-shuffle
 * permutation test as-is (day/evening standing in for untagged/tagged). Also always
 * [TrendReliability.PATTERN] for the same "suppressed, not shown as Hint" reason.
 * [TrendFinding.direction] [ShiftDirection.UP] means the evening mean is higher, `DOWN` means the day
 * mean is; `priorValue`/`recentValue` hold the day-group mean and evening-group mean.
 *
 * [TAG_TIMING] (Story C T7) tests whether a tag's own events cluster into one bucket of either the
 * Rhythm heatmap's [TimeOfDay] dimension or its [DayOfWeek] dimension, beyond the Case's own overall
 * rhythm there — a categorical clustering question, not a difference-in-means one, so unlike every
 * other [TrendReliability.PATTERN] kind it doesn't reuse [labelShufflePValue]; it calls
 * [permutationPValue] directly with its own max-bucket-deviation statistic, the same "no third
 * bespoke test" precedent [TREND_SLOPE] set for reusing the shared engine with a custom statistic.
 * Exactly one of [TrendFinding.weekday]/[TrendFinding.timeOfDay] is set, matching which dimension
 * fired; `priorValue`/`recentValue` hold that one bucket's Case-wide share and the tag's own share
 * of it (fractions, 0.0-1.0). [TrendFinding.direction] is always [ShiftDirection.UP] — this only
 * ever tests over-concentration, never under-representation, the same one-directional convention
 * [WENT_QUIET] uses. Like [TAG_SHARE_SHIFT]/[TAG_OUTCOME], one Case can surface more than one
 * [TAG_TIMING] finding (one per qualifying tag/dimension pair).
 */
enum class TrendFindingKind {
    WENT_QUIET,
    GAP_SHIFT,
    STREAK_SHIFT,
    FREQUENCY_SHIFT,
    TAG_SHARE_SHIFT,
    RECURRENCE_SHAPE,
    TAG_OUTCOME,
    CHANGE_POINT,
    TREND_SLOPE,
    TIME_OF_DAY_SPLIT,
    TAG_TIMING,
}

/**
 * Spec §10 Trends section: how much statistical weight a finding carries. Distinct from
 * [ConfidenceTier] — that measures whether there's enough data for a reliable average; this
 * measures whether the *effect itself* has been tested for significance. [HINT] is a detector that
 * crossed its own descriptive threshold with no significance test behind it (every detector today).
 * [PATTERN] is reserved for detectors whose permutation/significance test confirms the effect isn't
 * due to chance.
 */
enum class TrendReliability {
    HINT,
    PATTERN,
}

/**
 * Spec §10 Trends section: one finding from one detector, shown as its own row with its own info
 * dialog. [sampleCount] is the evidence size behind the finding — disclosed alongside
 * [reliability], phrased like `Voice.verdictMeta`. [priorValue]/[recentValue] are the two halves
 * being compared, carried through so the row's own sentence states the shift in real numbers
 * rather than direction alone — days for [TrendFindingKind.GAP_SHIFT]/[TrendFindingKind.STREAK_SHIFT]
 * (from [ShiftResult]'s two half-averages), event counts for [TrendFindingKind.FREQUENCY_SHIFT]
 * (from `TrendStats`' `recentCount`/`priorCount`), a share fraction for
 * [TrendFindingKind.TAG_SHARE_SHIFT] (from [TagShareShiftResult]'s `priorShare`/`recentShare`). The
 * UI already dispatches on [kind] to pick the matching Voice sentence, so it also knows which unit
 * these two values are in. For [TrendFindingKind.TAG_OUTCOME], they hold the without-tag/with-tag
 * group means in [outcome]'s own unit instead; for [TrendFindingKind.TREND_SLOPE], the time-ordered
 * first-half/second-half averages, also in [outcome]'s own unit; for
 * [TrendFindingKind.TIME_OF_DAY_SPLIT], the day-group/evening-group means, same unit convention. For
 * [TrendFindingKind.TAG_TIMING], the fired bucket's Case-wide share and the tag's own share of it
 * (fractions, 0.0-1.0).
 * [tagName] is set for [TrendFindingKind.TAG_SHARE_SHIFT], [TrendFindingKind.TAG_OUTCOME], and
 * [TrendFindingKind.TAG_TIMING] — `null` for every other kind, which isn't about one specific tag.
 * [outcome] is set for [TrendFindingKind.TAG_OUTCOME], [TrendFindingKind.TREND_SLOPE], and
 * [TrendFindingKind.TIME_OF_DAY_SPLIT] — `null` for every other kind. [changePointDate] is only set
 * for [TrendFindingKind.CHANGE_POINT] — `null` for every other kind, none of which need a specific
 * calendar date to state their sentence. [weekday]/[timeOfDay] are only set for
 * [TrendFindingKind.TAG_TIMING] — exactly one of the two, matching which dimension fired; `null` for
 * every other kind.
 */
data class TrendFinding(
    val kind: TrendFindingKind,
    val direction: ShiftDirection,
    val reliability: TrendReliability,
    val sampleCount: Int,
    val priorValue: Double,
    val recentValue: Double,
    val tagName: String? = null,
    val outcome: TagOutcome? = null,
    val changePointDate: LocalDate? = null,
    val weekday: DayOfWeek? = null,
    val timeOfDay: TimeOfDay? = null,
)
