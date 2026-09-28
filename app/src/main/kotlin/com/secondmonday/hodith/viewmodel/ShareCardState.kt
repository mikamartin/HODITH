package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.LOG_SHARE_CARD_ENTRY_CAP
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.filterAndSortEvents
import com.secondmonday.hodith.ui.casedetail.trendsVisibleFindings
import com.secondmonday.hodith.ui.voice.Voice
import java.time.ZoneId

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
    TRENDS,
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

/** One formatted row on a Log Share card — [detail] is `null` when every field is off or the event carries none of them. */
data class LogCardRow(
    val timestamp: String,
    val detail: String?,
)

/**
 * What [ui.share.ShareCardTemplate] renders — either spec §13's Insights summary or a Log Share
 * card (a bounded list of the Case's actual entries instead of stats; not a data export — see
 * PROGRESS.md's "Share button: add a Log Share option" item). Both share a case header/footer and
 * theme skin, differing only in body content, hence one sealed type rather than two unrelated ones.
 */
sealed interface ShareCardData {
    val format: ShareCardFormat
    val caseIcon: String
    val caseName: String
    val generatedAtMillis: Long

    /** A fixed-order subset of the real Insights tab's sections. */
    data class Insights(
        override val format: ShareCardFormat,
        override val caseIcon: String,
        override val caseName: String,
        override val generatedAtMillis: Long,
        val topBeat: ShareTopBeat,
        val frequency: FrequencyDisplay?,
        val rhythm: RhythmDisplay?,
        val gaps: GapsDisplay?,
        val trends: List<TrendFinding>,
        val duration: DurationDisplay?,
        val intensity: IntensityDisplay?,
    ) : ShareCardData

    /**
     * [rangeLabel] and [rows] arrive pre-formatted (the caller already resolves "today" for the
     * range picker, so it also resolves the "All time" vs. explicit-dates wording here — see
     * [logShareCardState]). [truncatedTotalCount] is `null` when nothing was cut; otherwise the
     * pre-cap match count, for the card's own "+N more" note.
     */
    data class Log(
        override val format: ShareCardFormat,
        override val caseIcon: String,
        override val caseName: String,
        override val generatedAtMillis: Long,
        val rangeLabel: String,
        val rows: List<LogCardRow>,
        val truncatedTotalCount: Int?,
    ) : ShareCardData
}

/**
 * Assembles spec §13's share card content purely by filtering the same [insightsTabState]/[hunchTabState]
 * output Case Detail's Insights/Hunch tabs already compute — no new domain math. [displayName] is separate
 * from [CaseEntity.name] so the share screen's editable name field never mutates the actual Case.
 * [eventCount]/[observedDays]
 * mirror the Log tab summary line's inputs (`events.size`/`observationSpanDays`), since [StatsSections.totalEventCount]
 * is unavailable whenever [insightsState] is [InsightsTabState.NothingLogged] but the Reality beat still needs
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
    generatedAtMillis: Long,
): ShareCardData.Insights {
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

    return ShareCardData.Insights(
        format = format,
        caseIcon = case.icon,
        caseName = displayName,
        topBeat = topBeat,
        frequency = stats?.frequency?.takeIf { ShareInsightsSection.FREQUENCY in selectedSections },
        rhythm = stats?.rhythm?.takeIf { ShareInsightsSection.RHYTHM in selectedSections },
        gaps = stats?.gaps?.takeIf { ShareInsightsSection.GAPS in selectedSections },
        trends =
            stats
                ?.trends
                ?.takeIf { ShareInsightsSection.TRENDS in selectedSections }
                ?.let { trendsVisibleFindings(it) }
                ?: emptyList(),
        duration = stats?.duration?.takeIf { ShareInsightsSection.DURATION in selectedSections },
        intensity = stats?.intensity?.takeIf { ShareInsightsSection.INTENSITY in selectedSections },
        generatedAtMillis = generatedAtMillis,
    )
}

/**
 * Assembles Log Share's card content: [filterAndSortEvents] (the same reusable filter a future
 * Log-tab-filter item would call) narrows and orders [events], then every match beyond
 * [LOG_SHARE_CARD_ENTRY_CAP] is dropped — a safety ceiling for both formats alike, since Square's
 * `heightIn(min = ...)` is a floor, not a cap, and grows for content exactly like Story (spec §13).
 * Each kept row reuses [eventDetailSummary]'s primitive overload directly: [fields] and the Case's
 * own [CaseEntity.durationMode]/[CaseEntity.intensityEnabled] both gate Duration/Intensity, so
 * neither shows unless the Case tracks it *and* the user left it on. [displayName] arrives
 * pre-resolved, matching how [shareCardState] already takes it resolved rather than deriving it
 * from [CaseEntity.name] itself. [dateFrom]/[dateTo] are the same local-day-boundary millis
 * [LogShareSelection] stores (see [ZoneId.startOfDayMillis]/[ZoneId.endOfDayMillis]); the "All
 * time" vs. explicit-dates wording is resolved here by comparing [dateTo]'s calendar date to
 * [now]'s, the one piece of "today" awareness this function needs.
 */
internal fun logShareCardState(
    case: CaseEntity,
    displayName: String,
    events: List<EventWithTags>,
    format: ShareCardFormat,
    sortOrder: ChronologicalOrder,
    dateFrom: Long?,
    dateTo: Long,
    fields: Set<LogRowField>,
    use24Hour: Boolean,
    voice: Voice,
    now: Long,
    generatedAtMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): ShareCardData.Log {
    val matches = filterAndSortEvents(events, from = dateFrom, to = dateTo, order = sortOrder)
    val capped = matches.take(LOG_SHARE_CARD_ENTRY_CAP)

    return ShareCardData.Log(
        format = format,
        caseIcon = case.icon,
        caseName = displayName,
        generatedAtMillis = generatedAtMillis,
        rangeLabel = logShareRangeLabel(dateFrom, dateTo, now, zone, voice),
        rows = capped.map { logCardRow(it, case, fields, use24Hour, now, voice) },
        truncatedTotalCount = matches.size.takeIf { it > LOG_SHARE_CARD_ENTRY_CAP },
    )
}

/** [Voice.shareLogRangeAllTimeLabel] when [dateFrom] is unset and [dateTo] is today; otherwise each bound formatted via [formatEventDate] ("…" for an unset [dateFrom]). */
private fun logShareRangeLabel(
    dateFrom: Long?,
    dateTo: Long,
    now: Long,
    zone: ZoneId,
    voice: Voice,
): String {
    val isDefaultRange = dateFrom == null && dateTo.toLocalDateIn(zone) == now.toLocalDateIn(zone)
    if (isDefaultRange) return voice.shareLogRangeAllTimeLabel
    val fromLabel = dateFrom?.let { formatEventDate(it, zone) } ?: "…"
    return "$fromLabel – ${formatEventDate(dateTo, zone)}"
}

private fun logCardRow(
    eventWithTags: EventWithTags,
    case: CaseEntity,
    fields: Set<LogRowField>,
    use24Hour: Boolean,
    now: Long,
    voice: Voice,
): LogCardRow {
    val event = eventWithTags.event
    val isOngoing = case.durationMode == DurationMode.START_STOP && event.endedAt == null

    return LogCardRow(
        timestamp = formatEventTime(event.occurredAt, now, use24Hour, zone = event.loggedZone()),
        detail =
            eventDetailSummary(
                occurredAt = event.occurredAt,
                endedAt = event.endedAt,
                intensity = event.intensity,
                note = event.note.takeIf { LogRowField.NOTES in fields },
                tagNames = if (LogRowField.TAGS in fields) eventWithTags.tags.map { it.name } else emptyList(),
                voice = voice,
                isOngoing = isOngoing,
                tracksDuration = case.durationMode.tracksDuration && LogRowField.DURATION in fields,
                showIntensity = case.intensityEnabled && LogRowField.INTENSITY in fields,
            ),
    )
}
