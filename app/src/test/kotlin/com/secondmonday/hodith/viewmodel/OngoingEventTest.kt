package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OngoingEventTest {
    // --- ongoingEventIn ---

    @Test
    fun `ongoingEventIn finds the open event for a START_STOP case`() {
        val case = testCase(durationMode = DurationMode.START_STOP)
        val open = testEvent(occurredAt = 0L, endedAt = null)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertEquals(open, ongoingEventIn(case, listOf(closed, open)))
    }

    @Test
    fun `ongoingEventIn is null when a START_STOP case has no open event`() {
        val case = testCase(durationMode = DurationMode.START_STOP)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertNull(ongoingEventIn(case, listOf(closed)))
    }

    @Test
    fun `ongoingEventIn ignores a null endedAt on a NONE mode case`() {
        val case = testCase(durationMode = DurationMode.NONE)
        val event = testEvent(occurredAt = 0L, endedAt = null)

        assertNull(ongoingEventIn(case, listOf(event)))
    }

    @Test
    fun `ongoingEventIn ignores a null endedAt on a MANUAL mode case`() {
        val case = testCase(durationMode = DurationMode.MANUAL)
        val event = testEvent(occurredAt = 0L, endedAt = null)

        assertNull(ongoingEventIn(case, listOf(event)))
    }

    @Test
    fun `ongoingEventIn returns the earliest-started open event when several are running`() {
        val case = testCase(durationMode = DurationMode.START_STOP)
        val later = testEvent(id = 2L, occurredAt = 500L, endedAt = null)
        val earlier = testEvent(id = 1L, occurredAt = 100L, endedAt = null)

        assertEquals(earlier, ongoingEventIn(case, listOf(later, earlier)))
    }

    // --- ongoingEventsIn ---

    @Test
    fun `ongoingEventsIn returns every open event, earliest first`() {
        val case = testCase(durationMode = DurationMode.START_STOP)
        val third = testEvent(id = 3L, occurredAt = 900L, endedAt = null)
        val first = testEvent(id = 1L, occurredAt = 100L, endedAt = null)
        val second = testEvent(id = 2L, occurredAt = 400L, endedAt = null)
        val closed = testEvent(id = 4L, occurredAt = 200L, endedAt = 300L)

        assertEquals(listOf(first, second, third), ongoingEventsIn(case, listOf(third, closed, first, second)))
    }

    @Test
    fun `ongoingEventsIn is empty for a START_STOP case with no open events`() {
        val case = testCase(durationMode = DurationMode.START_STOP)
        val closed = testEvent(occurredAt = 100L, endedAt = 200L)

        assertTrue(ongoingEventsIn(case, listOf(closed)).isEmpty())
    }

    @Test
    fun `ongoingEventsIn is empty for a NONE mode case even with null endedAt events`() {
        val case = testCase(durationMode = DurationMode.NONE)
        val event = testEvent(occurredAt = 0L, endedAt = null)

        assertTrue(ongoingEventsIn(case, listOf(event)).isEmpty())
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

    // --- isStaleOngoing ---

    @Test
    fun `isStaleOngoing is false just under the threshold`() {
        val event = testEvent(occurredAt = 0L)

        assertFalse(isStaleOngoing(event, now = MILLIS_PER_DAY - 1))
    }

    @Test
    fun `isStaleOngoing is true at exactly the threshold with no prior dismissal`() {
        val event = testEvent(occurredAt = 0L)

        assertTrue(isStaleOngoing(event, now = MILLIS_PER_DAY))
    }

    @Test
    fun `isStaleOngoing stays suppressed just under a day after being dismissed`() {
        val event = testEvent(occurredAt = 0L).copy(staleNudgeDismissedAt = MILLIS_PER_DAY)

        assertFalse(isStaleOngoing(event, now = 2 * MILLIS_PER_DAY - 1))
    }

    @Test
    fun `isStaleOngoing re-arms exactly a day after being dismissed`() {
        val event = testEvent(occurredAt = 0L).copy(staleNudgeDismissedAt = MILLIS_PER_DAY)

        assertTrue(isStaleOngoing(event, now = 2 * MILLIS_PER_DAY))
    }

    private fun testCase(
        durationMode: DurationMode,
        logFlow: LogFlow = LogFlow.ONE_TAP,
    ) = CaseEntity(
        id = 1L,
        name = "Test Case",
        icon = "🐛",
        createdAt = 0L,
        logFlow = logFlow,
        durationMode = durationMode,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = false,
    )

    private fun testEvent(
        id: Long = 0L,
        occurredAt: Long = 0L,
        endedAt: Long? = null,
    ) = EventEntity(
        id = id,
        caseId = 1L,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = null,
        note = null,
        loggedAt = occurredAt,
    )
}
