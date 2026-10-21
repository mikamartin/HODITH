package com.secondmonday.hodith.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarGridTest {
    @Test
    fun `weeksInGrid chunks every week into exactly 7 days`() {
        val weeks = weeksInGrid(YearMonth(2026, Month.JULY))

        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun `weeksInGrid pads a month that starts mid-week back to the preceding Monday`() {
        // July 2026 starts on a Wednesday.
        val weeks = weeksInGrid(YearMonth(2026, Month.JULY))

        assertEquals(DayOfWeek.MONDAY, weeks.first().first().dayOfWeek)
        assertEquals(YearMonth(2026, Month.JUNE).atDay(29), weeks.first().first())
    }

    @Test
    fun `weeksInGrid pads a month that ends mid-week forward to the following Sunday`() {
        // July 2026 ends on a Friday.
        val weeks = weeksInGrid(YearMonth(2026, Month.JULY))

        assertEquals(DayOfWeek.SUNDAY, weeks.last().last().dayOfWeek)
        assertEquals(YearMonth(2026, Month.AUGUST).atDay(2), weeks.last().last())
    }

    @Test
    fun `weeksInGrid needs no padding when the month already starts and ends on week boundaries`() {
        // June 2026 starts on a Monday and ends on a Tuesday.
        val weeks = weeksInGrid(YearMonth(2026, Month.JUNE))

        assertEquals(YearMonth(2026, Month.JUNE).atDay(1), weeks.first().first())
        assertEquals(DayOfWeek.MONDAY, YearMonth(2026, Month.JUNE).atDay(1).dayOfWeek)
    }

    @Test
    fun `weeksInGrid every day of the target month appears exactly once`() {
        val month = YearMonth(2026, Month.FEBRUARY)

        val daysInMonth = weeksInGrid(month).flatten().filter { it.month == month.month }
        val lengthOfMonth = month.atDay(1).daysUntil(month.atEndOfMonth()) + 1

        assertEquals(lengthOfMonth, daysInMonth.size)
        assertEquals(daysInMonth.distinct().size, daysInMonth.size)
    }

    private fun utcMillis(
        date: LocalDate,
        hour: Int,
    ): Long = date.atTime(hour, 0).toInstant(UtcOffset.ZERO).toEpochMilliseconds()

    @Test
    fun `datesCovered yields a single date for a span that starts and ends the same day`() {
        val day = LocalDate(2026, 2, 3)

        val covered = datesCovered(utcMillis(day, 8), utcMillis(day, 17), TimeZone.UTC)

        assertEquals(listOf(day), covered)
    }

    @Test
    fun `datesCovered yields every day from start to end inclusive`() {
        val covered =
            datesCovered(
                utcMillis(LocalDate(2026, 2, 1), 10),
                utcMillis(LocalDate(2026, 2, 4), 9),
                TimeZone.UTC,
            )

        assertEquals(
            listOf(
                LocalDate(2026, 2, 1),
                LocalDate(2026, 2, 2),
                LocalDate(2026, 2, 3),
                LocalDate(2026, 2, 4),
            ),
            covered,
        )
    }

    @Test
    fun `datesCovered marks both days for a span that crosses midnight`() {
        val covered =
            datesCovered(
                utcMillis(LocalDate(2026, 2, 1), 23) + 30 * 60_000L,
                utcMillis(LocalDate(2026, 2, 2), 0) + 30 * 60_000L,
                TimeZone.UTC,
            )

        assertEquals(listOf(LocalDate(2026, 2, 1), LocalDate(2026, 2, 2)), covered)
    }

    @Test
    fun `datesCovered floors an end that precedes the start to the start day`() {
        val covered =
            datesCovered(
                utcMillis(LocalDate(2026, 2, 10), 12),
                utcMillis(LocalDate(2026, 2, 8), 12),
                TimeZone.UTC,
            )

        assertEquals(listOf(LocalDate(2026, 2, 10)), covered)
    }

    @Test
    fun `datesCovered resolves the end in a separately supplied endZone, not the start zone`() {
        // A still-open span: the start resolves in the event's own captured offset (UTC here),
        // the open end resolves in a live current zone 10 hours west of it â€” enough to roll the
        // end's calendar date back a day if endZone were ignored in favor of the start zone.
        val start = utcMillis(LocalDate(2026, 2, 1), 22)
        val end = utcMillis(LocalDate(2026, 2, 2), 2)

        val covered = datesCovered(start, end, TimeZone.UTC, endZone = UtcOffset(hours = -10).asTimeZone())

        assertEquals(listOf(LocalDate(2026, 2, 1)), covered)
    }

    @Test
    fun `datesCovered resolves each end in the supplied zone`() {
        val newYork = TimeZone.of("America/New_York")
        // 02:00 UTC on Feb 2 is 21:00 EST on Feb 1.
        val covered =
            datesCovered(
                utcMillis(LocalDate(2026, 2, 1), 18),
                utcMillis(LocalDate(2026, 2, 2), 2),
                newYork,
            )

        assertEquals(listOf(LocalDate(2026, 2, 1)), covered)
    }

    @Test
    fun `datesCovered spans a daylight-saving transition without dropping or doubling a day`() {
        val newYork = TimeZone.of("America/New_York")
        // US DST springs forward on 2026-03-08 (a 23-hour local day).
        val noonOn: (LocalDate) -> Long = {
            it.atTime(12, 0).toInstant(newYork).toEpochMilliseconds()
        }

        val covered = datesCovered(noonOn(LocalDate(2026, 3, 7)), noonOn(LocalDate(2026, 3, 9)), newYork)

        assertEquals(
            listOf(LocalDate(2026, 3, 7), LocalDate(2026, 3, 8), LocalDate(2026, 3, 9)),
            covered,
        )
    }

    @Test
    fun `spansMultipleDays is false for a span within one calendar day`() {
        val day = LocalDate(2026, 2, 3)

        assertEquals(false, spansMultipleDays(utcMillis(day, 8), utcMillis(day, 23), TimeZone.UTC))
    }

    @Test
    fun `spansMultipleDays is true once a span crosses midnight`() {
        assertTrue(
            spansMultipleDays(
                utcMillis(LocalDate(2026, 2, 1), 23) + 30 * 60_000L,
                utcMillis(LocalDate(2026, 2, 2), 0) + 30 * 60_000L,
                TimeZone.UTC,
            ),
        )
    }

    @Test
    fun `spansMultipleDays floors an end before the start, so it is false`() {
        assertEquals(
            false,
            spansMultipleDays(
                utcMillis(LocalDate(2026, 2, 10), 12),
                utcMillis(LocalDate(2026, 2, 8), 12),
                TimeZone.UTC,
            ),
        )
    }

    @Test
    fun `spansMultipleDays resolves the end in a separately supplied endZone, not the start zone`() {
        val start = utcMillis(LocalDate(2026, 2, 1), 22)
        val end = utcMillis(LocalDate(2026, 2, 2), 2)

        assertEquals(false, spansMultipleDays(start, end, TimeZone.UTC, endZone = UtcOffset(hours = -10).asTimeZone()))
    }

    @Test
    fun `spansMultipleDays resolves the end in the supplied zone`() {
        val newYork = TimeZone.of("America/New_York")
        // 02:00 UTC Feb 2 is still Feb 1 in New York.
        assertEquals(
            false,
            spansMultipleDays(
                utcMillis(LocalDate(2026, 2, 1), 18),
                utcMillis(LocalDate(2026, 2, 2), 2),
                newYork,
            ),
        )
    }
}
