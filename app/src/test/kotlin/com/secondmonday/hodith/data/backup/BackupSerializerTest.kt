package com.secondmonday.hodith.data.backup

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class BackupSerializerTest {
    private val serializer = BackupSerializer(Moshi.Builder().build())

    private fun testBackup() =
        BackupData(
            cases =
                listOf(
                    CaseEntity(
                        id = 1L,
                        name = "Coffee",
                        icon = "☕️",
                        createdAt = 0L,
                        logFlow = LogFlow.ONE_TAP,
                        durationMode = DurationMode.NONE,
                        intensityEnabled = false,
                        hunchNudgeDismissed = false,
                        checkInsEnabled = true,
                        lastCheckInAt = null,
                        sortOrder = 0,
                        archived = false,
                    ),
                ),
            tags = emptyList(),
            events =
                listOf(
                    EventEntity(id = 1L, caseId = 1L, occurredAt = 100L, endedAt = null, intensity = null, note = null, loggedAt = 100L),
                ),
            eventTags = emptyList(),
            hunches = emptyList(),
            triggers = emptyList(),
        )

    @Test
    fun `toJson then fromJson round-trips every field`() {
        val backup = testBackup()

        val restored = serializer.fromJson(serializer.toJson(backup))

        assertEquals(backup, restored)
    }

    @Test
    fun `fromJson throws on malformed JSON`() {
        assertThrows(IOException::class.java) { serializer.fromJson("not json") }
    }

    @Test
    fun `fromJson throws when a required field is missing`() {
        assertThrows(JsonDataException::class.java) { serializer.fromJson("""{"schemaVersion":1}""") }
    }

    @Test
    fun `peekSchemaVersion reads a numeric schemaVersion`() {
        val json = """{"schemaVersion":2,"cases":[],"tags":[],"events":[],"eventTags":[],"hunches":[],"triggers":[]}"""

        assertEquals(2, serializer.peekSchemaVersion(json))
    }

    @Test
    fun `peekSchemaVersion defaults to 1 when the key is omitted`() {
        val json = """{"cases":[],"tags":[],"events":[],"eventTags":[],"hunches":[],"triggers":[]}"""

        assertEquals(1, serializer.peekSchemaVersion(json))
    }

    @Test
    fun `peekSchemaVersion returns null for non-JSON`() {
        assertNull(serializer.peekSchemaVersion("not json"))
    }

    @Test
    fun `peekSchemaVersion returns null for a top-level JSON array`() {
        assertNull(serializer.peekSchemaVersion("[]"))
    }

    @Test
    fun `fromJson with a declared version below current and no matching upgrade step falls through to strict parsing`() {
        val backup = testBackup()
        val alreadyValidJson = serializer.toJson(backup).replace("\"schemaVersion\":1", "\"schemaVersion\":0")

        val restored = serializer.fromJson(alreadyValidJson, declaredVersion = 0)

        assertEquals(backup.copy(schemaVersion = 0), restored)
    }

    @Test
    fun `applyUpgradeSteps folds only steps whose fromVersion is in range, in order`() {
        val stepFromZero = fakeUpgradeStep(fromVersion = 0) { raw -> raw + ("addedByZero" to "value") }
        val stepFromFive = fakeUpgradeStep(fromVersion = 5) { raw -> raw + ("unreached" to true) }

        val result = applyUpgradeSteps(raw = emptyMap(), declaredVersion = 0, targetVersion = 1, steps = listOf(stepFromZero, stepFromFive))

        assertEquals(mapOf("addedByZero" to "value"), result)
    }

    private fun fakeUpgradeStep(
        fromVersion: Int,
        upgrade: (Map<String, Any?>) -> Map<String, Any?>,
    ) = object : BackupUpgradeStep {
        override val fromVersion: Int = fromVersion

        override fun upgrade(raw: Map<String, Any?>): Map<String, Any?> = upgrade(raw)
    }
}
