package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.testsupport.testEvent
import com.secondmonday.hodith.ui.voice.IntenseVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CaseDetailFormattingTest {
    private val utc = ZoneId.of("UTC")

    // Exact-equality on the formatted string is avoided: the JDK's CLDR data inserts a
    // narrow no-break space before AM/PM (not a plain space), and that's an implementation
    // detail of the JDK version, not something this test should pin down.
    @Test
    fun `formatEventTime renders weekday, date and time, omitting the year when it matches now`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        val formatted = formatEventTime(instant.toEpochMilli(), now, use24Hour = false, zone = utc)

        assertTrue(formatted.contains("Thu"))
        assertTrue(formatted.contains("Jul 9"))
        assertTrue(formatted.contains("3:30"))
        assertTrue(formatted.contains("PM"))
        assertTrue("expected no year in \"$formatted\"", !formatted.contains("2026"))
        assertTrue("expected weekday after the timestamp in \"$formatted\"", formatted.indexOf("Thu") > formatted.indexOf("3:30"))
    }

    @Test
    fun `formatEventTime renders 24-hour time with no AM PM marker when use24Hour is set`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        val formatted = formatEventTime(instant.toEpochMilli(), now, use24Hour = true, zone = utc)

        assertTrue(formatted.contains("15:30"))
        assertTrue("expected no AM/PM marker in \"$formatted\"", !formatted.contains("PM"))
    }

    @Test
    fun `formatEventTime includes the year when it differs from now, in both formats`() {
        val instant = ZonedDateTime.of(2025, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        assertTrue(formatEventTime(instant.toEpochMilli(), now, use24Hour = false, zone = utc).contains("2025"))
        assertTrue(formatEventTime(instant.toEpochMilli(), now, use24Hour = true, zone = utc).contains("2025"))
    }

    @Test
    fun `formatEventTime reflects the requested zone, not just the instant`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()
        val now = instant.toEpochMilli()
        // America/New_York is UTC-4 in July (daylight saving), so 15:30 UTC is 11:30 local.
        val newYork = ZoneId.of("America/New_York")

        val formatted = formatEventTime(instant.toEpochMilli(), now, use24Hour = false, zone = newYork)

        assertTrue(formatted.contains("Jul 9"))
        assertTrue(formatted.contains("11:30"))
        assertTrue(formatted.contains("AM"))
    }

    @Test
    fun `eventDetailSummary is null when nothing is set`() {
        assertNull(eventDetailSummary(testEvent(intensity = null, note = null), tags = emptyList(), PlainVoice))
    }

    @Test
    fun `eventDetailSummary is null when note is blank, intensity is unset, and there are no tags`() {
        assertNull(eventDetailSummary(testEvent(intensity = null, note = "   "), tags = emptyList(), PlainVoice))
    }

    @Test
    fun `eventDetailSummary shows intensity only when note and tags are unset`() {
        assertEquals(
            PlainVoice.eventIntensityLabel(4),
            eventDetailSummary(testEvent(intensity = 4, note = null), tags = emptyList(), PlainVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows note only when intensity and tags are unset`() {
        assertEquals(
            "Snapped during dinner",
            eventDetailSummary(testEvent(intensity = null, note = "Snapped during dinner"), tags = emptyList(), PlainVoice),
        )
    }

    @Test
    fun `eventDetailSummary joins intensity and note, intensity first`() {
        assertEquals(
            "${PlainVoice.eventIntensityLabel(2)} · Quick one",
            eventDetailSummary(testEvent(intensity = 2, note = "Quick one"), tags = emptyList(), PlainVoice),
        )
    }

    @Test
    fun `eventDetailSummary omits intensity when showIntensity is false, even though it's set`() {
        assertEquals(
            "Quick one",
            eventDetailSummary(testEvent(intensity = 2, note = "Quick one"), tags = emptyList(), PlainVoice, showIntensity = false),
        )
    }

    @Test
    fun `eventDetailSummary showIntensity false still shows duration, note and tags`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))
        val event = testEvent(intensity = 4, note = "Quick one", occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(
            "${PlainVoice.eventDurationLabel("45m")} · Quick one · #dinner",
            eventDetailSummary(event, tags = tags, PlainVoice, showIntensity = false),
        )
    }

    @Test
    fun `eventDetailSummary uses the given voice's intensity copy, not a hardcoded string`() {
        assertEquals(
            IntenseVoice.eventIntensityLabel(5),
            eventDetailSummary(testEvent(intensity = 5, note = null), tags = emptyList(), IntenseVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows tags only when intensity and note are unset`() {
        val tags = listOf(TagEntity(id = 1, name = "work"), TagEntity(id = 2, name = "morning"))

        assertEquals(
            "#work #morning",
            eventDetailSummary(testEvent(intensity = null, note = null), tags = tags, PlainVoice),
        )
    }

    @Test
    fun `eventDetailSummary appends tags last, after intensity and note`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))

        assertEquals(
            "${PlainVoice.eventIntensityLabel(3)} · Quick one · #dinner",
            eventDetailSummary(testEvent(intensity = 3, note = "Quick one"), tags = tags, PlainVoice),
        )
    }

    @Test
    fun `eventDetailSummary is null for an ongoing event with nothing else set - the pill carries the state`() {
        assertNull(
            eventDetailSummary(testEvent(intensity = null, note = null), tags = emptyList(), PlainVoice, isOngoing = true),
        )
    }

    @Test
    fun `eventDetailSummary omits any running label for an ongoing event - just intensity, note and tags`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))

        assertEquals(
            "${PlainVoice.eventIntensityLabel(3)} · Quick one · #dinner",
            eventDetailSummary(testEvent(intensity = 3, note = "Quick one"), tags = tags, PlainVoice, isOngoing = true),
        )
    }

    @Test
    fun `eventDetailSummary shows the duration for a finished event with an endedAt`() {
        val event = testEvent(intensity = null, note = null, occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(PlainVoice.eventDurationLabel("45m"), eventDetailSummary(event, tags = emptyList(), PlainVoice))
    }

    @Test
    fun `eventDetailSummary puts the duration before intensity, note and tags`() {
        val tags = listOf(TagEntity(id = 1, name = "dinner"))
        val event = testEvent(intensity = 3, note = "Quick one", occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(
            "${PlainVoice.eventDurationLabel("45m")} · ${PlainVoice.eventIntensityLabel(3)} · Quick one · #dinner",
            eventDetailSummary(event, tags = tags, PlainVoice),
        )
    }

    @Test
    fun `eventDetailSummary shows no duration when endedAt is null and not ongoing`() {
        val event = testEvent(intensity = 3, note = null, occurredAt = 0L, endedAt = null)

        assertEquals(PlainVoice.eventIntensityLabel(3), eventDetailSummary(event, tags = emptyList(), PlainVoice))
    }

    @Test
    fun `eventDetailSummary shows no duration for an ongoing event even with a stale endedAt`() {
        // isOngoing is caller-asserted (spec: only true for a START_STOP case's open event, which
        // by construction never has an endedAt) — this pins that a stray endedAt never leaks a
        // "lasted N" line onto a still-running event.
        val event = testEvent(intensity = 3, note = null, occurredAt = 0L, endedAt = 999L)

        assertEquals(
            PlainVoice.eventIntensityLabel(3),
            eventDetailSummary(event, tags = emptyList(), PlainVoice, isOngoing = true),
        )
    }

    @Test
    fun `eventDetailSummary hides the duration line when the Case no longer tracks duration`() {
        // A real endedAt, but durationMode is now NONE — the event is a point (spec §9).
        val event = testEvent(intensity = 3, note = null, occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(
            PlainVoice.eventIntensityLabel(3),
            eventDetailSummary(event, tags = emptyList(), PlainVoice, tracksDuration = false),
        )
    }

    @Test
    fun `eventDetailSummary hides the duration line for a zero-length event`() {
        val event = testEvent(intensity = 3, note = null, occurredAt = 10 * 60_000L, endedAt = 10 * 60_000L)

        assertEquals(PlainVoice.eventIntensityLabel(3), eventDetailSummary(event, tags = emptyList(), PlainVoice))
    }

    @Test
    fun `eventDetailSummary hides the duration line when the event is both ongoing and non-tracking`() {
        // The two suppressors compose: isOngoing (a stray endedAt on a reopened event) and
        // tracksDuration = false (Case now NONE) each independently drop the "lasted N" line.
        val event = testEvent(intensity = 3, note = null, occurredAt = 0L, endedAt = 45 * 60_000L)

        assertEquals(
            PlainVoice.eventIntensityLabel(3),
            eventDetailSummary(event, tags = emptyList(), PlainVoice, isOngoing = true, tracksDuration = false),
        )
    }

    // ---- eventDetailSummary primitive overload (Big Picture's detail rows, spec §9) ----

    @Test
    fun `eventDetailSummary primitive overload is null when only a blank note and no duration or intensity`() {
        assertNull(
            eventDetailSummary(
                occurredAt = 0L,
                endedAt = null,
                intensity = null,
                note = "  ",
                tagNames = emptyList(),
                voice = PlainVoice,
                tracksDuration = false,
                showIntensity = false,
            ),
        )
    }

    @Test
    fun `eventDetailSummary primitive overload gives just the duration when intensity is hidden`() {
        assertEquals(
            PlainVoice.eventDurationLabel("40m"),
            eventDetailSummary(
                occurredAt = 0L,
                endedAt = 40 * 60_000L,
                intensity = 3,
                note = null,
                tagNames = emptyList(),
                voice = PlainVoice,
                showIntensity = false,
            ),
        )
    }

    @Test
    fun `eventDetailSummary primitive overload joins tag names with a hash each`() {
        assertEquals(
            "#work #morning",
            eventDetailSummary(
                occurredAt = 0L,
                endedAt = null,
                intensity = null,
                note = null,
                tagNames = listOf("work", "morning"),
                voice = PlainVoice,
            ),
        )
    }

    // ---- formatRate ----

    @Test
    fun `formatRate renders one decimal place and the per-unit suffix`() {
        assertEquals("2.6×/week", formatRate(2.6, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT))
        assertEquals("1.0×/day", formatRate(1.0, ExpectedPer.DAY, VerdictMetric.OCCURRENCE_COUNT))
        assertEquals("1.1×/month", formatRate(1.1, ExpectedPer.MONTH, VerdictMetric.OCCURRENCE_COUNT))
    }

    @Test
    fun `formatRate rounds to one decimal place`() {
        assertEquals("2.4×/week", formatRate(2.36, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT))
    }

    @Test
    fun `formatRate renders a days-active rate without the multiplication sign`() {
        assertEquals("5.6 days/week", formatRate(5.6, ExpectedPer.WEEK, VerdictMetric.DAYS_ACTIVE))
        assertEquals("24.0 days/month", formatRate(24.0, ExpectedPer.MONTH, VerdictMetric.DAYS_ACTIVE))
        assertEquals("60.0 days/3 months", formatRate(60.0, ExpectedPer.QUARTER, VerdictMetric.DAYS_ACTIVE))
    }

    // ---- formatExpectedFrequency ----

    @Test
    fun `formatExpectedFrequency renders a whole-number rate with a tilde`() {
        assertEquals("~5×/week", formatExpectedFrequency(5, ExpectedPer.WEEK, VerdictMetric.OCCURRENCE_COUNT))
        assertEquals("~1×/day", formatExpectedFrequency(1, ExpectedPer.DAY, VerdictMetric.OCCURRENCE_COUNT))
        assertEquals("~2×/month", formatExpectedFrequency(2, ExpectedPer.MONTH, VerdictMetric.OCCURRENCE_COUNT))
    }

    @Test
    fun `formatExpectedFrequency renders a days-active expectation without the multiplication sign`() {
        assertEquals("~4 days/week", formatExpectedFrequency(4, ExpectedPer.WEEK, VerdictMetric.DAYS_ACTIVE))
        assertEquals("~20 days/3 months", formatExpectedFrequency(20, ExpectedPer.QUARTER, VerdictMetric.DAYS_ACTIVE))
    }

    // ---- monthsAgo ----

    @Test
    fun `monthsAgo counts whole calendar months`() {
        val past = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        assertEquals(4L, monthsAgo(past, now, utc))
    }

    @Test
    fun `monthsAgo is zero for less than a full month`() {
        val past = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, utc).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(2026, 3, 20, 0, 0, 0, 0, utc).toInstant().toEpochMilli()

        assertEquals(0L, monthsAgo(past, now, utc))
    }
}
