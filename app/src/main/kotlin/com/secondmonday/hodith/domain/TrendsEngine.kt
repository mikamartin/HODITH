package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventWithTags
import java.time.LocalDate
import kotlin.math.abs

/**
 * Spec §10 Trends section: hard ceiling on findings kept at all, across every detector combined —
 * protects the full-list screen (and, once per-tag detectors land, the multiple-comparisons risk
 * of many simultaneous findings) from growing unbounded.
 */
internal const val TRENDS_MAX_FINDINGS = 8

/**
 * Spec §10 Trends section: every eligible finding across all detectors — went-quiet, gap shift,
 * streak shift, and frequency shift for now (Story C T1, plus the "Case quiet vs. abandoned"
 * resolution). [computeQuietSignal]/[computeGapShift]/[computeStreakShift]/[trendStats] themselves
 * are unchanged; this only wraps their output into [TrendFinding]s and applies [capTrendFindings].
 * [TrendFindingKind.WENT_QUIET] is prepended first (when it fires) rather than appended, so it
 * leads the list — the one finding about the Case's live, still-unresolved state, ahead of every
 * other finding's report on settled history. The four it shares with the Hint detectors are
 * [TrendReliability.HINT]: none runs a significance test, just a descriptive threshold check
 * ([QUIET_SIGNAL_MIN_SAMPLE_COUNT]/[GAP_SHIFT_MIN_SAMPLE_COUNT]/[STREAK_SHIFT_MIN_SAMPLE_COUNT] and
 * [SHIFT_MIN_FRACTION]/[SHIFT_MIN_ABSOLUTE_DAYS] for the shift pair; a flat comparison producing no
 * finding at all for frequency shift). [trendStats] is passed in rather than recomputed
 * here, since the caller already computed it for its own frequency-shift wiring.
 * [eventsWithTags] backs [computeTagShareShift] (Story C T2), placed after gap/streak/frequency
 * shift since it's the one detector that can contribute more than one finding — every other kind is
 * capped at 0..1. [computeCommonTagCombos] (Story C T9) is appended right after tag share shift —
 * both are tag-based [TrendReliability.HINT] detectors — and can likewise contribute more than one
 * finding (capped at its own [TAG_COMBO_MAX_FINDINGS]); its direction is an unused sentinel (see
 * [TrendFinding]'s own KDoc). [computeRecurrenceShape] (Story C T3) is always
 * [TrendReliability.HINT] — a self-relative descriptive threshold check
 * ([RECURRENCE_SHAPE_MIN_SAMPLE_COUNT] and the spike/dead-zone share bars), no significance test.
 * [computeTagOutcomeFindings] (Story C T4) is the first detector able to report
 * [TrendReliability.PATTERN], since a result only exists here once it's already cleared a real
 * permutation-significance test; a non-significant candidate never reaches this function at all.
 * [computeChangePoint] (Story C T5) is appended after tag → outcome, the second
 * [TrendReliability.PATTERN]-only detector — same "suppressed, not shown as Hint" rule as tag →
 * outcome, backed by its own timeline-shuffle permutation test rather than tag → outcome's
 * label-shuffle one. [computeTrendSlopeFindings]/[computeTimeOfDaySplitFindings] (Story C T6) are
 * appended last, gated per outcome on [statsShownOutcomes] (only outcomes whose stat card the caller
 * already shows) — both are also always [TrendReliability.PATTERN], the first reusing the shared
 * permutation engine directly with its own OLS-slope statistic, the second reusing
 * [labelShufflePValue] as-is with day/evening groups standing in for untagged/tagged.
 * [computeTagTimingFindings] (Story C T7) is appended after tag → outcome — a categorical clustering
 * test rather than a difference-in-means one, so like trend slope it calls the shared permutation
 * engine directly with its own statistic rather than [labelShufflePValue]; not gated on
 * [statsShownOutcomes], since it's not outcome-typed. Always [TrendReliability.PATTERN] for the same
 * "suppressed, not shown as Hint" reason as every other permutation-backed detector.
 * [computeWeekdayWeekendFindings] (Story C T8, the scoped fallback from that item's cycles/
 * seasonality investigation) is appended last — case-wide like change point, not per-tag or
 * per-outcome, so it's not gated on [statsShownOutcomes] either. Also calls the shared permutation
 * engine directly with its own Monte Carlo binomial statistic, the same "no third bespoke test"
 * precedent tag timing and trend slope both set. Always [TrendReliability.PATTERN] for the same
 * reason as every other permutation-backed detector.
 */
internal fun computeTrendFindings(
    gapStats: GapStats,
    activeDates: List<LocalDate>,
    trendStats: TrendStats?,
    eventsWithTags: List<EventWithTags> = emptyList(),
    recentlyActiveElsewhere: Boolean = false,
    statsShownOutcomes: Set<TagOutcome> = emptySet(),
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
                latestEvidenceAt = caseLatestActivityAt(gapStats),
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
                latestEvidenceAt = caseLatestActivityAt(gapStats),
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
                latestEvidenceAt = caseLatestActivityAt(gapStats),
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
                    latestEvidenceAt = caseLatestActivityAt(gapStats),
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
                latestEvidenceAt = it.latestEvidenceAt,
            )
    }
    computeCommonTagCombos(eventsWithTags).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TAG_COMBO,
                direction = ShiftDirection.UP, // unused sentinel -- see TrendFinding's KDoc.
                reliability = TrendReliability.HINT,
                sampleCount = it.count,
                priorValue = it.count.toDouble(),
                recentValue = it.totalEvents.toDouble(),
                tagNames = it.tagNames,
                latestEvidenceAt = it.latestEvidenceAt,
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
                latestEvidenceAt = caseLatestActivityAt(gapStats),
            )
    }
    computeTagOutcomeFindings(eventsWithTags).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TAG_OUTCOME,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                pValue = it.pValue,
                sampleCount = it.sampleCount,
                priorValue = it.withoutTagMean,
                recentValue = it.withTagMean,
                tagName = it.tagName,
                outcome = it.outcome,
                latestEvidenceAt = it.latestEvidenceAt,
            )
    }
    computeChangePoint(gapStats, eventsWithTags)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.CHANGE_POINT,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                pValue = it.pValue,
                sampleCount = it.sampleCount,
                priorValue = it.priorAverageDays,
                recentValue = it.recentAverageDays,
                changePointDate = it.changePointDate,
                latestEvidenceAt = caseLatestActivityAt(gapStats),
            )
    }
    computeTrendSlopeFindings(eventsWithTags, statsShownOutcomes).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TREND_SLOPE,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                pValue = it.pValue,
                sampleCount = it.sampleCount,
                priorValue = it.priorValue,
                recentValue = it.recentValue,
                outcome = it.outcome,
                latestEvidenceAt = it.latestEvidenceAt,
            )
    }
    computeTimeOfDaySplitFindings(eventsWithTags, statsShownOutcomes).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TIME_OF_DAY_SPLIT,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                pValue = it.pValue,
                sampleCount = it.sampleCount,
                priorValue = it.dayMean,
                recentValue = it.eveningMean,
                outcome = it.outcome,
                latestEvidenceAt = it.latestEvidenceAt,
            )
    }
    computeTagTimingFindings(eventsWithTags).forEach {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.TAG_TIMING,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.PATTERN,
                pValue = it.pValue,
                sampleCount = it.sampleCount,
                priorValue = it.baselineShare,
                recentValue = it.taggedShare,
                tagName = it.tagName,
                weekday = it.weekday,
                timeOfDay = it.timeOfDay,
                latestEvidenceAt = it.latestEvidenceAt,
            )
    }
    computeWeekdayWeekendFindings(eventsWithTags)?.let {
        findings +=
            TrendFinding(
                kind = TrendFindingKind.WEEKDAY_WEEKEND_SPLIT,
                direction = it.direction,
                reliability = TrendReliability.PATTERN,
                pValue = it.pValue,
                sampleCount = it.sampleCount,
                priorValue = it.baselineShare,
                recentValue = it.observedShare,
                latestEvidenceAt = it.latestEvidenceAt,
            )
    }
    return capTrendFindings(findings)
}

/**
 * The Case's most recent activity, for the detectors that compare its whole history. Fails loudly on
 * a Case with no events rather than inventing a date: every finding that calls this already needs
 * at least one past gap, so `null` here means [GapStats] and the event list disagree.
 */
private fun caseLatestActivityAt(gapStats: GapStats): Long =
    checkNotNull(gapStats.lastActivityAt) { "A Trends finding needs a Case with at least one event" }

/**
 * Orders [findings] by strength, then caps them at [TRENDS_MAX_FINDINGS]. Sorting comes first so the
 * cap keeps the strongest findings rather than whichever detector happened to run first.
 */
internal fun capTrendFindings(findings: List<TrendFinding>): List<TrendFinding> =
    findings.sortedWith(TREND_FINDING_ORDER).take(TRENDS_MAX_FINDINGS)

/**
 * Display order for trend findings: [TrendFindingKind.WENT_QUIET] always leads (it describes the
 * Case's live state), then Pattern findings by ascending p-value (smaller is stronger), then Hint
 * findings by descending size of change. Findings still tied after that order by most recent
 * evidence, newest first ([TrendFinding.latestEvidenceAt]). Any remaining tie keeps detector order,
 * since the sort is stable.
 */
internal val TREND_FINDING_ORDER: Comparator<TrendFinding> =
    compareBy<TrendFinding> { groupRank(it) }
        .thenBy { it.pValue ?: Double.MAX_VALUE }
        .thenByDescending { hintEffectSize(it) }
        .thenByDescending { it.latestEvidenceAt }

private fun groupRank(finding: TrendFinding): Int =
    when {
        finding.kind == TrendFindingKind.WENT_QUIET -> 0
        finding.reliability == TrendReliability.PATTERN -> 1
        else -> 2
    }

/**
 * Relative size of a Hint's before/after change. [TrendFindingKind.TAG_COMBO] and
 * [TrendFindingKind.RECURRENCE_SHAPE] have no before/after pair on the same scale, so they rank as 0.0.
 */
private fun hintEffectSize(finding: TrendFinding): Double =
    when {
        finding.kind == TrendFindingKind.TAG_COMBO || finding.kind == TrendFindingKind.RECURRENCE_SHAPE -> 0.0
        finding.priorValue == 0.0 -> Double.MAX_VALUE
        else -> abs(finding.recentValue - finding.priorValue) / abs(finding.priorValue)
    }
