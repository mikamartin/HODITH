package com.secondmonday.hodith.data.backup

import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** The user-picked file behind a Storage Access Framework [Uri] — abstracted so the ViewModel doesn't need a [android.content.Context]. */
interface BackupFileWriter {
    fun openOutputStream(uri: Uri): OutputStream?

    fun openInputStream(uri: Uri): InputStream?
}
