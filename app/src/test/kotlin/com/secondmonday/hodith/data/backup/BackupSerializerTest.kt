package com.secondmonday.hodith.data.backup

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {
    private val serializer =
        BackupSerializer(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )

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
                        tracksDuration = false,
                        intensityEnabled = false,
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
            watches = emptyList(),
        )

    @Test
    fun `fromJson ignores the removed hunchNudgeDismissed key a pre-v9 backup still carries`() {
        val currentJson = serializer.toJson(testBackup())
        val olderJson = currentJson.replace("\"intensityEnabled\":false", "\"intensityEnabled\":false,\"hunchNudgeDismissed\":true")

        val restored = serializer.fromJson(olderJson, declaredVersion = 1)

        assertEquals(testBackup().cases, restored.cases)
    }

    @Test
    fun `toJson then fromJson round-trips every field`() {
        val backup = testBackup()

        val restored = serializer.fromJson(serializer.toJson(backup))

        assertEquals(backup, restored)
    }

    @Test
    fun `toJson writes a WatchEntity's defaulted fields instead of omitting them`() {
        // kotlinx.serialization omits a field equal to its default unless the Json instance sets
        // encodeDefaults = true. A round-trip-equality test can't catch a regression here - decoding
        // falls back to the same Kotlin default whether or not the key was ever in the JSON - so
        // this asserts on the JSON text itself, matching how BackupUpgradeStep's raw JsonObject
        // inspection (and a human reading an exported file) would actually be affected.
        val watch =
            WatchEntity(
                id = 1L,
                caseId = 1L,
                kind = WatchKind.OFTEN,
                threshold = 3,
                windowDays = 7,
                expectedPer = ExpectedPer.WEEK,
                minIntensity = null,
                enabled = true,
                lastFiredAt = null,
            )
        val backup = testBackup().copy(watches = listOf(watch))

        val json = serializer.toJson(backup)

        assertTrue(json.contains("\"armed\":true"))
        assertTrue(json.contains("\"metric\":\"OCCURRENCE_COUNT\""))
    }

    @Test
    fun `fromJson throws on malformed JSON`() {
        assertThrows(SerializationException::class.java) { serializer.fromJson("not json") }
    }

    @Test
    fun `fromJson throws when a required field is missing`() {
        assertThrows(SerializationException::class.java) { serializer.fromJson("""{"schemaVersion":1}""") }
    }

    @Test
    fun `peekSchemaVersion reads a numeric schemaVersion`() {
        val json = """{"schemaVersion":2,"cases":[],"tags":[],"events":[],"eventTags":[],"notifications":[]}"""

        assertEquals(2, serializer.peekSchemaVersion(json))
    }

    @Test
    fun `peekSchemaVersion defaults to the current version when the key is omitted`() {
        val json = """{"cases":[],"tags":[],"events":[],"eventTags":[],"notifications":[]}"""

        assertEquals(BACKUP_SCHEMA_VERSION, serializer.peekSchemaVersion(json))
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
        val alreadyValidJson =
            serializer.toJson(backup).replace("\"schemaVersion\":$BACKUP_SCHEMA_VERSION", "\"schemaVersion\":0")

        val restored = serializer.fromJson(alreadyValidJson, declaredVersion = 0)

        assertEquals(backup.copy(schemaVersion = 0), restored)
    }

    @Test
    fun `applyUpgradeSteps folds only steps whose fromVersion is in range, in order`() {
        val stepFromZero = fakeUpgradeStep(fromVersion = 0) { raw -> JsonObject(raw + ("addedByZero" to JsonPrimitive("value"))) }
        val stepFromFive = fakeUpgradeStep(fromVersion = 5) { raw -> JsonObject(raw + ("unreached" to JsonPrimitive(true))) }

        val result =
            applyUpgradeSteps(
                raw = JsonObject(emptyMap()),
                declaredVersion = 0,
                targetVersion = 1,
                steps = listOf(stepFromZero, stepFromFive),
            )

        assertEquals(JsonObject(mapOf("addedByZero" to JsonPrimitive("value"))), result)
    }

    private fun fakeUpgradeStep(
        fromVersion: Int,
        upgrade: (JsonObject) -> JsonObject,
    ) = object : BackupUpgradeStep {
        override val fromVersion: Int = fromVersion

        override fun upgrade(raw: JsonObject): JsonObject = upgrade(raw)
    }
}
