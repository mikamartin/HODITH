package com.secondmonday.hodith.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatCardRowsTest {
    private fun gaps(
        shortest: Long? = 1,
        longest: Long = 9,
        current: Long = 2,
        average: Double = 3.1,
        bursty: Boolean = false,
        longestStreak: Int = 0,
        averageStreak: Double = 0.0,
    ) = GapsDisplay(
        longestGapDays = longest,
        currentGapDays = current,
        averageGapDays = average,
        isBursty = bursty,
        longestStreakDays = longestStreak,
        averageStreakDays = averageStreak,
        shortestGapDays = shortest,
    )

    private fun duration(
        average: Double = 130.0,
        longest: Long = 400,
        total: Long = 4030,
        shortest: Long = 25,
    ) = DurationDisplay(
        averageMinutes = average,
        longestMinutes = longest,
        totalMinutes = total,
        shortestMinutes = shortest,
    )

    @Test
    fun `gaps min avg and max read in the share card's compact day notation`() {
        val rows = gapsStatRows(gaps(shortest = 1, average = 3.1, longest = 9))

        assertEquals(MinAvgMaxValues(min = "1d", avg = "3.1d", max = "9d"), rows.minAvgMax)
    }

    @Test
    fun `gaps figures are the share card's Square fixture, read from one function`() {
        val rows = gapsStatRows(gaps(shortest = 1, current = 2, average = 3.1, longest = 9, longestStreak = 6, averageStreak = 2.5))

        assertEquals(MinAvgMaxValues(min = "1d", avg = "3.1d", max = "9d"), rows.minAvgMax)
        assertEquals("2d", rows.currentGap)
        assertEquals("6d", rows.longestStreak)
        assertEquals("2.5d", rows.averageStreak)
    }

    @Test
    fun `gaps min avg and max are absent before there are two events`() {
        assertNull(gapsStatRows(gaps(shortest = null)).minAvgMax)
    }

    @Test
    fun `gaps average drops a whole number's decimal point`() {
        assertEquals("3d", gapsStatRows(gaps(average = 3.0)).minAvgMax?.avg)
    }

    @Test
    fun `gaps current gap and streaks use the same day notation`() {
        val rows = gapsStatRows(gaps(current = 2, longestStreak = 4, averageStreak = 2.5))

        assertEquals("2d", rows.currentGap)
        assertEquals("4d", rows.longestStreak)
        assertEquals("2.5d", rows.averageStreak)
    }

    @Test
    fun `gaps current gap reads zero while an event is running`() {
        assertEquals("0d", gapsStatRows(gaps(current = 0)).currentGap)
    }

    @Test
    fun `gaps bursty flag passes through unchanged`() {
        assertTrue(gapsStatRows(gaps(bursty = true)).isBursty)
        assertFalse(gapsStatRows(gaps(bursty = false)).isBursty)
    }

    @Test
    fun `duration min avg and max read in the duration notation`() {
        assertEquals(
            MinAvgMaxValues(min = "25m", avg = "2h 10m", max = "6h 40m"),
            durationMinAvgMax(duration(average = 130.0, longest = 400, shortest = 25)),
        )
    }

    @Test
    fun `duration average rounds half up to whole minutes before formatting`() {
        assertEquals("2h 11m", durationMinAvgMax(duration(average = 130.5)).avg)
    }
}
