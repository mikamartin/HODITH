package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.domain.FrequencyGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Covers the pure formatters consolidated into [EventTimeFormat] from the three separate
 * `ofPattern` copies that lived in `CaseDetailViewModel`, `BigPictureGrid` and `InsightsTab`
 * (spec §15, PROGRESS.md "Satellite · 12h/24h time format"). [formatEventTime] /
 * [formatEventTimeOfDay] are exercised by `CaseDetailFormattingTest` / `LogDetailViewModelTest`.
 */
class EventTimeFormatTest {
    @Test
    fun `formatClockTime renders 12-hour with an AM PM marker`() {
        val formatted = formatClockTime(LocalTime.of(15, 30), use24Hour = false)

        assertTrue(formatted.contains("3:30"))
        assertTrue(formatted.contains("PM"))
    }

    @Test
    fun `formatClockTime renders 24-hour with no marker`() {
        val formatted = formatClockTime(LocalTime.of(15, 30), use24Hour = true)

        assertEquals("15:30", formatted)
        assertFalse(formatted.contains("PM"))
    }

    @Test
    fun `formatClockTime zero-pads the 24-hour morning hour`() {
        assertEquals("09:05", formatClockTime(LocalTime.of(9, 5), use24Hour = true))
    }

    @Test
    fun `formatSpanDate is month and day, no year`() {
        assertEquals("Jul 9", formatSpanDate(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun `formatSpanDateTime is month, day and time with no year, in both clock formats and the given zone`() {
        val utc = ZoneId.of("UTC")
        val millis = ZonedDateTime.of(2026, 7, 9, 8, 2, 0, 0, utc).toInstant().toEpochMilli()

        val twelve = formatSpanDateTime(millis, use24Hour = false, zone = utc)
        assertEquals("Jul 9, 8:02 AM", twelve)
        assertFalse("expected no year in \"$twelve\"", twelve.contains("2026"))

        assertEquals("Jul 9, 08:02", formatSpanDateTime(millis, use24Hour = true, zone = utc))
        // America/New_York is UTC-4 in July, so 08:02 UTC is 04:02 local.
        assertEquals("Jul 9, 4:02 AM", formatSpanDateTime(millis, use24Hour = false, zone = ZoneId.of("America/New_York")))
    }

    @Test
    fun `formatMediumDate includes the year`() {
        assertEquals("Jul 9, 2026", formatMediumDate(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun `formatWeekdayDayDate is weekday and day-of-month`() {
        assertEquals("Thu 9", formatWeekdayDayDate(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun `formatFrequencyTickLabel is numeric and short, varying by granularity`() {
        val start = LocalDate.of(2026, 7, 9)

        assertEquals("9", formatFrequencyTickLabel(start, FrequencyGranularity.DAY, Locale.US))
        assertEquals("7/9", formatFrequencyTickLabel(start, FrequencyGranularity.WEEK, Locale.US))
        assertEquals("Jul", formatFrequencyTickLabel(start, FrequencyGranularity.MONTH, Locale.US))
    }

    @Test
    fun `frequencyTickCount maps each granularity to its shipped density`() {
        // Pinned so a swapped WEEK/MONTH mapping (or a DAY that stops being "every bar") shows up
        // here on the JVM instead of only in the Compose-level instrumented test (spec S9).
        assertEquals(12, frequencyTickCount(FrequencyGranularity.DAY))
        assertEquals(6, frequencyTickCount(FrequencyGranularity.WEEK))
        assertEquals(6, frequencyTickCount(FrequencyGranularity.MONTH))
    }

    @Test
    fun `frequencyTickIndices labels every bar when the tick count equals the bar count`() {
        assertEquals((0..11).toList(), frequencyTickIndices(barCount = 12, tickCount = 12))
    }

    @Test
    fun `frequencyTickIndices centers ticks in each slice without pinning either endpoint`() {
        // The "every 2nd"/"every 3rd" strategies HODITH ships deliberately don't force bar 0 or
        // bar 11 the way an interpolation between the two ends would — that's what crowded a tick
        // right next to a forced endpoint before (spec S9). 12 bars / 4 ticks -> 3-wide slices,
        // centered at 1, 4, 7, 10 — neither end is touched at all.
        assertEquals(listOf(1, 4, 7, 10), frequencyTickIndices(barCount = 12, tickCount = 4))
    }

    @Test
    fun `frequencyTickIndices spreads gaps evenly rather than concentrating rounding error at one edge`() {
        val indices = frequencyTickIndices(barCount = 12, tickCount = 6)

        assertEquals(listOf(1, 3, 5, 7, 9, 11), indices)
        val gaps = indices.zipWithNext { a, b -> b - a }
        assertTrue("expected uniform gaps, got $gaps", gaps.all { it == gaps.first() })
    }
}
