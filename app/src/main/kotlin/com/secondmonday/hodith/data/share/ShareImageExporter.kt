package com.secondmonday.hodith.data.share

import android.graphics.Bitmap
import android.net.Uri

/**
 * Turns a captured share card [Bitmap] into a `content://` [Uri] usable in an `ACTION_SEND` intent.
 * Takes a plain Android [Bitmap] rather than Compose's `ImageBitmap` — the data layer stays free of
 * UI-toolkit types; the Composable call site converts via `ImageBitmap.asAndroidBitmap()`.
 */
interface ShareImageExporter {
    suspend fun exportToShareUri(
        bitmap: Bitmap,
        fileNamePrefix: String,
    ): Uri
}
