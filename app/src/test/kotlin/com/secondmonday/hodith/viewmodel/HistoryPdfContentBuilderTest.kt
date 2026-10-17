package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.testsupport.TEST_ZONE
import com.secondmonday.hodith.testsupport.finishedEvent
import com.secondmonday.hodith.testsupport.finishedPoint
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.runningEvent
import com.secondmonday.hodith.testsupport.testCase
import com.secondmonday.hodith.testsupport.testEvent
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `historyPdfContent` is the History PDF's content-assembly step (feature/pdf-export) - the
 * column-gating rules, per-row field formatting, and ongoing-event handling nothing else exercises
 * directly: `CasePdfExporterTest` only ever builds [com.secondmonday.hodith.data.pdf.HistoryPdfContent]
 * by hand, never through this function.
 */
class HistoryPdfContentBuilderTest {
    private val now = millisAtDay(60)
    private val allFields = HistoryRowField.entries.toSet()

    private fun content(
        case: com.secondmonday.hodith.data.CaseEntity = testCase(createdAt = millisAtDay(0)),
        events: List<EventWithTags> = emptyList(),
        fields: Set<HistoryRowField> = allFields,
        dateFrom: Long? = null,
        dateTo: Long = now,
    ) = historyPdfContent(
        case = case,
        displayName = case.name,
        events = events,
        sortOrder = ChronologicalOrder.NEWEST_FIRST,
        dateFrom = dateFrom,
        dateTo = dateTo,
        fields = fields,
        use24Hour = false,
        voice = PlainVoice,
        now = now,
        generatedAtMillis = now,
    )

    @Test
    fun `kicker is always null - History never shows one`() {
        assertNull(content().kicker)
    }

    @Test
    fun `end and duration columns are gated together on tracksDuration, not on field selection alone`() {
        val tracking = testCase(tracksDuration = true, createdAt = millisAtDay(0))
        val nonTracking = testCase(tracksDuration = false, createdAt = millisAtDay(0))

        assertTrue(content(case = tracking).columns.endHeader != null)
        assertTrue(content(case = tracking).columns.durationHeader != null)
        assertNull(content(case = nonTracking).columns.endHeader)
        assertNull(content(case = nonTracking).columns.durationHeader)
    }

    @Test
    fun `end and duration are both absent when Duration is unchecked in Include, even on a tracking Case`() {
        val tracking = testCase(tracksDuration = true, createdAt = millisAtDay(0))
        val fieldsWithoutDuration = allFields - HistoryRowField.DURATION

        val columns = content(case = tracking, fields = fieldsWithoutDuration).columns

        assertNull(columns.endHeader)
        assertNull(columns.durationHeader)
    }

    @Test
    fun `intensity column needs both the Case's own setting and the field selection`() {
        val intensityCase = testCase(intensityEnabled = true, createdAt = millisAtDay(0))
        val plainCase = testCase(intensityEnabled = false, createdAt = millisAtDay(0))

        assertTrue(content(case = intensityCase).columns.intensityHeader != null)
        assertNull(content(case = plainCase).columns.intensityHeader)
        assertNull(content(case = intensityCase, fields = allFields - HistoryRowField.INTENSITY).columns.intensityHeader)
    }

    @Test
    fun `tags and notes columns follow field selection directly - no Case-level gate`() {
        val case = testCase(createdAt = millisAtDay(0))

        assertTrue(content(case = case).columns.tagsHeader != null)
        assertTrue(content(case = case).columns.notesHeader != null)
        assertNull(content(case = case, fields = allFields - HistoryRowField.TAGS).columns.tagsHeader)
        assertNull(content(case = case, fields = allFields - HistoryRowField.NOTES).columns.notesHeader)
    }

    @Test
    fun `an ongoing event shows the voice's ongoing pill in both end and duration, not a time or blank`() {
        val case = testCase(tracksDuration = true, createdAt = millisAtDay(0))
        val events = listOf(EventWithTags(runningEvent(occurredAt = millisAtDay(50)), emptyList()))

        val row = content(case = case, events = events).rows.single()

        assertEquals(PlainVoice.ongoingPillLabel, row.end)
        assertEquals(PlainVoice.ongoingPillLabel, row.duration)
    }

    @Test
    fun `a zero-length (finished) point event on a tracking Case leaves end and duration blank, not the ongoing pill`() {
        // A null endedAt on a tracking Case reads as ongoing, not "no duration" - finishedPoint's
        // endedAt == occurredAt is the real "finished, zero-length" case (see its own doc comment).
        val case = testCase(tracksDuration = true, createdAt = millisAtDay(0))
        val events = listOf(EventWithTags(finishedPoint(epochDay = 50, caseId = case.id), emptyList()))

        val row = content(case = case, events = events).rows.single()

        assertNull(row.end)
        assertNull(row.duration)
    }

    @Test
    fun `a finished duration event gets a real end timestamp and a formatted elapsed duration`() {
        val case = testCase(tracksDuration = true, createdAt = millisAtDay(0))
        val occurredAt = millisAtDay(50)
        val endedAt = occurredAt + 30 * 60_000L
        val events = listOf(EventWithTags(finishedEvent(occurredAt = occurredAt, endedAt = endedAt), emptyList()))

        val row = content(case = case, events = events).rows.single()

        assertEquals("30m", row.duration)
        assertEquals(formatHistoryPdfCellTimestamp(endedAt, use24Hour = false, zone = TEST_ZONE), row.end)
    }

    @Test
    fun `tags are joined sorted with a hash prefix, null when the event carries none`() {
        val case = testCase(createdAt = millisAtDay(0))
        val tagged = EventWithTags(testEvent(occurredAt = millisAtDay(50)), listOf(TagEntity(name = "zeta"), TagEntity(name = "alpha")))
        val untagged = EventWithTags(testEvent(id = 2, occurredAt = millisAtDay(51)), emptyList())

        val rows = content(case = case, events = listOf(tagged, untagged)).rows

        assertEquals("#alpha #zeta", rows.first { it.tags != null }.tags)
        assertTrue(rows.any { it.tags == null })
    }

    @Test
    fun `a blank note reads as null, not an empty string`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events = listOf(EventWithTags(testEvent(occurredAt = millisAtDay(50), note = "   "), emptyList()))

        assertNull(content(case = case, events = events).rows.single().notes)
    }

    @Test
    fun `record count reflects the filtered match count, not the full event list`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events =
            (10L..15L).map { day -> EventWithTags(testEvent(id = day, occurredAt = millisAtDay(day)), emptyList()) }

        val filtered = content(case = case, events = events, dateFrom = millisAtDay(12), dateTo = millisAtDay(13))

        assertEquals(PlainVoice.sharePdfRecordCount(2), filtered.recordCountText)
        assertEquals(2, filtered.rows.size)
    }
}
