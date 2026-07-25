package com.secondmonday.hodith.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

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
}
