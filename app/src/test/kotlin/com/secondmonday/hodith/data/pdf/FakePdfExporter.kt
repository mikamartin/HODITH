package com.secondmonday.hodith.data.pdf

import android.net.Uri

/**
 * Hand-rolled test double for [PdfExporter] — same "no mocking library" style and the same reason
 * as [com.secondmonday.hodith.data.share.FakeShareImageExporter]: only injectable so
 * [HistoryShareViewModel][com.secondmonday.hodith.viewmodel.HistoryShareViewModel] can be constructed in tests; deliberately not
 * exercised, since a real [Uri] can't be built on a plain JVM unit test (no Robolectric here).
 */
class FakePdfExporter : PdfExporter {
    override suspend fun exportHistoryPdf(
        content: HistoryPdfContent,
        fileNamePrefix: String,
    ): Uri = throw UnsupportedOperationException("Uri can't be constructed on a plain JVM unit test; not exercised by design")
}
