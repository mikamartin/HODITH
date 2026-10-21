package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.pdf.HistoryPdfColumns
import com.secondmonday.hodith.data.pdf.HistoryPdfContent
import com.secondmonday.hodith.data.pdf.HistoryPdfRow
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.filterAndSortEvents
import com.secondmonday.hodith.domain.isStillRunning
import com.secondmonday.hodith.ui.voice.Voice
import kotlinx.datetime.TimeZone
import kotlinx.datetime.asTimeZone

/**
 * The History PDF's full counterpart of [historyShareCardState] - same filter pipeline, with
 * [com.secondmonday.hodith.domain.HISTORY_SHARE_CARD_ENTRY_CAP] lifted (a PDF paginates instead
 * of truncating) and a real multi-column table instead of one joined detail string, so a reader
 * doesn't see "Duration"/"Intensity" repeated on every row.
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
    zone: TimeZone = TimeZone.currentSystemDefault(),
): HistoryPdfContent {
    val matches = filterAndSortEvents(events, from = dateFrom, to = dateTo, order = sortOrder)
    val (fromText, toText) = pdfDateRangeBounds(case.createdAt, dateFrom, dateTo, zone)
    val columns = historyPdfColumns(case, fields, voice)

    return HistoryPdfContent(
        documentTitle = voice.sharePdfDocumentTitle,
        caseName = displayName,
        kicker = null,
        countedWithText = voice.sharePdfGeneratedLine(formatCardTimestamp(generatedAtMillis, use24Hour, zone)),
        dateRangeLabel = voice.sharePdfDateRangeLabel,
        dateRangeText = voice.shareHistoryRangeNote(fromText, toText),
        recordCountText = voice.sharePdfRecordCount(matches.size),
        appLinkText = pdfAppLinkText(),
        columns = columns,
        rows = matches.map { historyPdfRow(it, case, columns, use24Hour, now, voice, zone) },
    )
}

/** End/Duration share one gate (an end time only means anything once duration is tracked and shown); Intensity/Tags/Notes each gate on their own field + the Case's own tracking flag where one applies. */
private fun historyPdfColumns(
    case: CaseEntity,
    fields: Set<HistoryRowField>,
    voice: Voice,
): HistoryPdfColumns {
    val showDuration = case.tracksDuration && HistoryRowField.DURATION in fields
    return HistoryPdfColumns(
        startHeader = voice.sharePdfColumnStartLabel,
        endHeader = voice.sharePdfColumnEndLabel.takeIf { showDuration },
        durationHeader = voice.shareDurationTitle.takeIf { showDuration },
        intensityHeader = voice.insightsSectionLabelIntensity.takeIf { case.intensityEnabled && HistoryRowField.INTENSITY in fields },
        tagsHeader = voice.shareHistoryFieldTagsLabel.takeIf { HistoryRowField.TAGS in fields },
        notesHeader = voice.shareHistoryFieldNotesLabel.takeIf { HistoryRowField.NOTES in fields },
    )
}

private fun historyPdfRow(
    eventWithTags: EventWithTags,
    case: CaseEntity,
    columns: HistoryPdfColumns,
    use24Hour: Boolean,
    now: Long,
    voice: Voice,
    zone: TimeZone,
): HistoryPdfRow {
    val event = eventWithTags.event
    val isOngoing = isStillRunning(event, case.tracksDuration, now)
    val end = event.endedAt?.takeIf { columns.endHeader != null && it > event.occurredAt }

    return HistoryPdfRow(
        start = formatHistoryPdfCellTimestamp(event.occurredAt, use24Hour, event.loggedZone().asTimeZone()),
        end =
            when {
                columns.endHeader == null -> null
                isOngoing -> voice.ongoingPillLabel
                end != null -> formatHistoryPdfCellTimestamp(end, use24Hour, event.loggedZone().asTimeZone())
                else -> null
            },
        duration =
            when {
                columns.durationHeader == null -> null
                isOngoing -> voice.ongoingPillLabel
                end != null -> formatElapsedDuration(event.occurredAt, end)
                else -> null
            },
        intensity = if (columns.intensityHeader != null) event.intensity?.toString() else null,
        tags =
            if (columns.tagsHeader != null && eventWithTags.tags.isNotEmpty()) {
                eventWithTags.tags
                    .map { it.name }
                    .sorted()
                    .joinToString(" ") { "#$it" }
            } else {
                null
            },
        notes = if (columns.notesHeader != null) event.note?.takeIf { it.isNotBlank() } else null,
    )
}
