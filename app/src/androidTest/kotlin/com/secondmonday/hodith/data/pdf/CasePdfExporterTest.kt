package com.secondmonday.hodith.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.domain.PDF_HISTORY_FIRST_PAGE_ROW_CAPACITY
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The one piece of feature/pdf-export nothing else exercises end to end: everything upstream
 * (pagination math in `PdfLayoutTest`, the chart-capture mechanism in `OffscreenCaptureTest`) is
 * unit/mechanism-tested, but only this actually calls [PdfDocument][android.graphics.pdf.PdfDocument]
 * and reads the result back as a real PDF via [PdfRenderer] - the happy path the generation step
 * can be automated for, same split `CasePdfExporter`'s own doc comment and the project's standing
 * Espresso-Intents deferral describe: generation is covered here, the share-sheet hand-off stays manual.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CasePdfExporterTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var exporter: PdfExporter

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    private fun pageCount(uri: android.net.Uri): Int {
        val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
        return PdfRenderer(pfd).use { it.pageCount }
    }

    private fun historyContent(rowCount: Int) =
        HistoryPdfContent(
            caseName = "Coffee",
            kicker = "History",
            dateRangeText = "Jan 1 – Jan 31",
            generatedAtText = "counted with HODITH app · Jan 31, 9:00 AM",
            rows = (1..rowCount).map { HistoryPdfRow(timestamp = "Jan $it, 9:00 AM", detail = "Note #$it") },
        )

    @Test
    fun exportHistoryPdf_fewRows_producesOnePage() =
        runBlocking {
            val uri = exporter.exportHistoryPdf(historyContent(rowCount = 3), "test-history")

            assertEquals(1, pageCount(uri))
        }

    @Test
    fun exportHistoryPdf_noRows_stillProducesOneValidPage() =
        runBlocking {
            val uri = exporter.exportHistoryPdf(historyContent(rowCount = 0), "test-history-empty")

            assertEquals(1, pageCount(uri))
        }

    @Test
    fun exportHistoryPdf_rowsOverFirstPageCapacity_paginatesToASecondPage() =
        runBlocking {
            val uri = exporter.exportHistoryPdf(historyContent(rowCount = PDF_HISTORY_FIRST_PAGE_ROW_CAPACITY + 1), "test-history-overflow")

            assertEquals(2, pageCount(uri))
        }

    @Test
    fun exportInsightsPdf_withStatLinesAndChart_producesAValidPage() =
        runBlocking {
            val chartBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            val content =
                InsightsPdfContent(
                    caseName = "Coffee",
                    kicker = "Insights",
                    dateRangeText = "Jan 1 – Jan 31",
                    generatedAtText = "counted with HODITH app · Jan 31, 9:00 AM",
                    statLines = listOf("Gaps: Min 1d, Avg 2d, Max 5d", "Top tags: coffee (12)"),
                    chartBitmap = chartBitmap,
                )

            val uri = exporter.exportInsightsPdf(content, "test-insights")

            assertTrue(pageCount(uri) >= 1)
        }

    @Test
    fun exportInsightsPdf_withNoChart_stillProducesAValidPage() =
        runBlocking {
            val content =
                InsightsPdfContent(
                    caseName = "Coffee",
                    kicker = "Insights",
                    dateRangeText = "Jan 1 – Jan 31",
                    generatedAtText = "counted with HODITH app · Jan 31, 9:00 AM",
                    statLines = listOf("Gaps: Min 1d, Avg 2d, Max 5d"),
                    chartBitmap = null,
                )

            val uri = exporter.exportInsightsPdf(content, "test-insights-no-chart")

            assertEquals(1, pageCount(uri))
        }
}
