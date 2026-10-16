package com.secondmonday.hodith.data.pdf

import android.graphics.Bitmap

/**
 * The document-level fields both PDF types share (feature/pdf-export) - a masthead title, the
 * Case's own name/kicker, a "counted with / generated at" provenance line (also repeated in the
 * footer, so it survives a page being separated from the rest when printed), a labelled date
 * range, and an optional app link (blank/`null` until the app has a public page to point to -
 * printed as plain text since `PdfDocument` has no clickable-annotation API; most PDF viewers
 * still auto-linkify a bare URL in plain text).
 */
interface PdfDocumentHeader {
    val documentTitle: String
    val caseName: String

    /** `null` omits the kicker line entirely (and the gap it would take) rather than drawing a blank one - the History PDF has none, since "History" read as a stray leftover label once the table itself already makes the document's nature obvious. */
    val kicker: String?
    val countedWithText: String
    val dateRangeLabel: String
    val dateRangeText: String

    /** Already-formatted total row count, shown under the date range - `null` for the Insights PDF, which has no row list of its own. */
    val recordCountText: String?
    val appLinkText: String?
}

/**
 * Everything [PdfExporter.exportInsightsPdf] needs to draw, already resolved by the caller
 * (strings formatted, sections picked) so the exporter stays pure drawing code with no Voice or
 * domain lookups of its own. [statLines] is one already-formatted line per stat the user has
 * selected (see [com.secondmonday.hodith.data.ShareInsightsSection]'s picker, the same one the
 * Insight share card uses). [chartBitmap] is `null` whenever there's no frequency chart to show
 * (a multi-day-event Case, or too few events) - a page without a chart block, not a blank one.
 */
data class InsightsPdfContent(
    override val documentTitle: String,
    override val caseName: String,
    override val kicker: String?,
    override val countedWithText: String,
    override val dateRangeLabel: String,
    override val dateRangeText: String,
    override val appLinkText: String?,
    val statLines: List<String>,
    val chartBitmap: Bitmap?,
) : PdfDocumentHeader {
    override val recordCountText: String? = null
}

/**
 * The table's column headers, one property per possible column. `null` means that column is
 * absent from the whole document (the Case doesn't track it, or it's off in "Include") - Start is
 * the only column that's never absent. Every present row then has a non-null value in the
 * matching [HistoryPdfRow] property wherever it has data, `null` where this particular event
 * doesn't (e.g. no tags on an otherwise-tagged Case).
 */
data class HistoryPdfColumns(
    val startHeader: String,
    val endHeader: String?,
    val durationHeader: String?,
    val intensityHeader: String?,
    val tagsHeader: String?,
    val notesHeader: String?,
)

/** One row of [HistoryPdfContent.rows] - each property blank (`null`) wherever [HistoryPdfColumns] has no header for it, or this event has nothing to put there. */
data class HistoryPdfRow(
    val start: String,
    val end: String?,
    val duration: String?,
    val intensity: String?,
    val tags: String?,
    val notes: String?,
)

/** Everything [PdfExporter.exportHistoryPdf] needs to draw - the uncapped, multi-column counterpart of [com.secondmonday.hodith.viewmodel.ShareCardData.History]. */
data class HistoryPdfContent(
    override val documentTitle: String,
    override val caseName: String,
    override val kicker: String?,
    override val countedWithText: String,
    override val dateRangeLabel: String,
    override val dateRangeText: String,
    override val recordCountText: String?,
    override val appLinkText: String?,
    val columns: HistoryPdfColumns,
    val rows: List<HistoryPdfRow>,
) : PdfDocumentHeader
