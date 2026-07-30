package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

private fun trigger(
    kind: TriggerKind = TriggerKind.AT_LEAST,
    threshold: Int = 3,
    windowDays: Int? = 7,
    enabled: Boolean = true,
    armed: Boolean = true,
    lastFiredAt: Long? = null,
) = TriggerEntity(
    id = 1,
    caseId = 1,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    enabled = enabled,
    armed = armed,
    lastFiredAt = lastFiredAt,
)

private fun event(occurredAt: Long) =
    EventEntity(id = 0, caseId = 1, occurredAt = occurredAt, endedAt = null, intensity = null, note = null, loggedAt = occurredAt)

class TriggerEngineTest {
    // ---- evaluateTrigger: the shared armed/fired state machine ----

    @Test
    fun `evaluateTrigger fires and disarms when armed and condition met`() {
        val result = evaluateTrigger(trigger(armed = true), conditionMet = true, now = 100L)

        assertTrue(result.shouldFire)
        assertFalse(result.newArmed)
        assertEquals(100L, result.newLastFiredAt)
    }

    @Test
    fun `evaluateTrigger does not refire while unarmed and condition is still met`() {
        val result = evaluateTrigger(trigger(armed = false, lastFiredAt = 50L), conditionMet = true, now = 100L)

        assertFalse(result.shouldFire)
        assertFalse(result.newArmed)
        assertEquals(50L, result.newLastFiredAt)
    }

    @Test
    fun `evaluateTrigger re-arms without firing once the condition drops`() {
        val result = evaluateTrigger(trigger(armed = false, lastFiredAt = 50L), conditionMet = false, now = 100L)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
        assertEquals(50L, result.newLastFiredAt)
    }

    @Test
    fun `evaluateTrigger stays armed and quiet while armed and condition is not met`() {
        val result = evaluateTrigger(trigger(armed = true), conditionMet = false, now = 100L)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
        assertNull(result.newLastFiredAt)
    }

    @Test
    fun `evaluateTrigger never fires a disabled trigger even when armed and condition met`() {
        val result = evaluateTrigger(trigger(enabled = false, armed = true), conditionMet = true, now = 100L)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
        assertNull(result.newLastFiredAt)
    }

    // ---- evaluateAtLeast: rolling-window event count against threshold ----

    @Test
    fun `evaluateAtLeast fires at exactly the threshold count within the window`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(9)) }

        val result = evaluateAtLeast(trigger(threshold = 3, windowDays = 7), events = events, now = now)

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateAtLeast does not fire one event short of threshold`() {
        val now = millisAtDay(10)
        val events = List(2) { event(millisAtDay(9)) }

        val result = evaluateAtLeast(trigger(threshold = 3, windowDays = 7), events = events, now = now)

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateAtLeast excludes events that have aged out of the rolling window`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(2)) }

        val result = evaluateAtLeast(trigger(threshold = 3, windowDays = 7), events = events, now = now)

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateAtLeast re-arms once the window count ages back below threshold`() {
        val alreadyFired = trigger(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(10))

        val result = evaluateAtLeast(alreadyFired, events = emptyList(), now = millisAtDay(20))

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }

    @Test
    fun `evaluateAtLeast re-arms when a previously-counted event is deleted, without waiting for the window to age`() {
        val now = millisAtDay(10)
        val alreadyFired = trigger(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(9))
        val eventsAfterDeletion = List(2) { event(millisAtDay(9)) } // one of the original 3 events was deleted

        val result = evaluateAtLeast(alreadyFired, events = eventsAfterDeletion, now = now)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }

    // ---- evaluateSilentFor: gap since the latest of last event / case creation ----

    @Test
    fun `evaluateSilentFor fires at exactly the threshold day gap since the last event`() {
        val silentTrigger = trigger(kind = TriggerKind.SILENT_FOR, threshold = 30, windowDays = null)

        val result =
            evaluateSilentFor(
                silentTrigger,
                mostRecentEventAt = millisAtDay(0),
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(30),
            )

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateSilentFor does not fire one day short of the threshold gap`() {
        val silentTrigger = trigger(kind = TriggerKind.SILENT_FOR, threshold = 30, windowDays = null)

        val result =
            evaluateSilentFor(
                silentTrigger,
                mostRecentEventAt = millisAtDay(0),
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(29),
            )

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateSilentFor falls back to case creation for a Case with no events yet`() {
        val silentTrigger = trigger(kind = TriggerKind.SILENT_FOR, threshold = 14, windowDays = null)

        val result =
            evaluateSilentFor(
                silentTrigger,
                mostRecentEventAt = null,
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(14),
            )

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateSilentFor re-arms once a new event resets the gap to zero`() {
        val alreadyFired =
            trigger(kind = TriggerKind.SILENT_FOR, threshold = 30, windowDays = null, armed = false, lastFiredAt = millisAtDay(30))

        val result =
            evaluateSilentFor(
                alreadyFired,
                mostRecentEventAt = millisAtDay(31),
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(31),
            )

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }
}
