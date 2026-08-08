package com.secondmonday.hodith.data.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** Matches the `<cache-path name="shared_cards" .../>` entry in `res/xml/file_paths.xml`. */
private const val SHARE_CACHE_DIR_NAME = "shared_cards"

/**
 * Writes the bitmap into a dedicated cache subfolder (not the general cache root, so the
 * FileProvider only ever exposes share-card PNGs, never other cached files) and hands back a
 * `content://` Uri via [FileProvider]. Files are transient — nothing here needs cleanup beyond
 * what the OS already does for cache storage under pressure.
 */
class ComposeShareImageExporter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ShareImageExporter {
        override suspend fun exportToShareUri(
            bitmap: Bitmap,
            fileNamePrefix: String,
        ): Uri =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, SHARE_CACHE_DIR_NAME).apply { mkdirs() }
                val file = File(dir, "$fileNamePrefix-${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
    }
