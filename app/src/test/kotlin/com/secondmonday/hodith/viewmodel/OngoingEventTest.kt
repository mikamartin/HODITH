package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import com.secondmonday.hodith.testsupport.testCase
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 9_000L

class OngoingEventTest {
    // --- ongoingEventIn ---

    @Test
    fun `ongoingEventIn finds the open event for a tracking case`() {
        val case = testCase(tracksDuration = true)
        val open = testEvent(occurredAt = 0L, endedAt = null)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertEquals(open, ongoingEventIn(case, listOf(closed, open), NOW))
    }

    @Test
    fun `ongoingEventIn finds a planned-future event for a tracking case`() {
        val case = testCase(tracksDuration = true)
        val planned = testEvent(occurredAt = 0L, endedAt = NOW + 500L)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertEquals(planned, ongoingEventIn(case, listOf(closed, planned), NOW))
    }

    @Test
    fun `ongoingEventIn is null when a tracking case has no open or planned event`() {
        val case = testCase(tracksDuration = true)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertNull(ongoingEventIn(case, listOf(closed), NOW))
    }

    @Test
    fun `ongoingEventIn ignores a null endedAt on a case that doesn't track duration`() {
        val case = testCase(tracksDuration = false)
        val event = testEvent(occurredAt = 0L, endedAt = null)

        assertNull(ongoingEventIn(case, listOf(event), NOW))
    }

    @Test
    fun `ongoingEventIn returns the earliest-started open event when several are running`() {
        val case = testCase(tracksDuration = true)
        val later = testEvent(id = 2L, occurredAt = 500L, endedAt = null)
        val earlier = testEvent(id = 1L, occurredAt = 100L, endedAt = null)

        assertEquals(earlier, ongoingEventIn(case, listOf(later, earlier), NOW))
    }

    // --- ongoingEventsIn ---

    @Test
    fun `ongoingEventsIn returns every open event, earliest first`() {
        val case = testCase(tracksDuration = true)
        val third = testEvent(id = 3L, occurredAt = 900L, endedAt = null)
        val first = testEvent(id = 1L, occurredAt = 100L, endedAt = null)
        val second = testEvent(id = 2L, occurredAt = 400L, endedAt = null)
        val closed = testEvent(id = 4L, occurredAt = 200L, endedAt = 300L)

        assertEquals(listOf(first, second, third), ongoingEventsIn(case, listOf(third, closed, first, second), NOW))
    }

    @Test
    fun `ongoingEventsIn is empty for a tracking case with no open or planned events`() {
        val case = testCase(tracksDuration = true)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertTrue(ongoingEventsIn(case, listOf(closed), NOW).isEmpty())
    }

    @Test
    fun `ongoingEventsIn is empty for a case that doesn't track duration even with null endedAt events`() {
        val case = testCase(tracksDuration = false)
        val event = testEvent(occurredAt = 0L, endedAt = null)

        assertTrue(ongoingEventsIn(case, listOf(event), NOW).isEmpty())
    }

    // --- formatElapsedDuration ---

    @Test
    fun `formatElapsedDuration renders under an hour as minutes only`() {
        assertEquals("0m", formatElapsedDuration(startMillis = 0L, nowMillis = 0L))
        assertEquals("45m", formatElapsedDuration(startMillis = 0L, nowMillis = 45 * MILLIS_PER_MINUTE))
        assertEquals("59m", formatElapsedDuration(startMillis = 0L, nowMillis = 59 * MILLIS_PER_MINUTE))
    }

    @Test
    fun `formatElapsedDuration renders an hour or more but under a day as hours and minutes`() {
        assertEquals("1h 0m", formatElapsedDuration(startMillis = 0L, nowMillis = 60 * MILLIS_PER_MINUTE))
        assertEquals("2h 14m", formatElapsedDuration(startMillis = 0L, nowMillis = 2 * MILLIS_PER_HOUR + 14 * MILLIS_PER_MINUTE))
        assertEquals("23h 59m", formatElapsedDuration(startMillis = 0L, nowMillis = MILLIS_PER_DAY - MILLIS_PER_MINUTE))
    }

    @Test
    fun `formatElapsedDuration renders a day or more as days and hours`() {
        assertEquals("1d 0h", formatElapsedDuration(startMillis = 0L, nowMillis = MILLIS_PER_DAY))
        assertEquals("2d 3h", formatElapsedDuration(startMillis = 0L, nowMillis = 2 * MILLIS_PER_DAY + 3 * MILLIS_PER_HOUR))
    }
}
