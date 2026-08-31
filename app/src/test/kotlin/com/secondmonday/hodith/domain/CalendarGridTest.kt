package com.secondmonday.hodith.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarGridTest {
    @Test
    fun `weeksInGrid chunks every week into exactly 7 days`() {
        val weeks = weeksInGrid(YearMonth.of(2026, 7))

        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun `weeksInGrid pads a month that starts mid-week back to the preceding Monday`() {
        // July 2026 starts on a Wednesday.
        val weeks = weeksInGrid(YearMonth.of(2026, 7))

        assertEquals(DayOfWeek.MONDAY, weeks.first().first().dayOfWeek)
        assertEquals(YearMonth.of(2026, 6).atDay(29), weeks.first().first())
    }

    @Test
    fun `weeksInGrid pads a month that ends mid-week forward to the following Sunday`() {
        // July 2026 ends on a Friday.
        val weeks = weeksInGrid(YearMonth.of(2026, 7))

        assertEquals(DayOfWeek.SUNDAY, weeks.last().last().dayOfWeek)
        assertEquals(YearMonth.of(2026, 8).atDay(2), weeks.last().last())
    }

    @Test
    fun `weeksInGrid needs no padding when the month already starts and ends on week boundaries`() {
        // June 2026 starts on a Monday and ends on a Tuesday.
        val weeks = weeksInGrid(YearMonth.of(2026, 6))

        assertEquals(YearMonth.of(2026, 6).atDay(1), weeks.first().first())
        assertEquals(YearMonth.of(2026, 6).atDay(1).dayOfWeek, DayOfWeek.MONDAY)
    }

    @Test
    fun `weeksInGrid every day of the target month appears exactly once`() {
        val month = YearMonth.of(2026, 2)

        val daysInMonth = weeksInGrid(month).flatten().filter { it.month == month.month }

        assertEquals(month.lengthOfMonth(), daysInMonth.size)
        assertEquals(daysInMonth.distinct().size, daysInMonth.size)
    }

    private fun utcMillis(
        date: LocalDate,
        hour: Int,
    ): Long = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `datesCovered yields a single date for a span that starts and ends the same day`() {
        val day = LocalDate.of(2026, 2, 3)

        val covered = datesCovered(utcMillis(day, 8), utcMillis(day, 17), ZoneOffset.UTC)

        assertEquals(listOf(day), covered)
    }

    @Test
    fun `datesCovered yields every day from start to end inclusive`() {
        val covered =
            datesCovered(
                utcMillis(LocalDate.of(2026, 2, 1), 10),
                utcMillis(LocalDate.of(2026, 2, 4), 9),
                ZoneOffset.UTC,
            )

        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 3),
                LocalDate.of(2026, 2, 4),
            ),
            covered,
        )
    }

    @Test
    fun `datesCovered marks both days for a span that crosses midnight`() {
        val covered =
            datesCovered(
                utcMillis(LocalDate.of(2026, 2, 1), 23) + 30 * 60_000L,
                utcMillis(LocalDate.of(2026, 2, 2), 0) + 30 * 60_000L,
                ZoneOffset.UTC,
            )

        assertEquals(listOf(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)), covered)
    }

    @Test
    fun `datesCovered floors an end that precedes the start to the start day`() {
        val covered =
            datesCovered(
                utcMillis(LocalDate.of(2026, 2, 10), 12),
                utcMillis(LocalDate.of(2026, 2, 8), 12),
                ZoneOffset.UTC,
            )

        assertEquals(listOf(LocalDate.of(2026, 2, 10)), covered)
    }

    @Test
    fun `datesCovered resolves each end in the supplied zone`() {
        val newYork = ZoneId.of("America/New_York")
        // 02:00 UTC on Feb 2 is 21:00 EST on Feb 1.
        val covered =
            datesCovered(
                utcMillis(LocalDate.of(2026, 2, 1), 18),
                utcMillis(LocalDate.of(2026, 2, 2), 2),
                newYork,
            )

        assertEquals(listOf(LocalDate.of(2026, 2, 1)), covered)
    }

    @Test
    fun `datesCovered spans a daylight-saving transition without dropping or doubling a day`() {
        val newYork = ZoneId.of("America/New_York")
        // US DST springs forward on 2026-03-08 (a 23-hour local day).
        val noonOn: (LocalDate) -> Long = {
            it
                .atTime(12, 0)
                .atZone(newYork)
                .toInstant()
                .toEpochMilli()
        }

        val covered = datesCovered(noonOn(LocalDate.of(2026, 3, 7)), noonOn(LocalDate.of(2026, 3, 9)), newYork)

        assertEquals(
            listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 9)),
            covered,
        )
    }

    @Test
    fun `spansMultipleDays is false for a span within one calendar day`() {
        val day = LocalDate.of(2026, 2, 3)

        assertEquals(false, spansMultipleDays(utcMillis(day, 8), utcMillis(day, 23), ZoneOffset.UTC))
    }

    @Test
    fun `spansMultipleDays is true once a span crosses midnight`() {
        assertTrue(
            spansMultipleDays(
                utcMillis(LocalDate.of(2026, 2, 1), 23) + 30 * 60_000L,
                utcMillis(LocalDate.of(2026, 2, 2), 0) + 30 * 60_000L,
                ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun `spansMultipleDays floors an end before the start, so it is false`() {
        assertEquals(
            false,
            spansMultipleDays(
                utcMillis(LocalDate.of(2026, 2, 10), 12),
                utcMillis(LocalDate.of(2026, 2, 8), 12),
                ZoneOffset.UTC,
            ),
        )
    }

    @Test
    fun `spansMultipleDays resolves the end in the supplied zone`() {
        val newYork = ZoneId.of("America/New_York")
        // 02:00 UTC Feb 2 is still Feb 1 in New York.
        assertEquals(
            false,
            spansMultipleDays(
                utcMillis(LocalDate.of(2026, 2, 1), 18),
                utcMillis(LocalDate.of(2026, 2, 2), 2),
                newYork,
            ),
        )
    }
}
