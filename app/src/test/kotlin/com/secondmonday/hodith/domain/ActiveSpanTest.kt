package com.secondmonday.hodith.domain

import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSpanTest {
    @Test
    fun `isStillRunning is true for a null endedAt on a tracking case`() {
        assertTrue(isStillRunning(endedAt = null, tracksDuration = true, now = 9_000L))
    }

    @Test
    fun `isStillRunning is true for an endedAt still in the future on a tracking case`() {
        assertTrue(isStillRunning(endedAt = 9_500L, tracksDuration = true, now = 9_000L))
    }

    @Test
    fun `isStillRunning is false once endedAt has passed on a tracking case`() {
        assertFalse(isStillRunning(endedAt = 500L, tracksDuration = true, now = 9_000L))
        assertFalse(isStillRunning(endedAt = 9_000L, tracksDuration = true, now = 9_000L))
    }

    @Test
    fun `isStillRunning is always false for a case that doesn't track duration`() {
        assertFalse(isStillRunning(endedAt = null, tracksDuration = false, now = 9_000L))
        assertFalse(isStillRunning(endedAt = 9_500L, tracksDuration = false, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd is the stored endedAt for a finished event on a tracking case`() {
        val event = testEvent(occurredAt = 100L, endedAt = 500L)

        assertEquals(500L, activeSpanEnd(event, tracksDuration = true, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd runs an open event on a tracking case to now`() {
        val event = testEvent(occurredAt = 100L, endedAt = null)

        assertEquals(9_000L, activeSpanEnd(event, tracksDuration = true, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd runs a tracking case's planned-future event to now, not its stated end`() {
        val event = testEvent(occurredAt = 100L, endedAt = 9_500L)

        assertEquals(9_000L, activeSpanEnd(event, tracksDuration = true, now = 9_000L))
    }

    @Test
    fun `activeSpanEnd collapses to occurredAt for a case that doesn't track duration, whatever endedAt is stored`() {
        val finished = testEvent(occurredAt = 100L, endedAt = 500L)
        val open = testEvent(occurredAt = 100L, endedAt = null)

        assertEquals(100L, activeSpanEnd(finished, tracksDuration = false, now = 9_000L))
        assertEquals(100L, activeSpanEnd(open, tracksDuration = false, now = 9_000L))
    }
}
