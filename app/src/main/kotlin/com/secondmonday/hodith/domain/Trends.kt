package com.secondmonday.hodith.domain

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
 */
enum class TrendFindingKind {
    WENT_QUIET,
    GAP_SHIFT,
    STREAK_SHIFT,
    FREQUENCY_SHIFT,
    TAG_SHARE_SHIFT,
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
 * these two values are in. [tagName] is only set for [TrendFindingKind.TAG_SHARE_SHIFT] — `null`
 * for every other kind, which isn't about one specific tag.
 */
data class TrendFinding(
    val kind: TrendFindingKind,
    val direction: ShiftDirection,
    val reliability: TrendReliability,
    val sampleCount: Int,
    val priorValue: Double,
    val recentValue: Double,
    val tagName: String? = null,
)
