package com.secondmonday.hodith.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DigitInputTest {
    @Test
    fun `filterDigitInput strips non-digit characters`() {
        assertEquals("123", filterDigitInput("1a2b3c", maxDigits = 5))
    }

    @Test
    fun `filterDigitInput truncates beyond the cap`() {
        assertEquals("12345", filterDigitInput("1234567", maxDigits = 5))
    }

    @Test
    fun `filterDigitInput leaves input under the cap untouched`() {
        assertEquals("12", filterDigitInput("12", maxDigits = 5))
    }

    @Test
    fun `filterDigitInput on an empty string returns an empty string`() {
        assertEquals("", filterDigitInput("", maxDigits = 5))
    }
}
