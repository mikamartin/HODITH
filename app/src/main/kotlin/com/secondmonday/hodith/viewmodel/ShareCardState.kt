package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.HeroRate
import com.secondmonday.hodith.domain.LOG_SHARE_CARD_ENTRY_CAP
import com.secondmonday.hodith.domain.SHARE_CARD_TOP_TAG_COUNT
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.filterAndSortEvents
import com.secondmonday.hodith.ui.casedetail.trendsVisibleFindings
import com.secondmonday.hodith.ui.voice.Voice
import java.time.ZoneId

/** Spec §13's two share-card canvases. */
enum class ShareCardFormat {
    STORY,
    SQUARE,
}

/**
 * Spec §13's checklist-driven Story section picker. Declaration order is the order the picker rows
 * and the card's sections both follow, after the always-present hero beat.
 */
enum class ShareInsightsSection {
    GAPS,
    STREAKS,
    DURATION,
    RHYTHM,
    INTENSITY,
    TRENDS,
    TAGS,
}

/**
 * The share card's top beat — spec §13's product-owner call to always have at least one beat
 * rather than risk an empty card.
 */
sealed interface ShareTopBeat {
    /**
     * The headline both formats open with: the observed span and event count, plus the Case's
     * [rate] (`null` until there are enough events and days to state one).
     */
    data class Summary(
        val eventCount: Int,
        val observedDays: Long,
        val rate: HeroRate?,
    ) : ShareTopBeat
}

/** The longest and average streak, as the share card shows them. */
data class StreakDisplay(
    val longestStreakDays: Int,
    val averageStreakDays: Double,
)

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
    val caseIcon: String
    val caseName: String
    val generatedAtMillis: Long

    /** A fixed-order subset of the real Insights tab's sections. */
    data class Insights(
        val format: ShareCardFormat,
        override val caseIcon: String,
        override val caseName: String,
        override val generatedAtMillis: Long,
        val topBeat: ShareTopBeat,
        val rhythm: RhythmDisplay?,
        val gaps: GapsDisplay?,
        /** The Case's streak figures. Square shows them inside the Gaps card; Story only when its Streaks section is picked. `null` until there are two events. */
        val streaks: StreakDisplay? = null,
        val trends: List<TrendFinding>,
        val duration: DurationDisplay?,
        val intensity: IntensityDisplay?,
        /** The Case's busiest tags, at most [SHARE_CARD_TOP_TAG_COUNT]; only Story's Tags section fills it. */
        val tags: List<TagBreakdownEntry> = emptyList(),
        /** Days the Case has been quiet while the went-quiet signal is live; shown on the Gaps panel when that panel is on the card. */
        val quietForDays: Long? = null,
    ) : ShareCardData

    /**
     * [rangeLabel] and [rows] arrive pre-formatted (the caller already resolves "today" for the
     * range picker, so it also resolves the "All time" vs. explicit-dates wording here — see
     * [logShareCardState]). [truncatedTotalCount] is `null` when nothing was cut; otherwise the
     * pre-cap match count, for the card's own "+N more" note.
     */
    data class Log(
        override val caseIcon: String,
        override val caseName: String,
        override val generatedAtMillis: Long,
        val rangeLabel: String,
        val rows: List<LogCardRow>,
        val truncatedTotalCount: Int?,
    ) : ShareCardData
}

/**
 * Assembles spec §13's share card content purely from the same [insightsTabState] output Case
 * Detail's Insights tab already computes — no new domain math. [displayName] is separate
 * from [CaseEntity.name] so the share screen's editable name field never mutates the actual Case.
 * [eventCount]/[observedDays]
 * mirror the Log tab summary line's inputs (`events.size`/`observationSpanDays`), since [StatsSections.totalEventCount]
 * is unavailable whenever [insightsState] is [InsightsTabState.NothingLogged] but the top beat still needs
 * to show the true count.
 *
 * [ShareCardFormat.STORY] keeps the user's choice: a section is only included when both the caller
 * selected it and it's actually present in [insightsState] (see [availableShareSections]) —
 * sections absent from the Case's config are already `null` in [StatsSections]. Both formats open
 * with the same [ShareTopBeat.Summary] hero. [ShareCardFormat.SQUARE] is a preset the user never configures:
 * [selectedSections] is ignored and the content follows the Case's own settings (see [squareInsights]).
 */
internal fun shareCardState(
    case: CaseEntity,
    displayName: String,
    insightsState: InsightsTabState,
    eventCount: Int,
    observedDays: Long,
    format: ShareCardFormat,
    selectedSections: Set<ShareInsightsSection>,
    generatedAtMillis: Long,
): ShareCardData.Insights {
    val stats = (insightsState as? InsightsTabState.Ready)?.stats

    return when (format) {
        ShareCardFormat.SQUARE -> squareInsights(case, displayName, stats, eventCount, observedDays, generatedAtMillis)
        ShareCardFormat.STORY -> storyInsights(case, displayName, stats, eventCount, observedDays, selectedSections, generatedAtMillis)
    }
}

/**
 * Story's findings for the Trends section: the went-quiet finding is left out because the hero's
 * and the Gaps panel's quiet signal already carry it (it rides on [ShareCardData.Insights.quietForDays]),
 * then the list is capped the way the Insights tab's own Trends card caps it.
 */
internal fun storyTrendFindings(stats: StatsSections): List<TrendFinding> =
    trendsVisibleFindings(stats.trends.filterNot { it.kind == TrendFindingKind.WENT_QUIET })

/**
 * The Story picker's rows for [stats], in card order — only sections the Case has something to
 * show for: Duration and Intensity are absent from [stats] unless the Case tracks them and logged
 * data for them, Trends needs at least one finding [storyTrendFindings] would keep, and Tags needs
 * at least one tagged event. Nothing is offered before the first event (`null` [stats]).
 */
internal fun availableShareSections(stats: StatsSections?): List<ShareInsightsSection> {
    if (stats == null) return emptyList()

    return ShareInsightsSection.entries.filter { section ->
        when (section) {
            ShareInsightsSection.RHYTHM -> true
            ShareInsightsSection.GAPS, ShareInsightsSection.STREAKS -> stats.gaps.shortestGapDays != null
            ShareInsightsSection.DURATION -> stats.duration != null
            ShareInsightsSection.INTENSITY -> stats.intensity != null
            ShareInsightsSection.TRENDS -> storyTrendFindings(stats).isNotEmpty()
            ShareInsightsSection.TAGS -> stats.tags.isNotEmpty()
        }
    }
}

/** The streak figures, or `null` until there are two events: a streak needs a gap to sit beside, so the card hides both together. */
private fun GapsDisplay.streakDisplay(): StreakDisplay? =
    takeIf { shortestGapDays != null }?.let { StreakDisplay(it.longestStreakDays, it.averageStreakDays) }

/** Days the Case has been quiet while the went-quiet signal is live — the finding's current gap — else `null`. */
private fun StatsSections.quietForDays(): Long? =
    trends
        .firstOrNull { it.kind == TrendFindingKind.WENT_QUIET }
        ?.recentValue
        ?.toLong()

/**
 * The hero, then whichever of [selectedSections] the Case has data for. The went-quiet pill shows
 * on the Gaps panel only when the user picked Gaps, so an unpicked section never leaks onto the card.
 */
private fun storyInsights(
    case: CaseEntity,
    displayName: String,
    stats: StatsSections?,
    eventCount: Int,
    observedDays: Long,
    selectedSections: Set<ShareInsightsSection>,
    generatedAtMillis: Long,
): ShareCardData.Insights {
    val gaps = stats?.gaps?.takeIf { ShareInsightsSection.GAPS in selectedSections && it.shortestGapDays != null }
    val streaks = stats?.gaps?.takeIf { ShareInsightsSection.STREAKS in selectedSections }?.streakDisplay()

    return ShareCardData.Insights(
        format = ShareCardFormat.STORY,
        caseIcon = case.icon,
        caseName = displayName,
        topBeat = ShareTopBeat.Summary(eventCount = eventCount, observedDays = observedDays, rate = stats?.heroRate),
        rhythm = stats?.rhythm?.takeIf { ShareInsightsSection.RHYTHM in selectedSections },
        gaps = gaps,
        streaks = streaks,
        trends =
            stats
                ?.takeIf { ShareInsightsSection.TRENDS in selectedSections }
                ?.let { storyTrendFindings(it) }
                ?: emptyList(),
        duration = stats?.duration?.takeIf { ShareInsightsSection.DURATION in selectedSections },
        intensity = stats?.intensity?.takeIf { ShareInsightsSection.INTENSITY in selectedSections },
        tags =
            stats
                ?.tags
                ?.takeIf { ShareInsightsSection.TAGS in selectedSections }
                ?.take(SHARE_CARD_TOP_TAG_COUNT)
                ?: emptyList(),
        quietForDays = if (gaps != null) stats.quietForDays() else null,
        generatedAtMillis = generatedAtMillis,
    )
}

/**
 * The fixed Square preset, top to bottom: the [ShareTopBeat.Summary] headline, then Gaps and its
 * streak figures once there are two events, then whichever of Duration and Intensity
 * the Case tracks, and Rhythm only when it tracks
 * neither. The Case's settings pick the panels (not what was logged), so a Case that tracks
 * Duration but has no finished event yet simply shows no Duration panel rather than swapping in
 * Rhythm. Frequency and Trends never appear on Square; the went-quiet signal rides on the Gaps
 * panel as [ShareCardData.Insights.quietForDays].
 */
private fun squareInsights(
    case: CaseEntity,
    displayName: String,
    stats: StatsSections?,
    eventCount: Int,
    observedDays: Long,
    generatedAtMillis: Long,
): ShareCardData.Insights {
    val tracksNeither = !case.durationMode.tracksDuration && !case.intensityEnabled
    val gaps = stats?.gaps?.takeIf { it.shortestGapDays != null }

    return ShareCardData.Insights(
        format = ShareCardFormat.SQUARE,
        caseIcon = case.icon,
        caseName = displayName,
        topBeat = ShareTopBeat.Summary(eventCount = eventCount, observedDays = observedDays, rate = stats?.heroRate),
        rhythm = stats?.rhythm?.takeIf { tracksNeither },
        gaps = gaps,
        streaks = gaps?.streakDisplay(),
        trends = emptyList(),
        duration = stats?.duration,
        intensity = stats?.intensity,
        quietForDays = if (gaps != null) stats.quietForDays() else null,
        generatedAtMillis = generatedAtMillis,
    )
}

/**
 * Assembles Log Share's card content: [filterAndSortEvents] (the same reusable filter a future
 * Log-tab-filter item would call) narrows and orders [events], then every match beyond
 * [LOG_SHARE_CARD_ENTRY_CAP] is dropped, so a long range never grows the card past a readable
 * length (spec §13). The card has one shape, the content-sized one Story uses.
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
        caseIcon = case.icon,
        caseName = displayName,
        generatedAtMillis = generatedAtMillis,
        rangeLabel = logShareRangeLabel(dateFrom, dateTo, now, zone, voice),
        rows = capped.map { logCardRow(it, case, fields, use24Hour, now, voice) },
        truncatedTotalCount = matches.size.takeIf { it > LOG_SHARE_CARD_ENTRY_CAP },
    )
}

/** Delegates the combining logic to [Voice.shareLogRangeNote], the same function the Log tab's range note and the Log Share button use. */
private fun logShareRangeLabel(
    dateFrom: Long?,
    dateTo: Long,
    now: Long,
    zone: ZoneId,
    voice: Voice,
): String {
    val isDefaultRange = dateFrom == null && dateTo.toLocalDateIn(zone) == now.toLocalDateIn(zone)
    return voice.shareLogRangeNote(
        from = dateFrom?.let { formatDateRangeBound(it, now, zone) },
        to = if (isDefaultRange) null else formatDateRangeBound(dateTo, now, zone),
    )
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
