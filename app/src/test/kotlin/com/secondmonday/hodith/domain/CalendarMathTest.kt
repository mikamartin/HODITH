package com.secondmonday.hodith.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * `daysBetween` is the shared calendar-date difference behind `VerdictEngine` / `TriggerEngine` /
 * `StatsEngine` / `CheckIn` / `InsightsEngine.computeGapStats`. These pin that it counts calendar
 * days, not elapsed 24-hour chunks, across both daylight-saving transitions — the property every
 * caller relies on and that a raw `millis / MILLIS_PER_DAY` would get wrong. Mirrors
 * `CalendarGridTest`'s style: an explicit zone argument, no `TimeZone.setDefault`.
 */
class CalendarMathTest {
    private val newYork = ZoneId.of("America/New_York")

    private fun noonMillis(date: LocalDate): Long =
        date
            .atTime(12, 0)
            .atZone(newYork)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `daysBetween counts calendar days across a spring-forward, not fixed 24h chunks`() {
        // US DST springs forward on 2026-03-08 (a 23-hour local day).
        val from = noonMillis(LocalDate.of(2026, 3, 7))
        val to = noonMillis(LocalDate.of(2026, 3, 13))

        assertEquals(6L, daysBetween(from, to, newYork))
        // Only 5 days, 23 hours of elapsed millis — a raw millis / MILLIS_PER_DAY would report 5.
        assertTrue(to - from < 6 * MILLIS_PER_DAY)
    }

    @Test
    fun `daysBetween counts calendar days across a fall-back, not fixed 24h chunks`() {
        // US DST falls back on 2026-11-01 (a 25-hour local day).
        val from = noonMillis(LocalDate.of(2026, 10, 31))
        val to = noonMillis(LocalDate.of(2026, 11, 6))

        assertEquals(6L, daysBetween(from, to, newYork))
        // 6 days, 1 hour of elapsed millis — a raw millis / MILLIS_PER_DAY would still report 6
        // here, but the extra hour shows this is a calendar-date difference, not an elapsed one.
        assertTrue(to - from > 6 * MILLIS_PER_DAY)
    }

    @Test
    fun `daysBetween is zero for two instants on the same calendar day, even the DST day`() {
        val morning =
            LocalDate
                .of(2026, 3, 8)
                .atTime(1, 0)
                .atZone(newYork)
                .toInstant()
                .toEpochMilli()
        val evening =
            LocalDate
                .of(2026, 3, 8)
                .atTime(23, 0)
                .atZone(newYork)
                .toInstant()
                .toEpochMilli()

        assertEquals(0L, daysBetween(morning, evening, newYork))
    }
}
