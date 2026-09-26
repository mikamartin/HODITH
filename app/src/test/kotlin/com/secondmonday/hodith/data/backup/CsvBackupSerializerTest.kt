package com.secondmonday.hodith.data.backup

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Test

private const val HEADER = "case_name,occurred_at,ended_at,duration_minutes,intensity,tags,note"
private const val CRLF = "\r\n"

class CsvBackupSerializerTest {
    private val serializer = CsvBackupSerializer()

    private fun testCase(
        id: Long = 1L,
        name: String = "Coffee",
        archived: Boolean = false,
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = archived,
    )

    private fun testEvent(
        id: Long = 1L,
        caseId: Long = 1L,
        occurredAt: Long = 0L,
        endedAt: Long? = null,
        intensity: Int? = null,
        note: String? = null,
        utcOffsetMinutes: Int = 0,
    ) = EventEntity(
        id = id,
        caseId = caseId,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = intensity,
        note = note,
        loggedAt = occurredAt,
        utcOffsetMinutes = utcOffsetMinutes,
    )

    private fun backupData(
        cases: List<CaseEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
        tags: List<TagEntity> = emptyList(),
        eventTags: List<EventTagCrossRef> = emptyList(),
    ) = BackupData(cases = cases, tags = tags, events = events, eventTags = eventTags, hunches = emptyList(), triggers = emptyList())

    @Test
    fun `an empty backup produces only the header row`() {
        val csv = serializer.toCsv(backupData())

        assertEquals(HEADER + CRLF, csv)
    }

    @Test
    fun `an instant event leaves ended_at and duration_minutes blank, not zero`() {
        val data = backupData(cases = listOf(testCase()), events = listOf(testEvent(occurredAt = 0L)))

        val csv = serializer.toCsv(data)

        assertEquals(HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,," + CRLF, csv)
    }

    @Test
    fun `a durationed event with intensity renders duration_minutes and its own captured offset`() {
        val event =
            testEvent(occurredAt = 0L, endedAt = 1_800_000L, intensity = 3, utcOffsetMinutes = -240)
        val data = backupData(cases = listOf(testCase()), events = listOf(event))

        val csv = serializer.toCsv(data)

        assertEquals(
            HEADER + CRLF + "Coffee,1969-12-31T20:00:00-04:00,1969-12-31T20:30:00-04:00,30,3,," + CRLF,
            csv,
        )
    }

    @Test
    fun `a null intensity is blank, not the literal word null`() {
        val data = backupData(cases = listOf(testCase()), events = listOf(testEvent(intensity = null)))

        val csv = serializer.toCsv(data)

        assertEquals(HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,," + CRLF, csv)
    }

    @Test
    fun `an event with no tags leaves the tags column blank`() {
        val data = backupData(cases = listOf(testCase()), events = listOf(testEvent()), tags = emptyList(), eventTags = emptyList())

        val csv = serializer.toCsv(data)

        assertEquals(HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,," + CRLF, csv)
    }

    @Test
    fun `multiple tags are joined sorted and the comma forces quoting`() {
        val event = testEvent(id = 3L, occurredAt = 60_000L)
        val data =
            backupData(
                cases = listOf(testCase()),
                events = listOf(event),
                tags = listOf(TagEntity(id = 10L, name = "home"), TagEntity(id = 11L, name = "coffee")),
                eventTags = listOf(EventTagCrossRef(eventId = 3L, tagId = 10L), EventTagCrossRef(eventId = 3L, tagId = 11L)),
            )

        val csv = serializer.toCsv(data)

        assertEquals(
            HEADER + CRLF + "Coffee,1970-01-01T00:01:00+00:00,,,,\"coffee, home\"," + CRLF,
            csv,
        )
    }

    @Test
    fun `a note containing a comma is quoted`() {
        val data =
            backupData(cases = listOf(testCase()), events = listOf(testEvent(note = "Woke up, had coffee")))

        val csv = serializer.toCsv(data)

        assertEquals(
            HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,,\"Woke up, had coffee\"" + CRLF,
            csv,
        )
    }

    @Test
    fun `a note containing a double quote is quoted with the quote doubled`() {
        val data =
            backupData(cases = listOf(testCase()), events = listOf(testEvent(note = "She said \"ouch\"")))

        val csv = serializer.toCsv(data)

        assertEquals(
            HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,,\"She said \"\"ouch\"\"\"" + CRLF,
            csv,
        )
    }

    @Test
    fun `a note containing a newline is quoted with the newline preserved`() {
        val data =
            backupData(cases = listOf(testCase()), events = listOf(testEvent(note = "Line one\nLine two")))

        val csv = serializer.toCsv(data)

        assertEquals(
            HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,,\"Line one\nLine two\"" + CRLF,
            csv,
        )
    }

    @Test
    fun `rows follow case order in BackupData then ascending occurredAt within each case`() {
        val coffee = testCase(id = 1L, name = "Coffee")
        val exercise = testCase(id = 2L, name = "Exercise")
        val events =
            listOf(
                testEvent(id = 1L, caseId = 1L, occurredAt = 200_000L),
                testEvent(id = 2L, caseId = 2L, occurredAt = 50_000L),
                testEvent(id = 3L, caseId = 1L, occurredAt = 100_000L),
            )
        val data = backupData(cases = listOf(coffee, exercise), events = events)

        val csv = serializer.toCsv(data)

        val expectedRows =
            listOf(
                "Coffee,1970-01-01T00:01:40+00:00,,,,,",
                "Coffee,1970-01-01T00:03:20+00:00,,,,,",
                "Exercise,1970-01-01T00:00:50+00:00,,,,,",
            )
        assertEquals(HEADER + CRLF + expectedRows.joinToString(CRLF) + CRLF, csv)
    }

    @Test
    fun `an archived case and its events are excluded entirely`() {
        val active = testCase(id = 1L, name = "Coffee", archived = false)
        val archived = testCase(id = 2L, name = "Old habit", archived = true)
        val events =
            listOf(
                testEvent(id = 1L, caseId = 1L, occurredAt = 0L),
                testEvent(id = 2L, caseId = 2L, occurredAt = 0L),
            )
        val data = backupData(cases = listOf(active, archived), events = events)

        val csv = serializer.toCsv(data)

        assertEquals(HEADER + CRLF + "Coffee,1970-01-01T00:00:00+00:00,,,,," + CRLF, csv)
    }
}
