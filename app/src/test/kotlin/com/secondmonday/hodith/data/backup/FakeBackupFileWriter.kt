package com.secondmonday.hodith.data.backup

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Hand-rolled in-memory test double for [BackupFileWriter] — same "no mocking library" style as
 * [com.secondmonday.hodith.data.FakeHodithRepository]. [uri] is never inspected: tests only need a
 * single logical file slot, and the real [Uri] type can't be constructed on a plain JVM unit test
 * anyway (no Robolectric here), which is exactly why [BackupFileWriter] exists as a seam.
 */
class FakeBackupFileWriter : BackupFileWriter {
    var writtenBytes: ByteArray? = null
        private set

    var contentToRead: ByteArray? = null
    var failOutput = false
    var failInput = false

    override fun openOutputStream(uri: Uri): OutputStream? {
        if (failOutput) return null
        return object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                writtenBytes = toByteArray()
            }
        }
    }

    override fun openInputStream(uri: Uri): InputStream? {
        if (failInput) return null
        return ByteArrayInputStream(contentToRead ?: ByteArray(0))
    }
}
