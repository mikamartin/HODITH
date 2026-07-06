package com.secondmonday.hodith.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeClockTest {
    @Test
    fun `defaults to zero`() {
        assertEquals(0L, FakeClock().nowMillis())
    }

    @Test
    fun `set overrides the current time`() {
        val clock = FakeClock()

        clock.set(1_000L)

        assertEquals(1_000L, clock.nowMillis())
    }

    @Test
    fun `advanceBy adds to the current time`() {
        val clock = FakeClock(1_000L)

        clock.advanceBy(500L)

        assertEquals(1_500L, clock.nowMillis())
    }
}
