package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.domain.FrequencyGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
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
    fun `formatMediumDate includes the year`() {
        assertEquals("Jul 9, 2026", formatMediumDate(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun `formatWeekdayDayDate is weekday and day-of-month`() {
        assertEquals("Thu 9", formatWeekdayDayDate(LocalDate.of(2026, 7, 9)))
    }

    @Test
    fun `formatFrequencyPeriodLabel varies by granularity and routes the weekly wrapper through the given label fn`() {
        val start = LocalDate.of(2026, 7, 9)
        val weekOf = { date: String -> "wk:$date" }

        assertEquals("Jul 9", formatFrequencyPeriodLabel(start, FrequencyGranularity.DAY, Locale.US, weekOf))
        assertEquals("wk:Jul 9", formatFrequencyPeriodLabel(start, FrequencyGranularity.WEEK, Locale.US, weekOf))
        assertEquals("Jul 2026", formatFrequencyPeriodLabel(start, FrequencyGranularity.MONTH, Locale.US, weekOf))
    }
}
