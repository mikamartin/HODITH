package com.secondmonday.hodith.data.pdf

import android.net.Uri

/**
 * Turns one Case's Insights or History content into a PDF written to a `content://` [Uri] usable
 * in an `ACTION_SEND` intent - the PDF counterpart of
 * [com.secondmonday.hodith.data.share.ShareImageExporter]. Two methods, not one generic one:
 * Insights and History are genuinely different documents (stats-and-chart vs. a table), and a
 * shared abstraction over them would hide that rather than simplify anything.
 */
interface PdfExporter {
    suspend fun exportInsightsPdf(
        content: InsightsPdfContent,
        fileNamePrefix: String,
    ): Uri

    suspend fun exportHistoryPdf(
        content: HistoryPdfContent,
        fileNamePrefix: String,
    ): Uri
}
