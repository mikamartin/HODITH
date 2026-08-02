package com.secondmonday.hodith.data.share

import android.graphics.Bitmap
import android.net.Uri

/**
 * Hand-rolled test double for [ShareImageExporter] — same "no mocking library" style as
 * [com.secondmonday.hodith.data.backup.FakeBackupFileWriter]. Only injectable so [ShareViewModel]
 * can be constructed in tests; deliberately not exercised, since a real [Uri] can't be built on a
 * plain JVM unit test (no Robolectric here) — [ShareViewModel.share] is untested framework glue by
 * the same precedent as `SettingsViewModel`'s `exportData`/`importData`.
 */
class FakeShareImageExporter : ShareImageExporter {
    override suspend fun exportToShareUri(
        bitmap: Bitmap,
        fileNamePrefix: String,
    ): Uri = throw UnsupportedOperationException("Uri can't be constructed on a plain JVM unit test; not exercised by design")
}
