package com.secondmonday.hodith.data.pdf

import android.graphics.Bitmap

/**
 * Everything [PdfExporter.exportInsightsPdf] needs to draw, already resolved by the caller
 * (strings formatted, sections picked) so the exporter stays pure drawing code with no Voice or
 * domain lookups of its own. [statLines] is one already-formatted line per stat the user has
 * selected (see [com.secondmonday.hodith.data.ShareInsightsSection]'s picker, the same one the
 * Insight share card uses). [chartBitmap] is `null` whenever there's no frequency chart to show
 * (a multi-day-event Case, or too few events) - a page without a chart block, not a blank one.
 */
data class InsightsPdfContent(
    val caseName: String,
    val kicker: String,
    val dateRangeText: String,
    val generatedAtText: String,
    val statLines: List<String>,
    val chartBitmap: Bitmap?,
)

/** One row of [HistoryPdfContent.rows] - [detail] mirrors [com.secondmonday.hodith.viewmodel.HistoryCardRow], already truncated to one line. */
data class HistoryPdfRow(
    val timestamp: String,
    val detail: String?,
)

/** Everything [PdfExporter.exportHistoryPdf] needs to draw - the uncapped counterpart of [com.secondmonday.hodith.viewmodel.ShareCardData.History]. */
data class HistoryPdfContent(
    val caseName: String,
    val kicker: String,
    val dateRangeText: String,
    val generatedAtText: String,
    val rows: List<HistoryPdfRow>,
)
