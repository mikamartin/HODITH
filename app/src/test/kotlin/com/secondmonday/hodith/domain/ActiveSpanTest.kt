package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSpanTest {
    @Test
    fun `activeSpanEnd is the stored endedAt for a finished event on a tracking case`() {
        val event = testEvent(occurredAt = 100L, endedAt = 500L)

        assertEquals(500L, activeSpanEnd(event, DurationMode.MANUAL, now = 9_000L))
        assertEquals(500L, activeSpanEnd(event, DurationMode.START_STOP, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd runs a still-running START_STOP event to now`() {
        val event = testEvent(occurredAt = 100L, endedAt = null)

        assertEquals(9_000L, activeSpanEnd(event, DurationMode.START_STOP, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd is occurredAt for a MANUAL event with no endedAt`() {
        val event = testEvent(occurredAt = 100L, endedAt = null)

        assertEquals(100L, activeSpanEnd(event, DurationMode.MANUAL, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd collapses to occurredAt for a NONE case whatever endedAt is stored`() {
        val finished = testEvent(occurredAt = 100L, endedAt = 500L)
        val open = testEvent(occurredAt = 100L, endedAt = null)

        assertEquals(100L, activeSpanEnd(finished, DurationMode.NONE, now = 9_000L))
        assertEquals(100L, activeSpanEnd(open, DurationMode.NONE, now = 9_000L))
    }
}
