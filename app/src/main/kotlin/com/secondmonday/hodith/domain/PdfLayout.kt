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
 * footer and a repeated table header row.
 *
 * [PDF_HEADER_BLOCK_HEIGHT_PT] is calibrated against the header's actual drawn content (the
 * masthead, case name, provenance line, date range and record count - no kicker on the History
 * PDF) - about 97-99pt of real content from the top of the block, plus about 15pt of intentional
 * breathing room before the table starts.
 */
const val PDF_HEADER_BLOCK_HEIGHT_PT = 115
const val PDF_FOOTER_BLOCK_HEIGHT_PT = 24
const val PDF_TABLE_HEADER_ROW_HEIGHT_PT = 20

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
