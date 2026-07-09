package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.EventEntity
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
    fun `formatEventTime renders date and time in the given zone`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()

        val formatted = formatEventTime(instant.toEpochMilli(), utc)

        assertTrue(formatted.contains("Jul 9, 2026"))
        assertTrue(formatted.contains("3:30"))
        assertTrue(formatted.contains("PM"))
    }

    @Test
    fun `formatEventTime reflects the requested zone, not just the instant`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()
        // America/New_York is UTC-4 in July (daylight saving), so 15:30 UTC is 11:30 local.
        val newYork = ZoneId.of("America/New_York")

        val formatted = formatEventTime(instant.toEpochMilli(), newYork)

        assertTrue(formatted.contains("Jul 9, 2026"))
        assertTrue(formatted.contains("11:30"))
        assertTrue(formatted.contains("AM"))
    }

    @Test
    fun `eventDetailSummary is null when neither intensity nor note is set`() {
        assertNull(eventDetailSummary(testEvent(intensity = null, note = null), SeriousVoice))
    }

    @Test
    fun `eventDetailSummary is null when note is blank and intensity is unset`() {
        assertNull(eventDetailSummary(testEvent(intensity = null, note = "   "), SeriousVoice))
    }

    @Test
    fun `eventDetailSummary shows intensity only when note is unset`() {
        assertEquals(
            SeriousVoice.eventIntensityLabel(4),
            eventDetailSummary(testEvent(intensity = 4, note = null), SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows note only when intensity is unset`() {
        assertEquals(
            "Snapped during dinner",
            eventDetailSummary(testEvent(intensity = null, note = "Snapped during dinner"), SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary joins intensity and note, intensity first`() {
        assertEquals(
            "${SeriousVoice.eventIntensityLabel(2)} · Quick one",
            eventDetailSummary(testEvent(intensity = 2, note = "Quick one"), SeriousVoice),
        )
    }

    @Test
    fun `eventDetailSummary uses the given voice's intensity copy, not a hardcoded string`() {
        assertEquals(
            GothVoice.eventIntensityLabel(5),
            eventDetailSummary(testEvent(intensity = 5, note = null), GothVoice),
        )
    }

    private fun testEvent(
        intensity: Int?,
        note: String?,
    ) = EventEntity(
        caseId = 1L,
        occurredAt = 0L,
        endedAt = null,
        intensity = intensity,
        note = note,
        loggedAt = 0L,
    )
}
