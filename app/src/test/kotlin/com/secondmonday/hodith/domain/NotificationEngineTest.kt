package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun notification(
    kind: NotificationKind = NotificationKind.OFTEN,
    threshold: Int = 3,
    windowDays: Int? = 7,
    enabled: Boolean = true,
    armed: Boolean = true,
    lastFiredAt: Long? = null,
) = NotificationEntity(
    id = 1,
    caseId = 1,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    expectedPer = ExpectedPer.WEEK,
    metric = VerdictMetric.OCCURRENCE_COUNT,
    minIntensity = null,
    enabled = enabled,
    armed = armed,
    lastFiredAt = lastFiredAt,
)

private fun event(occurredAt: Long) = testEvent(occurredAt = occurredAt)

class NotificationEngineTest {
    // ---- evaluateNotification: the shared armed/fired state machine ----

    @Test
    fun `evaluateNotification fires and disarms when armed and condition met`() {
        val result = evaluateNotification(notification(armed = true), conditionMet = true, now = 100L)

        assertTrue(result.shouldFire)
        assertFalse(result.newArmed)
        assertEquals(100L, result.newLastFiredAt)
    }

    @Test
    fun `evaluateNotification does not refire while unarmed and condition is still met`() {
        val result = evaluateNotification(notification(armed = false, lastFiredAt = 50L), conditionMet = true, now = 100L)

        assertFalse(result.shouldFire)
        assertFalse(result.newArmed)
        assertEquals(50L, result.newLastFiredAt)
    }

    @Test
    fun `evaluateNotification re-arms without firing once the condition drops`() {
        val result = evaluateNotification(notification(armed = false, lastFiredAt = 50L), conditionMet = false, now = 100L)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
        assertEquals(50L, result.newLastFiredAt)
    }

    @Test
    fun `evaluateNotification stays armed and quiet while armed and condition is not met`() {
        val result = evaluateNotification(notification(armed = true), conditionMet = false, now = 100L)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
        assertNull(result.newLastFiredAt)
    }

    @Test
    fun `evaluateNotification never fires a disabled notification even when armed and condition met`() {
        val result = evaluateNotification(notification(enabled = false, armed = true), conditionMet = true, now = 100L)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
        assertNull(result.newLastFiredAt)
    }

    // ---- evaluateOften: rolling-window event count against threshold ----

    @Test
    fun `evaluateOften fires at exactly the threshold count within the window`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(9)) }

        val result = evaluateOften(notification(threshold = 3, windowDays = 7), events = events, now = now)

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateOften does not fire one event short of threshold`() {
        val now = millisAtDay(10)
        val events = List(2) { event(millisAtDay(9)) }

        val result = evaluateOften(notification(threshold = 3, windowDays = 7), events = events, now = now)

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateOften excludes events that have aged out of the rolling window`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(2)) }

        val result = evaluateOften(notification(threshold = 3, windowDays = 7), events = events, now = now)

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateOften re-arms once the window count ages back below threshold`() {
        val alreadyFired = notification(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(10))

        val result = evaluateOften(alreadyFired, events = emptyList(), now = millisAtDay(20))

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }

    @Test
    fun `evaluateOften re-arms when a previously-counted event is deleted, without waiting for the window to age`() {
        val now = millisAtDay(10)
        val alreadyFired = notification(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(9))
        val eventsAfterDeletion = List(2) { event(millisAtDay(9)) } // one of the original 3 events was deleted

        val result = evaluateOften(alreadyFired, events = eventsAfterDeletion, now = now)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }

    // ---- evaluateQuiet: gap since the latest of last event / case creation ----

    @Test
    fun `evaluateQuiet fires at exactly the threshold day gap since the last event`() {
        val quietNotification = notification(kind = NotificationKind.QUIET, threshold = 30, windowDays = null)

        val result =
            evaluateQuiet(
                quietNotification,
                mostRecentEventAt = millisAtDay(0),
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(30),
            )

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateQuiet does not fire one day short of the threshold gap`() {
        val quietNotification = notification(kind = NotificationKind.QUIET, threshold = 30, windowDays = null)

        val result =
            evaluateQuiet(
                quietNotification,
                mostRecentEventAt = millisAtDay(0),
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(29),
            )

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateQuiet falls back to case creation for a Case with no events yet`() {
        val quietNotification = notification(kind = NotificationKind.QUIET, threshold = 14, windowDays = null)

        val result =
            evaluateQuiet(
                quietNotification,
                mostRecentEventAt = null,
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(14),
            )

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateQuiet re-arms once a new event resets the gap to zero`() {
        val alreadyFired =
            notification(kind = NotificationKind.QUIET, threshold = 30, windowDays = null, armed = false, lastFiredAt = millisAtDay(30))

        val result =
            evaluateQuiet(
                alreadyFired,
                mostRecentEventAt = millisAtDay(31),
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(31),
            )

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }
}
