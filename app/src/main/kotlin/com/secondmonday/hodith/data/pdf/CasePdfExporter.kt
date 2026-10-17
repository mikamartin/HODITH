package com.secondmonday.hodith.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.secondmonday.hodith.domain.PDF_CONTENT_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_CONTENT_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_FOOTER_BLOCK_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_HEADER_BLOCK_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_PAGE_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_PAGE_MARGIN_PT
import com.secondmonday.hodith.domain.PDF_PAGE_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_TABLE_CELL_HORIZONTAL_PADDING_PT
import com.secondmonday.hodith.domain.PDF_TABLE_DURATION_COLUMN_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_TABLE_END_COLUMN_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_TABLE_HEADER_ROW_HEIGHT_PT
import com.secondmonday.hodith.domain.PDF_TABLE_INTENSITY_COLUMN_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_TABLE_MIN_NOTES_COLUMN_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_TABLE_ROW_VERTICAL_PADDING_PT
import com.secondmonday.hodith.domain.PDF_TABLE_START_COLUMN_WIDTH_PT
import com.secondmonday.hodith.domain.PDF_TABLE_TAGS_COLUMN_WIDTH_PT
import com.secondmonday.hodith.domain.exportFileTimestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** Matches the `<cache-path name="exported_pdfs" .../>` entry in `res/xml/file_paths.xml`. */
private const val PDF_CACHE_DIR_NAME = "exported_pdfs"

private const val DOCUMENT_TITLE_TEXT_SIZE_PT = 13f
private const val TITLE_TEXT_SIZE_PT = 20f
private const val KICKER_TEXT_SIZE_PT = 11f
private const val BODY_TEXT_SIZE_PT = 10.5f
private const val DATE_RANGE_TEXT_SIZE_PT = 12.5f
private const val FOOTER_TEXT_SIZE_PT = 9f
private const val TABLE_HEADER_TEXT_SIZE_PT = 10f
private const val TABLE_CELL_TEXT_SIZE_PT = 9.5f
private const val TITLE_BLOCK_LINE_GAP_PT = 6f

/** Extra space before the date-range section, on top of [TITLE_BLOCK_LINE_GAP_PT] - visually separates it from the counted-with/generated line above, since it (and the record count under it) read as the document's own facts rather than its provenance line. */
private const val HEADER_SECTION_GAP_PT = 14f
private const val FOOTER_BASELINE_OFFSET_PT = 6f
private const val TABLE_HEADER_BASELINE_OFFSET_PT = 6f
private const val FOOTER_LINE_SEPARATOR = " · "
private val MUTED_GRAY = Color.rgb(110, 110, 110)

private val HISTORY_FIRST_PAGE_ROWS_BUDGET_PT =
    (PDF_CONTENT_HEIGHT_PT - PDF_HEADER_BLOCK_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT - PDF_TABLE_HEADER_ROW_HEIGHT_PT).toFloat()
private val HISTORY_CONTINUATION_ROWS_BUDGET_PT =
    (PDF_CONTENT_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT - PDF_TABLE_HEADER_ROW_HEIGHT_PT).toFloat()

/**
 * Writes a Case's History PDF into a dedicated cache subfolder (same reasoning as
 * [com.secondmonday.hodith.data.share.ComposeShareImageExporter] - only this subfolder is ever
 * exposed via [FileProvider], not the whole cache root) and hands back a `content://` [Uri].
 * Pages are plain `android.graphics.pdf.PdfDocument`/[Canvas] drawing - no new dependency, vector
 * text throughout. Files are transient, same lifecycle as the share-card PNGs.
 */
class CasePdfExporter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PdfExporter {
        override suspend fun exportHistoryPdf(
            content: HistoryPdfContent,
            fileNamePrefix: String,
        ): Uri =
            writePdf(fileNamePrefix) { document ->
                val columns = historyColumns(content.columns)
                val rowLayouts = buildRowLayouts(content.rows, columns)
                val pages = paginateHistoryRows(rowLayouts, HISTORY_FIRST_PAGE_ROWS_BUDGET_PT, HISTORY_CONTINUATION_ROWS_BUDGET_PT)
                pages.forEachIndexed { pageIndex, pageRows ->
                    val page = document.startPage(pageInfo(pageIndex + 1))
                    drawHistoryPage(page.canvas, content, columns, pageRows, isFirstPage = pageIndex == 0)
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
                val file = File(dir, "$fileNamePrefix-${exportFileTimestamp(System.currentTimeMillis())}.pdf")
                FileOutputStream(file).use { out -> document.writeTo(out) }
                document.close()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
    }

private fun pageInfo(pageNumber: Int): PdfDocument.PageInfo =
    PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH_PT, PDF_PAGE_HEIGHT_PT, pageNumber).create()

private fun drawHistoryPage(
    canvas: Canvas,
    content: HistoryPdfContent,
    columns: List<HistoryColumn>,
    pageRows: List<HistoryRowLayout>,
    isFirstPage: Boolean,
) {
    var y = PDF_PAGE_MARGIN_PT.toFloat()
    if (isFirstPage) y = drawDocumentHeader(canvas, content, y)
    val tableTopY = y
    y = drawTableHeaderRow(canvas, columns, y)
    val left = PDF_PAGE_MARGIN_PT.toFloat()
    pageRows.forEach { rowLayout ->
        rowLayout.cellLayouts.forEach { cell ->
            canvas.save()
            canvas.translate(left + cell.xOffset + PDF_TABLE_CELL_HORIZONTAL_PADDING_PT, y + PDF_TABLE_ROW_VERTICAL_PADDING_PT)
            cell.layout.draw(canvas)
            canvas.restore()
        }
        y += rowLayout.height
    }
    drawTableGrid(canvas, columns, pageRows.map { it.height }, tableTopY)
    drawFooter(canvas, content)
}

/**
 * The masthead title, the Case's own name/kicker, the "counted with / generated" line, then
 * (visually separated by [HEADER_SECTION_GAP_PT] and set larger/bolder, since these read as the
 * document's own facts rather than its provenance) the labelled date range and the total row
 * count under it. Returns the y just past the fixed header block, so the content below it always
 * starts at the same offset regardless of how long any of these lines actually are.
 */
private fun drawDocumentHeader(
    canvas: Canvas,
    header: PdfDocumentHeader,
    startY: Float,
): Float {
    val left = PDF_PAGE_MARGIN_PT.toFloat()
    var y = startY + DOCUMENT_TITLE_TEXT_SIZE_PT
    canvas.drawText(header.documentTitle, left, y, documentTitlePaint())
    y += TITLE_TEXT_SIZE_PT + TITLE_BLOCK_LINE_GAP_PT
    canvas.drawText(header.caseName, left, y, titlePaint())
    header.kicker?.let { kicker ->
        y += KICKER_TEXT_SIZE_PT + TITLE_BLOCK_LINE_GAP_PT
        canvas.drawText(kicker, left, y, kickerPaint())
    }
    y += BODY_TEXT_SIZE_PT + TITLE_BLOCK_LINE_GAP_PT
    canvas.drawText(header.countedWithText, left, y, bodyPaint())
    y += BODY_TEXT_SIZE_PT + HEADER_SECTION_GAP_PT
    canvas.drawText("${header.dateRangeLabel}: ${header.dateRangeText}", left, y, dateRangePaint())
    header.recordCountText?.let { recordCountText ->
        y += DATE_RANGE_TEXT_SIZE_PT + TITLE_BLOCK_LINE_GAP_PT
        canvas.drawText(recordCountText, left, y, dateRangePaint())
    }
    return startY + PDF_HEADER_BLOCK_HEIGHT_PT
}

private fun drawFooter(
    canvas: Canvas,
    header: PdfDocumentHeader,
) {
    val y = (PDF_PAGE_HEIGHT_PT - PDF_PAGE_MARGIN_PT).toFloat() - FOOTER_BASELINE_OFFSET_PT
    val text = header.appLinkText?.let { "${header.countedWithText}$FOOTER_LINE_SEPARATOR$it" } ?: header.countedWithText
    canvas.drawText(text, PDF_PAGE_MARGIN_PT.toFloat(), y, footerPaint())
}

// ---- History table: columns, wrapped-cell layout, height-aware pagination, grid ----

private data class HistoryColumn(
    val header: String,
    val width: Float,
    val value: (HistoryPdfRow) -> String?,
)

private data class HistoryCellLayout(
    val xOffset: Float,
    val layout: StaticLayout,
)

private data class HistoryRowLayout(
    val height: Float,
    val cellLayouts: List<HistoryCellLayout>,
)

/**
 * Builds the present columns in a fixed priority order (Start always present), each at a fixed
 * width except the last, which expands to use whatever's left of [PDF_CONTENT_WIDTH_PT] - usually
 * Notes, the most variable-length field, but any trailing column if Notes itself is off.
 */
private fun historyColumns(columns: HistoryPdfColumns): List<HistoryColumn> {
    val fixed = mutableListOf<HistoryColumn>()
    fixed += HistoryColumn(columns.startHeader, PDF_TABLE_START_COLUMN_WIDTH_PT) { it.start }
    columns.endHeader?.let { header -> fixed += HistoryColumn(header, PDF_TABLE_END_COLUMN_WIDTH_PT) { it.end } }
    columns.durationHeader?.let { header -> fixed += HistoryColumn(header, PDF_TABLE_DURATION_COLUMN_WIDTH_PT) { it.duration } }
    columns.intensityHeader?.let { header -> fixed += HistoryColumn(header, PDF_TABLE_INTENSITY_COLUMN_WIDTH_PT) { it.intensity } }
    columns.tagsHeader?.let { header -> fixed += HistoryColumn(header, PDF_TABLE_TAGS_COLUMN_WIDTH_PT) { it.tags } }
    columns.notesHeader?.let { header -> fixed += HistoryColumn(header, PDF_TABLE_MIN_NOTES_COLUMN_WIDTH_PT) { it.notes } }

    val usedByAllButLast = fixed.dropLast(1).sumOf { it.width.toDouble() }.toFloat()
    val last = fixed.last()
    val lastWidth = (PDF_CONTENT_WIDTH_PT - usedByAllButLast).coerceAtLeast(last.width)
    return fixed.dropLast(1) + last.copy(width = lastWidth)
}

private fun buildRowLayouts(
    rows: List<HistoryPdfRow>,
    columns: List<HistoryColumn>,
): List<HistoryRowLayout> {
    val paint = tableCellPaint()
    return rows.map { row ->
        var x = 0f
        val cells =
            columns.map { column ->
                val cellWidthPt = (column.width - 2 * PDF_TABLE_CELL_HORIZONTAL_PADDING_PT).toInt().coerceAtLeast(1)
                val cell = HistoryCellLayout(x, buildCellLayout(column.value(row), paint, cellWidthPt))
                x += column.width
                cell
            }
        val height = (cells.maxOfOrNull { it.layout.height } ?: 0) + 2 * PDF_TABLE_ROW_VERTICAL_PADDING_PT
        HistoryRowLayout(height, cells)
    }
}

/** No max-line cap and no ellipsizing - a long note wraps to however many lines it needs rather than losing content, even if that makes its row (and, via [buildRowLayouts]'s row-height, the page it lands on) taller. */
private fun buildCellLayout(
    text: String?,
    paint: TextPaint,
    widthPt: Int,
): StaticLayout {
    val content = text.orEmpty()
    return StaticLayout.Builder.obtain(content, 0, content.length, paint, widthPt).build()
}

/**
 * Greedily packs rows into pages by their real (wrapped) height instead of a fixed per-page
 * count - a row that wraps to extra lines for a long note takes proportionally more of the
 * page's budget. An empty [rowLayouts] still yields one (empty) page.
 */
private fun paginateHistoryRows(
    rowLayouts: List<HistoryRowLayout>,
    firstPageBudgetPt: Float,
    continuationBudgetPt: Float,
): List<List<HistoryRowLayout>> {
    if (rowLayouts.isEmpty()) return listOf(emptyList())

    val pages = mutableListOf<List<HistoryRowLayout>>()
    var current = mutableListOf<HistoryRowLayout>()
    var remaining = firstPageBudgetPt
    for (rowLayout in rowLayouts) {
        if (current.isNotEmpty() && rowLayout.height > remaining) {
            pages += current
            current = mutableListOf()
            remaining = continuationBudgetPt
        }
        current += rowLayout
        remaining -= rowLayout.height
    }
    pages += current
    return pages
}

private fun drawTableHeaderRow(
    canvas: Canvas,
    columns: List<HistoryColumn>,
    startY: Float,
): Float {
    val paint = tableHeaderPaint()
    val textY = startY + PDF_TABLE_HEADER_ROW_HEIGHT_PT - TABLE_HEADER_BASELINE_OFFSET_PT
    var x = PDF_PAGE_MARGIN_PT.toFloat()
    columns.forEach { column ->
        canvas.drawText(column.header, x + PDF_TABLE_CELL_HORIZONTAL_PADDING_PT, textY, paint)
        x += column.width
    }
    return startY + PDF_TABLE_HEADER_ROW_HEIGHT_PT
}

/** The full grid for one page's table: a horizontal rule above/below every row (including the header) and a vertical rule at every column boundary. */
private fun drawTableGrid(
    canvas: Canvas,
    columns: List<HistoryColumn>,
    rowHeights: List<Float>,
    tableTopY: Float,
) {
    val left = PDF_PAGE_MARGIN_PT.toFloat()
    val right = left + PDF_CONTENT_WIDTH_PT
    val paint = rulePaint()

    val horizontalLineYs = mutableListOf(tableTopY, tableTopY + PDF_TABLE_HEADER_ROW_HEIGHT_PT)
    rowHeights.forEach { height -> horizontalLineYs += horizontalLineYs.last() + height }
    horizontalLineYs.forEach { y -> canvas.drawLine(left, y, right, y, paint) }

    val bottom = horizontalLineYs.last()
    var x = left
    canvas.drawLine(x, tableTopY, x, bottom, paint)
    columns.forEach { column ->
        x += column.width
        canvas.drawLine(x, tableTopY, x, bottom, paint)
    }
}

private fun documentTitlePaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = DOCUMENT_TITLE_TEXT_SIZE_PT
        color = MUTED_GRAY
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

private fun dateRangePaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = DATE_RANGE_TEXT_SIZE_PT
        isFakeBoldText = true
        color = Color.BLACK
    }

private fun footerPaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = FOOTER_TEXT_SIZE_PT
        color = MUTED_GRAY
    }

private fun tableHeaderPaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TABLE_HEADER_TEXT_SIZE_PT
        isFakeBoldText = true
        color = Color.BLACK
    }

private fun tableCellPaint() =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TABLE_CELL_TEXT_SIZE_PT
        color = Color.BLACK
    }

private fun rulePaint() =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED_GRAY
        strokeWidth = 1f
    }
