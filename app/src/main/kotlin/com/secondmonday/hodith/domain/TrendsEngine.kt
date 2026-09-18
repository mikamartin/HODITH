package com.secondmonday.hodith.domain

import java.time.LocalDate

/**
 * Spec §10 Trends section: hard ceiling on findings kept at all, across every detector combined —
 * protects the full-list screen (and, once per-tag detectors land, the multiple-comparisons risk
 * of many simultaneous findings) from growing unbounded.
 */
internal const val TRENDS_MAX_FINDINGS = 8

/**
 * Spec §10 Trends section: every eligible finding across all detectors — gap shift, streak shift,
 * and frequency shift (the former standalone Trend arrow, absorbed here rather than kept as its
 * own section) for now (Story C T1). [computeGapShift]/[computeStreakShift]/[trendStats] themselves
 * are unchanged; this only wraps their output into [TrendFinding]s and applies [capTrendFindings].
 * All three are always [TrendReliability.HINT] today — none runs a significance test, just a
 * descriptive dual-threshold check ([GAP_SHIFT_MIN_SAMPLE_COUNT]/[STREAK_SHIFT_MIN_SAMPLE_COUNT]
 * and [SHIFT_MIN_FRACTION]/[SHIFT_MIN_ABSOLUTE_DAYS] for the first two; a flat comparison producing
 * no finding at all for the third) — [TrendReliability.PATTERN] is reserved for a future detector
 * (T4+) that adds a significance test. [trendStats] is the same value the caller separately keeps
 * on `StatsSections.trend` for Share's own mini trend arrow (PROGRESS.md T9 retires that once Share
 * moves to these findings too) — passed in rather than recomputed here.
 */
internal fun computeTrendFindings(
    gapStats: GapStats,
    activeDates: List<LocalDate>,
    trendStats: TrendStats?,
): List<TrendFinding> {
    val findings = mutableListOf<TrendFinding>()
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
    return capTrendFindings(findings)
}

/** Caps [findings] at [TRENDS_MAX_FINDINGS], factored out so it's unit-testable with synthetic findings today, since only three real detectors exist until T2+. */
internal fun capTrendFindings(findings: List<TrendFinding>): List<TrendFinding> = findings.take(TRENDS_MAX_FINDINGS)
