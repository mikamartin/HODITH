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

/** Fixed block heights every page's layout is built from - analytical, never live-measured, so pagination is deterministic. */
const val PDF_TITLE_BLOCK_HEIGHT_PT = 70
const val PDF_FOOTER_BLOCK_HEIGHT_PT = 24
const val PDF_TABLE_HEADER_HEIGHT_PT = 20
const val PDF_TABLE_ROW_HEIGHT_PT = 16
const val PDF_STAT_LINE_HEIGHT_PT = 16
const val PDF_CHART_BLOCK_HEIGHT_PT = 220

/** Only page 1 carries the title block; every page carries the footer. */
const val PDF_HISTORY_FIRST_PAGE_ROW_CAPACITY =
    (PDF_CONTENT_HEIGHT_PT - PDF_TITLE_BLOCK_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT - PDF_TABLE_HEADER_HEIGHT_PT) / PDF_TABLE_ROW_HEIGHT_PT
const val PDF_HISTORY_CONTINUATION_ROW_CAPACITY =
    (PDF_CONTENT_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT - PDF_TABLE_HEADER_HEIGHT_PT) / PDF_TABLE_ROW_HEIGHT_PT

/** The Insights PDF's stat lines sit below the title block (page 1) or alone (any overflow page) - the chart, when present, is drawn separately and only ever on page 1. */
const val PDF_INSIGHTS_FIRST_PAGE_LINE_CAPACITY =
    (PDF_CONTENT_HEIGHT_PT - PDF_TITLE_BLOCK_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT - PDF_CHART_BLOCK_HEIGHT_PT) / PDF_STAT_LINE_HEIGHT_PT
const val PDF_INSIGHTS_CONTINUATION_LINE_CAPACITY =
    (PDF_CONTENT_HEIGHT_PT - PDF_FOOTER_BLOCK_HEIGHT_PT) / PDF_STAT_LINE_HEIGHT_PT

/** A note long enough to risk wrapping a table row gets cut here instead - a fixed character budget keeps every row exactly one line, no live text measurement needed. */
const val PDF_TABLE_DETAIL_MAX_CHARS = 90

/**
 * Splits [items] into pages given a first-page capacity (smaller - it shares the page with the
 * title block) and every following page's capacity. Purely arithmetic, so a PDF's page count is
 * known before anything is drawn. An empty [items] still yields one (empty) page, so a Case with
 * nothing to show still gets a single valid page rather than a PDF with no pages at all.
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
