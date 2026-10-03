package com.secondmonday.hodith.domain

import com.secondmonday.hodith.testsupport.eventAtDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroRateTest {
    private fun trend(
        recent: Int,
        prior: Int,
        direction: TrendDirection =
            when {
                recent > prior -> TrendDirection.UP
                recent < prior -> TrendDirection.DOWN
                else -> TrendDirection.FLAT
            },
    ) = TrendStats(direction = direction, recentCount = recent, priorCount = prior)

    // ---- pickRateUnit ----

    @Test
    fun `pickRateUnit picks DAY at exactly one event a day`() {
        assertEquals(RateUnit.DAY, pickRateUnit(eventCount = 30, windowDays = 30))
    }

    @Test
    fun `pickRateUnit picks WEEK just under one a day`() {
        assertEquals(RateUnit.WEEK, pickRateUnit(eventCount = 29, windowDays = 30))
    }

    @Test
    fun `pickRateUnit picks WEEK at exactly one event a week`() {
        assertEquals(RateUnit.WEEK, pickRateUnit(eventCount = 5, windowDays = 35))
    }

    @Test
    fun `pickRateUnit picks MONTH just under one a week`() {
        assertEquals(RateUnit.MONTH, pickRateUnit(eventCount = 4, windowDays = 30))
    }

    @Test
    fun `pickRateUnit keeps MONTH below one a month`() {
        assertEquals(RateUnit.MONTH, pickRateUnit(eventCount = 5, windowDays = 400))
    }

    // ---- computeHeroRate eligibility ----

    @Test
    fun `computeHeroRate is null below the minimum event count`() {
        assertNull(computeHeroRate(HERO_RATE_MIN_EVENTS - 1, observedDays = 90, trendStats = null))
    }

    @Test
    fun `computeHeroRate is null below the minimum observed days`() {
        assertNull(computeHeroRate(10, observedDays = HERO_RATE_MIN_DAYS - 1, trendStats = null))
    }

    @Test
    fun `computeHeroRate is present at exactly both minimums`() {
        assertNotNull(computeHeroRate(HERO_RATE_MIN_EVENTS, observedDays = HERO_RATE_MIN_DAYS, trendStats = null))
    }

    // ---- computeHeroRate overall basis (no trend) ----

    @Test
    fun `computeHeroRate without a trend is the overall rate with no comparison`() {
        // 31 events over 94 days is about 0.33 a day, so 2.3 a week.
        val result = computeHeroRate(31, observedDays = 94, trendStats = null)!!

        assertEquals(RateUnit.WEEK, result.unit)
        assertEquals(31 * 7 / 94.0, result.value, 0.0001)
        assertNull(result.comparison)
        assertFalse(result.belowOnePerMonth)
    }

    @Test
    fun `computeHeroRate without a trend flags an overall rate under one a month`() {
        val result = computeHeroRate(5, observedDays = 400, trendStats = null)!!

        assertEquals(RateUnit.MONTH, result.unit)
        assertTrue(result.belowOnePerMonth)
    }

    // ---- computeHeroRate trend basis ----

    @Test
    fun `computeHeroRate with a trend headlines the last window and converts the prior into the same unit`() {
        // 9 events in the last 30 days is 2.1 a week; the 6 before are 1.4 a week.
        val result = computeHeroRate(31, observedDays = 94, trendStats = trend(recent = 9, prior = 6))!!

        assertEquals(RateUnit.WEEK, result.unit)
        assertEquals(9 * 7 / 30.0, result.value, 0.0001)
        assertEquals(TrendDirection.UP, result.comparison!!.direction)
        assertEquals(6 * 7 / 30.0, result.comparison.priorValue, 0.0001)
    }

    @Test
    fun `computeHeroRate with a trend reports a flat comparison`() {
        val result = computeHeroRate(31, observedDays = 94, trendStats = trend(recent = 7, prior = 7))!!

        assertEquals(TrendDirection.FLAT, result.comparison!!.direction)
    }

    @Test
    fun `computeHeroRate with a trend reports a down comparison`() {
        val result = computeHeroRate(31, observedDays = 94, trendStats = trend(recent = 4, prior = 8))!!

        assertEquals(TrendDirection.DOWN, result.comparison!!.direction)
    }

    @Test
    fun `computeHeroRate picks the unit from the last window's own count, not the overall one`() {
        // Overall 100 events in 70 days would be per day, but the last 30 days hold only 4.
        val result = computeHeroRate(100, observedDays = 70, trendStats = trend(recent = 4, prior = 30))!!

        assertEquals(RateUnit.MONTH, result.unit)
        assertEquals(4.0, result.value, 0.0001)
        assertEquals(30.0, result.comparison!!.priorValue, 0.0001)
    }

    @Test
    fun `computeHeroRate converts to a daily unit when the last window averages a day or more`() {
        val result = computeHeroRate(100, observedDays = 70, trendStats = trend(recent = 52, prior = 30))!!

        assertEquals(RateUnit.DAY, result.unit)
        assertEquals(52 / 30.0, result.value, 0.0001)
        assertEquals(30 / 30.0, result.comparison!!.priorValue, 0.0001)
    }

    @Test
    fun `computeHeroRate keeps a busy last window's rate in the daily unit with no month flag`() {
        val result = computeHeroRate(200, observedDays = 90, trendStats = trend(recent = 150, prior = 20))!!

        assertEquals(RateUnit.DAY, result.unit)
        assertEquals(5.0, result.value, 0.0001)
        assertFalse(result.belowOnePerMonth)
        assertEquals(TrendDirection.UP, result.comparison!!.direction)
        assertEquals(20 / 30.0, result.comparison.priorValue, 0.0001)
    }

    @Test
    fun `computeHeroRate with one event in the last window is exactly one a month, not below it`() {
        val result = computeHeroRate(5, observedDays = 400, trendStats = trend(recent = 1, prior = 0))!!

        assertEquals(RateUnit.MONTH, result.unit)
        assertEquals(1.0, result.value, 0.0001)
        assertFalse(result.belowOnePerMonth)
        assertEquals(0.0, result.comparison!!.priorValue, 0.0001)
    }

    @Test
    fun `computeHeroRate with no events in the last window is below one a month`() {
        val result = computeHeroRate(5, observedDays = 400, trendStats = trend(recent = 0, prior = 1))!!

        assertEquals(RateUnit.MONTH, result.unit)
        assertEquals(0.0, result.value, 0.0001)
        assertTrue(result.belowOnePerMonth)
        assertEquals(TrendDirection.DOWN, result.comparison!!.direction)
        assertEquals(1.0, result.comparison.priorValue, 0.0001)
    }

    // ---- caseHeroRate ----

    @Test
    fun `caseHeroRate is null below the minimum event count`() {
        val events = (0L until HERO_RATE_MIN_EVENTS - 1).map { eventAtDay(it) }

        assertNull(caseHeroRate(events, spanDays = 400, now = 0L))
    }

    @Test
    fun `caseHeroRate on a short span is the overall rate with no comparison`() {
        // Five events over 20 days is 1.75 a week; a span this short has no trend window to compare.
        val events = (0L until 5L).map { eventAtDay(it) }

        val result = caseHeroRate(events, spanDays = 20, now = 0L)!!

        assertEquals(RateUnit.WEEK, result.unit)
        assertEquals(1.75, result.value, 0.0001)
        assertNull(result.comparison)
    }
}
