package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.domain.ComparisonBand

/** Spec §13's two share-card canvases — Story allows the toggleable Hunch vs. Reality beat, Square never does. */
enum class ShareCardFormat {
    STORY,
    SQUARE,
}

/** Spec §13's checklist-driven Insights section picker — mirrors [StatsSections]' six optional sections. */
enum class ShareInsightsSection {
    FREQUENCY,
    RHYTHM,
    GAPS,
    TREND,
    DURATION,
    INTENSITY,
}

/**
 * The share card's top beat: either the expected-vs-observed rate pair (only ever on [ShareCardFormat.STORY],
 * and only when there's a resolved-band active Hunch and the user has it toggled on), or the plain
 * event-count/observation-length fallback shown whenever Hunch vs. Reality isn't — spec §13's product-owner
 * call to always have at least one beat rather than risk an empty card.
 */
sealed interface ShareTopBeat {
    data class HunchVsReality(
        val hunch: HunchEntity,
        val observedRate: Double,
        val band: ComparisonBand,
    ) : ShareTopBeat

    data class Reality(
        val eventCount: Int,
        val observedDays: Long,
    ) : ShareTopBeat
}

/** What [ui.share.ShareCardTemplate] renders — a fixed-order subset of the real Insights tab's sections. */
data class ShareCardData(
    val format: ShareCardFormat,
    val caseIcon: String,
    val caseName: String,
    val topBeat: ShareTopBeat,
    val frequency: FrequencyDisplay?,
    val rhythm: RhythmDisplay?,
    val gaps: GapsDisplay?,
    val trend: TrendDisplay?,
    val duration: DurationDisplay?,
    val intensity: IntensityDisplay?,
)

/**
 * Assembles spec §13's share card content purely by filtering the same [insightsTabState]/[hunchTabState]
 * output Case Detail's Insights/Hunch tabs already compute — no new domain math. [displayName] is separate
 * from [CaseEntity.name] so the share screen's editable name field never mutates the actual Case.
 * [eventCount]/[observedDays]
 * mirror the Log tab summary line's inputs (`events.size`/`observationSpanDays`), since [StatsSections.totalEventCount]
 * is unavailable whenever [insightsState] is [InsightsTabState.NotEnoughData] but the Reality beat still needs
 * to show the true count. A section is only included when both the caller selected it (spec §13: notes/tags
 * never offered; Duration/Intensity only offered when the Case tracks them) and it's actually present in
 * [insightsState] — sections absent from the Case's config are already `null` in [StatsSections].
 */
internal fun shareCardState(
    case: CaseEntity,
    displayName: String,
    insightsState: InsightsTabState,
    hunchState: HunchTabState,
    eventCount: Int,
    observedDays: Long,
    format: ShareCardFormat,
    selectedSections: Set<ShareInsightsSection>,
    showHunchVsReality: Boolean,
): ShareCardData {
    val stats = (insightsState as? InsightsTabState.Ready)?.stats

    val topBeat =
        if (format == ShareCardFormat.STORY && showHunchVsReality && hunchState is HunchTabState.Verdict) {
            ShareTopBeat.HunchVsReality(
                hunch = hunchState.hunch,
                observedRate = hunchState.result.observedRate,
                band =
                    requireNotNull(hunchState.result.comparisonBand) {
                        "HunchTabState.Verdict is only reached once computeVerdict yields a comparisonBand"
                    },
            )
        } else {
            ShareTopBeat.Reality(eventCount = eventCount, observedDays = observedDays)
        }

    return ShareCardData(
        format = format,
        caseIcon = case.icon,
        caseName = displayName,
        topBeat = topBeat,
        frequency = stats?.frequency?.takeIf { ShareInsightsSection.FREQUENCY in selectedSections },
        rhythm = stats?.rhythm?.takeIf { ShareInsightsSection.RHYTHM in selectedSections },
        gaps = stats?.gaps?.takeIf { ShareInsightsSection.GAPS in selectedSections },
        trend = stats?.trend?.takeIf { ShareInsightsSection.TREND in selectedSections },
        duration = stats?.duration?.takeIf { ShareInsightsSection.DURATION in selectedSections },
        intensity = stats?.intensity?.takeIf { ShareInsightsSection.INTENSITY in selectedSections },
    )
}
