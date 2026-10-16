package com.secondmonday.hodith.domain

/**
 * PDF export's page geometry (feature/pdf-export) - US Letter at 72pt/inch, the unit
 * `android.graphics.pdf.PdfDocument` pages use. One fixed size, not a setting: the spec doesn't
 * ask for a choice, and a second size would double every constant below.
 */
const val PDF_PAGE_WIDTH_PT = 612
const val PDF_PAGE_HEIGHT_PT = 792
const val PDF_PAGE_MARGIN_PT = 48
const val PDF_CONTENT_WIDTH_PT = PDF_PAGE_WIDTH_PT - 2 * PDF_PAGE_MARGIN_PT
const val PDF_CONTENT_HEIGHT_PT = PDF_PAGE_HEIGHT_PT - 2 * PDF_PAGE_MARGIN_PT

/**
 * Fixed block heights every page's layout is built from - analytical, never live-measured, so
 * pagination is deterministic. Only page 1 carries the document header; every page carries the
 * footer and (History only) a repeated table header row.
 *
 * [PDF_HEADER_BLOCK_HEIGHT_PT] is calibrated against actual drawn content, not a worst case that
 * never occurs: Insights has a kicker line but no record-count line, History has a record-count
 * line but no kicker, so both land around the same ~97-99pt of real content from the top of the
 * block - plus about 15pt of intentional breathing room before the table/stat lines start. A
 * value sized for "kicker and record count both present" (a combination that never happens) would
 * leave a visibly oversized gap for whichever one is actually missing its line.
 */
const val PDF_HEADER_BLOCK_HEIGHT_PT = 115
const val PDF_FOOTER_BLOCK_HEIGHT_PT = 24
const val PDF_TABLE_HEADER_ROW_HEIGHT_PT = 20
const val PDF_STAT_LINE_HEIGHT_PT = 16
const val PDF_CHART_BLOCK_HEIGHT_PT = 220

/**
 * History PDF table cell text metrics. Cells have no max-line cap and are never ellipsized - a
 * long note wraps to however many lines it needs rather than losing content. Row heights are
 * real, measured per row at draw time from each cell's actual wrapped line count, not assumed.
 */
const val PDF_TABLE_CELL_HORIZONTAL_PADDING_PT = 4f
const val PDF_TABLE_ROW_VERTICAL_PADDING_PT = 4f

/**
 * History PDF table column widths. Start/End/Duration/Intensity/Tags are fixed (Start/End sized
 * just wide enough for their two-line date/time text, so Notes - the most variable-length field -
 * keeps most of the row); Notes takes whatever's left of [PDF_CONTENT_WIDTH_PT] once the other
 * present columns are placed, so the table always uses the full content width regardless of
 * which optional columns a Case/selection turns on, and grows automatically when fewer are on.
 */
const val PDF_TABLE_START_COLUMN_WIDTH_PT = 75f
const val PDF_TABLE_END_COLUMN_WIDTH_PT = 75f
const val PDF_TABLE_DURATION_COLUMN_WIDTH_PT = 55f
const val PDF_TABLE_INTENSITY_COLUMN_WIDTH_PT = 50f
const val PDF_TABLE_TAGS_COLUMN_WIDTH_PT = 90f
const val PDF_TABLE_MIN_NOTES_COLUMN_WIDTH_PT = 80f

/** The Insights PDF's stat lines sit below the header block (page 1) or alone (any overflow page) - the chart, when present, is drawn separately and only ever on page 1. */
const val PDF_INSIGHTS_FIRST_PAGE_LINE_CAPACITY =
    (PDF_CONTENT_HEIGHT_PT - PDF_HEADER_BLOCK_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT - PDF_CHART_BLOCK_HEIGHT_PT) / PDF_STAT_LINE_HEIGHT_PT
const val PDF_INSIGHTS_CONTINUATION_LINE_CAPACITY =
    (PDF_CONTENT_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT) / PDF_STAT_LINE_HEIGHT_PT

/**
 * Splits [items] into pages given a first-page capacity (smaller - it shares the page with the
 * header block) and every following page's capacity. Purely arithmetic, so a PDF's page count is
 * known before anything is drawn. An empty [items] still yields one (empty) page, so a Case with
 * nothing to show still gets a single valid page rather than a PDF with no pages at all.
 *
 * Only the Insights PDF's stat lines use this - every line is one fixed height, so a page's
 * capacity is a plain count. The History PDF's table rows wrap to a variable number of lines
 * each, so its pagination is real-measurement-based instead (see `CasePdfExporter`'s
 * `historyTablePages`), not this count-based split.
 */
fun <T> paginatePdfItems(
    items: List<T>,
    firstPageCapacity: Int,
    continuationCapacity: Int,
): List<List<T>> {
    if (items.isEmpty()) return listOf(emptyList())

    val pages = mutableListOf<List<T>>()
    var index = 0
    var capacity = firstPageCapacity.coerceAtLeast(1)
    while (index < items.size) {
        val end = (index + capacity).coerceAtMost(items.size)
        pages += items.subList(index, end)
        index = end
        capacity = continuationCapacity.coerceAtLeast(1)
    }
    return pages
}
