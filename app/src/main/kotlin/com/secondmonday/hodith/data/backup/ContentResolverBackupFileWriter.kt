package com.secondmonday.hodith.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class ContentResolverBackupFileWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupFileWriter {
        override fun openOutputStream(uri: Uri): OutputStream? = context.contentResolver.openOutputStream(uri)

        override fun openInputStream(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
    }
