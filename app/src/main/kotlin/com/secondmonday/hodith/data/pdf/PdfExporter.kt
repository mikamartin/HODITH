package com.secondmonday.hodith.data.pdf

import android.net.Uri

/**
 * Turns one Case's History content into a PDF written to a `content://` [Uri] usable in an
 * `ACTION_SEND` intent - the PDF counterpart of
 * [com.secondmonday.hodith.data.share.ShareImageExporter].
 */
interface PdfExporter {
    suspend fun exportHistoryPdf(
        content: HistoryPdfContent,
        fileNamePrefix: String,
    ): Uri
}
