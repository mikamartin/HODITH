package com.secondmonday.hodith.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemClockTest {
    @Test
    fun `nowMillis returns a value close to the real system clock`() {
        val before = System.currentTimeMillis()
        val result = SystemClock().nowMillis()
        val after = System.currentTimeMillis()

        assertTrue(result in before..after)
    }
}
