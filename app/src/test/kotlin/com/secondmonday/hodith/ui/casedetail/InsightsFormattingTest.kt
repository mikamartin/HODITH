package com.secondmonday.hodith.ui.casedetail

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsFormattingTest {
    // ---- formatCompactDecimal ----

    @Test
    fun `formatCompactDecimal keeps one decimal place`() {
        assertEquals("2.3", formatCompactDecimal(2.34))
    }

    @Test
    fun `formatCompactDecimal drops a whole number's trailing zero`() {
        assertEquals("3", formatCompactDecimal(3.0))
    }

    @Test
    fun `formatCompactDecimal drops the zero when rounding lands on a whole number`() {
        assertEquals("3", formatCompactDecimal(2.96))
    }

    @Test
    fun `formatCompactDecimal keeps zero as zero`() {
        assertEquals("0", formatCompactDecimal(0.0))
    }

    @Test
    fun `formatCompactDecimal rounds to a whole number from ten up`() {
        assertEquals("10", formatCompactDecimal(10.0))
        assertEquals("12", formatCompactDecimal(11.6))
    }

    @Test
    fun `formatCompactDecimal stays one decimal just under ten`() {
        assertEquals("9.9", formatCompactDecimal(9.94))
    }

    @Test
    fun `formatCompactDecimal reads ten, not 10 point 0, when rounding carries up to ten`() {
        assertEquals("10", formatCompactDecimal(9.96))
    }

    // ---- formatIntensity ----

    @Test
    fun `formatIntensity drops the trailing zero of a whole average`() {
        assertEquals("3", formatIntensity(3.0))
    }

    @Test
    fun `formatIntensity keeps a fractional average to one decimal place`() {
        assertEquals("3.4", formatIntensity(3.4))
        assertEquals("4.5", formatIntensity(4.45))
    }

    // ---- formatDaysCompact ----

    @Test
    fun `formatDaysCompact is a number with a d suffix`() {
        assertEquals("9d", formatDaysCompact(9.0))
        assertEquals("3.1d", formatDaysCompact(3.1))
        assertEquals("0d", formatDaysCompact(0.0))
    }
}
