package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.pdf.HistoryPdfContent
import com.secondmonday.hodith.data.pdf.HistoryPdfRow
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.PDF_TABLE_DETAIL_MAX_CHARS
import com.secondmonday.hodith.domain.filterAndSortEvents
import com.secondmonday.hodith.domain.isStillRunning
import com.secondmonday.hodith.ui.voice.Voice
import java.time.ZoneId

/**
 * The History PDF's full counterpart of [historyShareCardState] - same filter/format pipeline,
 * with [com.secondmonday.hodith.domain.HISTORY_SHARE_CARD_ENTRY_CAP] lifted: a PDF paginates
 * instead of truncating, so a long range gets every entry rather than the card's capped
 * "+N more" note.
 */
internal fun historyPdfContent(
    case: CaseEntity,
    displayName: String,
    events: List<EventWithTags>,
    sortOrder: ChronologicalOrder,
    dateFrom: Long?,
    dateTo: Long,
    fields: Set<HistoryRowField>,
    use24Hour: Boolean,
    voice: Voice,
    now: Long,
    generatedAtMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): HistoryPdfContent {
    val matches = filterAndSortEvents(events, from = dateFrom, to = dateTo, order = sortOrder)
    val (fromText, toText) = historyRangeBounds(case.createdAt, dateFrom, dateTo, now, zone)

    return HistoryPdfContent(
        caseName = displayName,
        kicker = voice.shareTabHistoryLabel,
        dateRangeText = voice.shareHistoryRangeNote(fromText, toText),
        generatedAtText = voice.shareCardFooter(formatCardTimestamp(generatedAtMillis, use24Hour, zone)),
        rows = matches.map { historyPdfRow(it, case, fields, use24Hour, now, voice) },
    )
}

private fun historyPdfRow(
    eventWithTags: EventWithTags,
    case: CaseEntity,
    fields: Set<HistoryRowField>,
    use24Hour: Boolean,
    now: Long,
    voice: Voice,
): HistoryPdfRow {
    val event = eventWithTags.event
    val isOngoing = isStillRunning(event, case.tracksDuration, now)

    return HistoryPdfRow(
        timestamp = formatEventTime(event.occurredAt, now, use24Hour, zone = event.loggedZone()),
        detail =
            eventDetailSummary(
                occurredAt = event.occurredAt,
                endedAt = event.endedAt,
                intensity = event.intensity,
                note = event.note.takeIf { HistoryRowField.NOTES in fields },
                tagNames = if (HistoryRowField.TAGS in fields) eventWithTags.tags.map { it.name } else emptyList(),
                voice = voice,
                isOngoing = isOngoing,
                tracksDuration = case.tracksDuration && HistoryRowField.DURATION in fields,
                showIntensity = case.intensityEnabled && HistoryRowField.INTENSITY in fields,
            )?.take(PDF_TABLE_DETAIL_MAX_CHARS),
    )
}
