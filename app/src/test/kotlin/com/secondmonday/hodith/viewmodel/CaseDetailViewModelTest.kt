package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.ui.voice.GothVoice
import com.secondmonday.hodith.ui.voice.SeriousVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CaseDetailViewModelTest {
    private val utc = ZoneId.of("UTC")

    // Exact-equality on the formatted string is avoided: the JDK's CLDR data inserts a
    // narrow no-break space before AM/PM (not a plain space), and that's an implementation
    // detail of the JDK version, not something this test should pin down.
    @Test
    fun `formatEventTime renders weekday, date and time, omitting the year when it matches now`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        val formatted = formatEventTime(instant.toEpochMilli(), now, utc)

        assertTrue(formatted.contains("Thu"))
        assertTrue(formatted.contains("Jul 9"))
        assertTrue(formatted.contains("3:30"))
        assertTrue(formatted.contains("PM"))
        assertTrue("expected no year in \"$formatted\"", !formatted.contains("2026"))
        assertTrue("expected weekday after the timestamp in \"$formatted\"", formatted.indexOf("Thu") > formatted.indexOf("3:30"))
    }

    @Test
    fun `formatEventTime includes the year when it differs from now`() {
        val instant = ZonedDateTime.of(2025, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        val formatted = formatEventTime(instant.toEpochMilli(), now, utc)

        assertTrue(formatted.contains("2025"))
    }

    @Test
    fun `formatEventTime reflects the requested zone, not just the instant`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = instant.toEpochMilli()
        // America/New_York is UTC-4 in July (daylight saving), so 15:30 UTC is 11:30 local.
        val newYork = ZoneId.of("America/New_York")

        val formatted = formatEventTime(instant.toEpochMilli(), now, newYork)

        assertTrue(formatted.contains("Jul 9"))
        assertTrue(formatted.contains("11:30"))
        assertTrue(formatted.contains("AM"))
    }

    @Test
    fun `eventDetailSummary is null when nothing is set`() {
        assertNull(eventDetailSummary(testEvent(intensity = null, note = null), tags = emptyList(), SeriousVoice))
    }

    @Test
    fun `eventDetailSummary is null when note is blank, intensity is unset, and there are no tags`() {
        assertNull(eventDetailSummary(testEvent(intensity = null, note = "   "), tags = emptyList(), SeriousVoice))
    }

    @Test
    fun `eventDetailSummary shows intensity only when note and tags are unset`() {
        assertEquals(
            SeriousVoice.eventIntensityLabel(4),
            eventDetailSummary(testEvent(intensity = 4, note = null), tags = emptyList(), SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows note only when intensity and tags are unset`() {
        assertEquals(
            "Snapped during dinner",
            eventDetailSummary(testEvent(intensity = null, note = "Snapped during dinner"), tags = emptyList(), SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary joins intensity and note, intensity first`() {
        assertEquals(
            "${SeriousVoice.eventIntensityLabel(2)} · Quick one",
            eventDetailSummary(testEvent(intensity = 2, note = "Quick one"), tags = emptyList(), SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary uses the given voice's intensity copy, not a hardcoded string`() {
        assertEquals(
            GothVoice.eventIntensityLabel(5),
            eventDetailSummary(testEvent(intensity = 5, note = null), tags = emptyList(), GothVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows tags only when intensity and note are unset`() {
        val tags = listOf(TagEntity(id = 1, name = "work"), TagEntity(id = 2, name = "morning"))

        assertEquals(
            "#work #morning",
            eventDetailSummary(testEvent(intensity = null, note = null), tags = tags, SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary appends tags last, after intensity and note`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))

        assertEquals(
            "${SeriousVoice.eventIntensityLabel(3)} · Quick one · #dinner",
            eventDetailSummary(testEvent(intensity = 3, note = "Quick one"), tags = tags, SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows the ongoing label first when isOngoing is true`() {
        assertEquals(
            SeriousVoice.logSheetOngoingLabel,
            eventDetailSummary(testEvent(intensity = null, note = null), tags = emptyList(), SeriousVoice, isOngoing = true),
        )
    }

    @Test
    fun `eventDetailSummary puts the ongoing label before intensity, note and tags`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))

        assertEquals(
            "${SeriousVoice.logSheetOngoingLabel} · ${SeriousVoice.eventIntensityLabel(3)} · Quick one · #dinner",
            eventDetailSummary(testEvent(intensity = 3, note = "Quick one"), tags = tags, SeriousVoice, isOngoing = true),
        )
    }

    @Test
    fun `eventDetailSummary shows the duration for a finished event with an endedAt`() {
        val event = testEvent(intensity = null, note = null, occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(SeriousVoice.eventDurationLabel("45m"), eventDetailSummary(event, tags = emptyList(), SeriousVoice))
    }

    @Test
    fun `eventDetailSummary puts the duration before intensity, note and tags`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))
        val event = testEvent(intensity = 3, note = "Quick one", occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(
            "${SeriousVoice.eventDurationLabel("45m")} · ${SeriousVoice.eventIntensityLabel(3)} · Quick one · #dinner",
            eventDetailSummary(event, tags = tags, SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows no duration when endedAt is null and not ongoing`() {
        val event = testEvent(intensity = 3, note = null, occurredAt = 0L, endedAt = null)

        assertEquals(SeriousVoice.eventIntensityLabel(3), eventDetailSummary(event, tags = emptyList(), SeriousVoice))
    }

    @Test
    fun `eventDetailSummary shows the ongoing label instead of a duration even if isOngoing is true with a stale endedAt`() {
        // isOngoing is caller-asserted (spec: only true for a START_STOP case's open event, which
        // by construction never has an endedAt) — this pins that ongoing always wins the branch.
        val event = testEvent(intensity = null, note = null, occurredAt = 0L, endedAt = 999L)

        assertEquals(
            SeriousVoice.logSheetOngoingLabel,
            eventDetailSummary(event, tags = emptyList(), SeriousVoice, isOngoing = true),
        )
    }

    private fun testEvent(
        intensity: Int?,
        note: String?,
        occurredAt: Long = 0L,
        endedAt: Long? = null,
    ) = EventEntity(
        caseId = 1L,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = intensity,
        note = note,
        loggedAt = 0L,
    )
}
