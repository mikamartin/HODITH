package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventWithTags
import java.time.LocalDate

/**
 * Spec §10 Trends section: hard ceiling on findings kept at all, across every detector combined —
 * protects the full-list screen (and, once per-tag detectors land, the multiple-comparisons risk
 * of many simultaneous findings) from growing unbounded.
 */
internal const val TRENDS_MAX_FINDINGS = 8

/**
 * Spec §10 Trends section: every eligible finding across all detectors — went-quiet, gap shift,
 * streak shift, and frequency shift (the former standalone Trend arrow, absorbed here rather than
 * kept as its own section) for now (Story C T1, plus the "Case quiet vs. abandoned" resolution).
 * [computeQuietSignal]/[computeGapShift]/[computeStreakShift]/[trendStats] themselves are
 * unchanged; this only wraps their output into [TrendFinding]s and applies [capTrendFindings].
 * [TrendFindingKind.WENT_QUIET] is prepended first (when it fires) rather than appended, so it
 * leads the list — the one finding about the Case's live, still-unresolved state, ahead of every
 * other finding's report on settled history. All four are always [TrendReliability.HINT] today —
 * none runs a significance test, just a descriptive threshold check
 * ([QUIET_SIGNAL_MIN_SAMPLE_COUNT]/[GAP_SHIFT_MIN_SAMPLE_COUNT]/[STREAK_SHIFT_MIN_SAMPLE_COUNT] and
 * [SHIFT_MIN_FRACTION]/[SHIFT_MIN_ABSOLUTE_DAYS] for the shift pair; a flat comparison producing no
 * finding at all for frequency shift) — [TrendReliability.PATTERN] is reserved for a future
 * detector (T4+) that adds a significance test. [trendStats] is the same value the caller
 * separately keeps on `StatsSections.trend` for Share's own mini trend arrow (PROGRESS.md T9
 * retires that once Share moves to these findings too) — passed in rather than recomputed here.
 * [eventsWithTags] backs [computeTagShareShift] (Story C T2), placed after gap/streak/frequency
 * shift since it's the one detector that can contribute more than one finding — every other kind is
 * capped at 0..1. [computeRecurrenceShape] (Story C T3) is always
 * [TrendReliability.HINT] — a self-relative descriptive threshold check
 * ([RECURRENCE_SHAPE_MIN_SAMPLE_COUNT] and the spike/dead-zone share bars), no significance test.
 * [computeTagOutcomeFindings] (Story C T4) is the first detector able to report
 * [TrendReliability.PATTERN], since a result only exists here once it's already cleared a real
 * permutation-significance test; a non-significant candidate never reaches this function at all.
 * [computeChangePoint] (Story C T5) is appended last, the second [TrendReliability.PATTERN]-only
 * detector — same "suppressed, not shown as Hint" rule as tag → outcome, backed by its own
 * timeline-shuffle permutation test rather than tag → outcome's label-shuffle one.
 */
internal fun computeTrendFindings(
    gapStats: GapStats,
    activeDates: List<LocalDate>,
    trendStats: TrendStats?,
    eventsWithTags: List<EventWithTags> = emptyList(),
    recentlyActiveElsewhere: Boolean = false,
): List<TrendFinding> {
    val findings = mutableListOf<TrendFinding>()
    computeQuietSignal(gapStats, recentlyActiveElsewhere)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.WENT_QUIET,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.HINT,
                sampleCount = it.sampleCount,
                priorValue = it.longestPastGapDays.toDouble(),
                recentValue = it.currentGapDays.toDouble(),
            )
    }
    computeGapShift(gapStats.pastGaps)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.GAP_SHIFT,
                direction = it.direction,
                reliability = TrendReliability.HINT,
                sampleCount = it.sampleCount,
                priorValue = it.priorAverageDays,
                recentValue = it.recentAverageDays,
            )
    }
    computeStreakShift(activeDates)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.STREAK_SHIFT,
                direction = it.direction,
                reliability = TrendReliability.HINT,
                sampleCount = it.sampleCount,
                priorValue = it.priorAverageDays,
                recentValue = it.recentAverageDays,
            )
    }
    trendStats?.let {
        val direction =
            when (it.direction) {
                TrendDirection.UP -> ShiftDirection.UP
                TrendDirection.DOWN -> ShiftDirection.DOWN
                TrendDirection.FLAT -> null
            }
        if (direction != null) {
            findings +=
                TrendFinding(
                    kind = TrendFindingKind.FREQUENCY_SHIFT,
                    direction = direction,
                    reliability = TrendReliability.HINT,
                    sampleCount = it.recentCount + it.priorCount,
                    priorValue = it.priorCount.toDouble(),
                    recentValue = it.recentCount.toDouble(),
                )
        }
    }
    computeTagShareShift(eventsWithTags).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TAG_SHARE_SHIFT,
                direction = it.direction,
                reliability = TrendReliability.HINT,
                sampleCount = it.sampleCount,
                priorValue = it.priorShare,
                recentValue = it.recentShare,
                tagName = it.tagName,
            )
    }
    computeRecurrenceShape(gapStats)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.RECURRENCE_SHAPE,
                direction = it.direction,
                reliability = TrendReliability.HINT,
                sampleCount = it.sampleCount,
                priorValue = it.thresholdDays,
                recentValue = it.earlyShare,
            )
    }
    computeTagOutcomeFindings(eventsWithTags).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TAG_OUTCOME,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                sampleCount = it.sampleCount,
                priorValue = it.withoutTagMean,
                recentValue = it.withTagMean,
                tagName = it.tagName,
                outcome = it.outcome,
            )
    }
    computeChangePoint(gapStats, eventsWithTags)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.CHANGE_POINT,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                sampleCount = it.sampleCount,
                priorValue = it.priorAverageDays,
                recentValue = it.recentAverageDays,
                changePointDate = it.changePointDate,
            )
    }
    return capTrendFindings(findings)
}

/** Caps [findings] at [TRENDS_MAX_FINDINGS], factored out so it's unit-testable with synthetic findings today, since only three real detectors exist until T2+. */
internal fun capTrendFindings(findings: List<TrendFinding>): List<TrendFinding> = findings.take(TRENDS_MAX_FINDINGS)
