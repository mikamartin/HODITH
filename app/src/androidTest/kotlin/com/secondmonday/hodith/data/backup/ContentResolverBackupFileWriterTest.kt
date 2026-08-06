package com.secondmonday.hodith.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * [ContentResolverBackupFileWriter] is the one piece of the export/import flow nothing else
 * exercises: `RoomHodithRepositoryBackupTest` round-trips through an in-memory Room database and
 * `SettingsViewModelTest` round-trips through a fake [BackupFileWriter] — neither ever touches a
 * real [android.content.ContentResolver]. The real system "save to"/"open" picker itself still
 * needs a human (see MANUAL_TEST_PLAN.md's Data & backup section), but the boundary it hands off
 * to — writing/reading bytes through a real `Uri` via the real `ContentResolver` — had no coverage
 * at all until this.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ContentResolverBackupFileWriterTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var writer: BackupFileWriter

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun openOutputStreamThenOpenInputStream_roundTripsBytesThroughARealUri() {
        val file = File(context.cacheDir, "backup_test_${System.currentTimeMillis()}.json")
        val uri = Uri.fromFile(file)
        val payload = """{"schemaVersion":1,"cases":[]}"""

        try {
            val outputStream = writer.openOutputStream(uri)
            assertNotNull("Expected a real output stream for a file:// Uri", outputStream)
            outputStream!!.use { it.write(payload.toByteArray()) }

            val inputStream = writer.openInputStream(uri)
            assertNotNull("Expected a real input stream for a file:// Uri", inputStream)
            val roundTripped = inputStream!!.use { it.readBytes().decodeToString() }

            assertEquals(payload, roundTripped)
        } finally {
            file.delete()
        }
    }
}
