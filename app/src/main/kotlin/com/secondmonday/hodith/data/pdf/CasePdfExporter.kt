package com.secondmonday.hodith.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.secondmonday.hodith.domain.PDF_CHART_BLOCK_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_CONTENT_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_HISTORY_CONTINUATION_ROW_CAPACITY
import com.secondmonday.hodith.domain.PDF_HISTORY_FIRST_PAGE_ROW_CAPACITY
import com.secondmonday.hodith.domain.PDF_INSIGHTS_CONTINUATION_LINE_CAPACITY
import com.secondmonday.hodith.domain.PDF_INSIGHTS_FIRST_PAGE_LINE_CAPACITY
import com.secondmonday.hodith.domain.PDF_PAGE_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_PAGE_MARGIN_PT
import com.secondmonday.hodith.domain.PDF_PAGE_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_STAT_LINE_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_TABLE_HEADER_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_TABLE_ROW_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_TITLE_BLOCK_HEIGHT_PT
import com.secondmonday.hodith.domain.paginatePdfItems
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** Matches the `<cache-path name="exported_pdfs" .../>` entry in `res/xml/file_paths.xml`. */
private const val PDF_CACHE_DIR_NAME = "exported_pdfs"

private const val TITLE_TEXT_SIZE_PT = 20f
private const val KICKER_TEXT_SIZE_PT = 11f
private const val BODY_TEXT_SIZE_PT = 10.5f
private const val FOOTER_TEXT_SIZE_PT = 9f
private const val TITLE_BLOCK_LINE_GAP_PT = 6f
private const val FOOTER_BASELINE_OFFSET_PT = 6f
private const val TIMESTAMP_COLUMN_WIDTH_PT = 110f
private val MUTED_GRAY = Color.rgb(110, 110, 110)

/**
 * Writes Insights/History PDFs into a dedicated cache subfolder (same reasoning as
 * [com.secondmonday.hodith.data.share.ComposeShareImageExporter] - only this subfolder is ever
 * exposed via [FileProvider], not the whole cache root) and hands back a `content://` [Uri].
 * Pages are plain `android.graphics.pdf.PdfDocument`/[Canvas] drawing - no new dependency, vector
 * text throughout except the one captured chart bitmap. Files are transient, same lifecycle as
 * the share-card PNGs.
 */
class CasePdfExporter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PdfExporter {
        override suspend fun exportInsightsPdf(
            content: InsightsPdfContent,
            fileNamePrefix: String,
        ): Uri =
            writePdf(fileNamePrefix) { document ->
                val pages =
                    paginatePdfItems(content.statLines, PDF_INSIGHTS_FIRST_PAGE_LINE_CAPACITY, PDF_INSIGHTS_CONTINUATION_LINE_CAPACITY)
                pages.forEachIndexed { pageIndex, lines ->
                    val page = document.startPage(pageInfo(pageIndex + 1))
                    drawInsightsPage(page.canvas, content, lines, isFirstPage = pageIndex == 0)
                    document.finishPage(page)
                }
            }

        override suspend fun exportHistoryPdf(
            content: HistoryPdfContent,
            fileNamePrefix: String,
        ): Uri =
            writePdf(fileNamePrefix) { document ->
                val pages = paginatePdfItems(content.rows, PDF_HISTORY_FIRST_PAGE_ROW_CAPACITY, PDF_HISTORY_CONTINUATION_ROW_CAPACITY)
                pages.forEachIndexed { pageIndex, rows ->
                    val page = document.startPage(pageInfo(pageIndex + 1))
                    drawHistoryPage(page.canvas, content, rows, isFirstPage = pageIndex == 0)
                    document.finishPage(page)
                }
            }

        private suspend fun writePdf(
            fileNamePrefix: String,
            draw: (PdfDocument) -> Unit,
        ): Uri =
            withContext(Dispatchers.IO) {
                val document = PdfDocument()
                draw(document)
                val dir = File(context.cacheDir, PDF_CACHE_DIR_NAME).apply { mkdirs() }
                val file = File(dir, "$fileNamePrefix-${System.currentTimeMillis()}.pdf")
                FileOutputStream(file).use { out -> document.writeTo(out) }
                document.close()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
    }

private fun pageInfo(pageNumber: Int): PdfDocument.PageInfo =
    PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT, pageNumber).create()

private fun drawInsightsPage(
    canvas: Canvas,
    content: InsightsPdfContent,
    lines: List<String>,
    isFirstPage: Boolean,
) {
    var y = PDF_PAGE_MARGIN_PT.toFloat()
    if (isFirstPage) y = drawTitleBlock(canvas, content.caseName, content.kicker, content.dateRangeText, y)
    lines.forEach { line ->
        canvas.drawText(line, PDF_PAGE_MARGIN_PT.toFloat(), y, bodyPaint())
        y += PDF_STAT_LINE_HEIGHT_PT
    }
    if (isFirstPage) content.chartBitmap?.let { drawChart(canvas, it, y) }
    drawFooter(canvas, content.generatedAtText)
}

private fun drawHistoryPage(
    canvas: Canvas,
    content: HistoryPdfContent,
    rows: List<HistoryPdfRow>,
    isFirstPage: Boolean,
) {
    var y = PDF_PAGE_MARGIN_PT.toFloat()
    if (isFirstPage) y = drawTitleBlock(canvas, content.caseName, content.kicker, content.dateRangeText, y)
    y = drawTableHeaderRule(canvas, y)
    rows.forEach { row ->
        canvas.drawText(row.timestamp, PDF_PAGE_MARGIN_PT.toFloat(), y, bodyPaint())
        row.detail?.let { detail -> canvas.drawText(detail, PDF_PAGE_MARGIN_PT + TIMESTAMP_COLUMN_WIDTH_PT, y, bodyPaint()) }
        y += PDF_TABLE_ROW_HEIGHT_PT
    }
    drawFooter(canvas, content.generatedAtText)
}

/** Draws the title, kicker and date-range lines, and returns the y just past the fixed title block - the content below it always starts at the same offset regardless of how long any of these three lines actually are. */
private fun drawTitleBlock(
    canvas: Canvas,
    caseName: String,
    kicker: String,
    dateRangeText: String,
    startY: Float,
): Float {
    var y = startY + TITLE_TEXT_SIZE_PT
    canvas.drawText(caseName, PDF_PAGE_MARGIN_PT.toFloat(), y, titlePaint())
    y += KICKER_TEXT_SIZE_PT + TITLE_BLOCK_LINE_GAP_PT
    canvas.drawText(kicker, PDF_PAGE_MARGIN_PT.toFloat(), y, kickerPaint())
    y += BODY_TEXT_SIZE_PT + TITLE_BLOCK_LINE_GAP_PT
    canvas.drawText(dateRangeText, PDF_PAGE_MARGIN_PT.toFloat(), y, bodyPaint())
    return startY + PDF_TITLE_BLOCK_HEIGHT_PT
}

/** A plain rule, not a labelled header row - keeps the table free of a second set of user-visible strings beyond what the Voice layer already supplied in [HistoryPdfContent]. */
private fun drawTableHeaderRule(
    canvas: Canvas,
    startY: Float,
): Float {
    val lineY = startY + PDF_TABLE_HEADER_HEIGHT_PT / 2f
    canvas.drawLine(PDF_PAGE_MARGIN_PT.toFloat(), lineY, (PDF_PAGE_MARGIN_PT + PDF_CONTENT_WIDTH_PT).toFloat(), lineY, rulePaint())
    return startY + PDF_TABLE_HEADER_HEIGHT_PT
}

private fun drawFooter(
    canvas: Canvas,
    generatedAtText: String,
) {
    val y = (PDF_PAGE_HEIGHT_PT - PDF_PAGE_MARGIN_PT).toFloat() - FOOTER_BASELINE_OFFSET_PT
    canvas.drawText(generatedAtText, PDF_PAGE_MARGIN_PT.toFloat(), y, footerPaint())
}

/**
 * [toImageBitmap] (see `OffscreenCapture`/`OffscreenCaptureTest`) hands back a GPU-backed
 * `Config.HARDWARE` bitmap, which a software [Canvas] like a PDF page's can't draw directly -
 * confirmed by the capture mechanism's own validation test, not assumed here. Scaled to fit
 * [PDF_CONTENT_WIDTH_PT] by [PDF_CHART_BLOCK_HEIGHT_PT] without distorting its aspect ratio.
 */
private fun drawChart(
    canvas: Canvas,
    bitmap: Bitmap,
    startY: Float,
) {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val scale = minOf(PDF_CONTENT_WIDTH_PT.toFloat() / software.width, PDF_CHART_BLOCK_HEIGHT_PT.toFloat() / software.height)
    val left = PDF_PAGE_MARGIN_PT.toFloat()
    val rect = RectF(left, startY, left + software.width * scale, startY + software.height * scale)
    canvas.drawBitmap(software, null, rect, null)
}

private fun titlePaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TITLE_TEXT_SIZE_PT
        isFakeBoldText = true
        color = Color.BLACK
    }

private fun kickerPaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = KICKER_TEXT_SIZE_PT
        color = MUTED_GRAY
    }

private fun bodyPaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = BODY_TEXT_SIZE_PT
        color = Color.BLACK
    }

private fun footerPaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = FOOTER_TEXT_SIZE_PT
        color = MUTED_GRAY
    }

private fun rulePaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED_GRAY
        strokeWidth = 1f
    }
