package com.secondmonday.hodith.data.backup

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
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
}
