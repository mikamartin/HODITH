package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.testsupport.TEST_ZONE
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Epoch millis at local midnight on an explicit calendar date, safely inside a single DST regime
 * (no spring-forward/fall-back transition anywhere near it) — unlike [millisAtDay]'s 1970 epoch
 * days, which happen to straddle a DST boundary around day ~110-115. Used only where a test spans
 * a wide enough range (e.g. a 90-day lookback) that an epoch-day pick could otherwise cross one.
 */
private fun millisOnDate(
    year: Int,
    month: Int,
    day: Int,
) = LocalDate
    .of(year, month, day)
    .atStartOfDay(TEST_ZONE)
    .toInstant()
    .toEpochMilli()

private fun notification(
    kind: NotificationKind = NotificationKind.OFTEN,
    threshold: Int = 3,
    windowDays: Int? = 7,
    expectedPer: ExpectedPer = ExpectedPer.WEEK,
    metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
    minIntensity: Int? = null,
    enabled: Boolean = true,
    armed: Boolean = true,
    lastFiredAt: Long? = null,
) = NotificationEntity(
    id = 1,
    caseId = 1,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    expectedPer = expectedPer,
    metric = metric,
    minIntensity = minIntensity,
    enabled = enabled,
    armed = armed,
    lastFiredAt = lastFiredAt,
)

private fun event(
    occurredAt: Long,
    intensity: Int? = null,
) = testEvent(occurredAt = occurredAt, intensity = intensity)

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

    // ---- evaluateOften: honest observed-vs-expected rate comparison (spec §8/§11) ----
    //
    // evaluateOften delegates its window/rate math entirely to computeVerdict (VerdictEngineTest
    // covers that pipeline exhaustively); these tests exercise evaluateOften's own contribution —
    // wiring threshold/expectedPer/metric/minIntensity into an Expectation, picking
    // observedRate >= expectedRate as the firing comparison, and feeding that into the shared
    // armed/fired state machine — not every corner of computeVerdict itself.

    @Test
    fun `evaluateOften fires at exactly the threshold rate within the window`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(9)) }

        val result =
            evaluateOften(notification(threshold = 3, windowDays = 7), allEvents = events, now = now, durationMode = DurationMode.NONE)

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateOften does not fire one event short of the threshold rate`() {
        val now = millisAtDay(10)
        val events = List(2) { event(millisAtDay(9)) }

        val result =
            evaluateOften(notification(threshold = 3, windowDays = 7), allEvents = events, now = now, durationMode = DurationMode.NONE)

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateOften excludes events that have aged out of the rolling window`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(2)) }

        val result =
            evaluateOften(notification(threshold = 3, windowDays = 7), allEvents = events, now = now, durationMode = DurationMode.NONE)

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateOften re-arms once the rate ages back below threshold`() {
        val alreadyFired = notification(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(10))

        val result = evaluateOften(alreadyFired, allEvents = emptyList(), now = millisAtDay(20), durationMode = DurationMode.NONE)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }

    @Test
    fun `evaluateOften re-arms when a previously-counted event is deleted, without waiting for the window to age`() {
        val now = millisAtDay(10)
        val alreadyFired = notification(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(9))
        val eventsAfterDeletion = List(2) { event(millisAtDay(9)) } // one of the original 3 events was deleted

        val result = evaluateOften(alreadyFired, allEvents = eventsAfterDeletion, now = now, durationMode = DurationMode.NONE)

        assertFalse(result.shouldFire)
        assertTrue(result.newArmed)
    }

    @Test
    fun `evaluateOften never fires when disabled, even if the rate clears the threshold`() {
        val now = millisAtDay(10)
        val events = List(5) { event(millisAtDay(9)) }

        val result =
            evaluateOften(
                notification(threshold = 1, windowDays = 7, enabled = false),
                allEvents = events,
                now = now,
                durationMode = DurationMode.NONE,
            )

        assertFalse(result.shouldFire)
    }

    // ---- evaluateOften: full armed/fired sequence, driven by the normalized rate ----

    @Test
    fun `evaluateOften fires and disarms when armed and the rate clears the threshold`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(9)) }

        val result =
            evaluateOften(
                notification(threshold = 3, windowDays = 7, armed = true),
                allEvents = events,
                now = now,
                durationMode = DurationMode.NONE,
            )

        assertTrue(result.shouldFire)
        assertFalse(result.newArmed)
        assertEquals(now, result.newLastFiredAt)
    }

    @Test
    fun `evaluateOften does not refire while disarmed and the rate is still at or above threshold`() {
        val now = millisAtDay(10)
        val events = List(3) { event(millisAtDay(9)) }
        val alreadyFired = notification(threshold = 3, windowDays = 7, armed = false, lastFiredAt = millisAtDay(9))

        val result = evaluateOften(alreadyFired, allEvents = events, now = now, durationMode = DurationMode.NONE)

        assertFalse(result.shouldFire)
        assertFalse(result.newArmed)
        assertEquals(millisAtDay(9), result.newLastFiredAt)
    }

    // ---- evaluateOften: expectedPer scales the normalized rate, not just a raw count ----

    @Test
    fun `evaluateOften threshold comparison uses the DAY-normalized rate`() {
        val now = millisAtDay(10)

        fun fires(eventCount: Int) =
            evaluateOften(
                notification(threshold = 1, windowDays = 7, expectedPer = ExpectedPer.DAY),
                allEvents = List(eventCount) { event(millisAtDay(9)) },
                now = now,
                durationMode = DurationMode.NONE,
            ).shouldFire

        assertFalse(fires(6)) // 6/7 ≈ 0.857 per day, short of 1/day
        assertTrue(fires(7)) // 7/7 = 1.0 per day, exactly at threshold
        assertTrue(fires(8)) // 8/7 ≈ 1.14 per day, above threshold
    }

    @Test
    fun `evaluateOften threshold comparison uses the WEEK-normalized rate`() {
        val now = millisAtDay(10)

        fun fires(eventCount: Int) =
            evaluateOften(
                notification(threshold = 7, windowDays = 7, expectedPer = ExpectedPer.WEEK),
                allEvents = List(eventCount) { event(millisAtDay(9)) },
                now = now,
                durationMode = DurationMode.NONE,
            ).shouldFire

        assertFalse(fires(6)) // (6/7)*7 = 6 per week, short of 7/week
        assertTrue(fires(7)) // (7/7)*7 = 7 per week, exactly at threshold
        assertTrue(fires(8)) // (8/7)*7 ≈ 8 per week, above threshold
    }

    @Test
    fun `evaluateOften threshold comparison uses the MONTH-normalized rate`() {
        val now = millisAtDay(10)

        fun fires(eventCount: Int) =
            evaluateOften(
                notification(threshold = 30, windowDays = 7, expectedPer = ExpectedPer.MONTH),
                allEvents = List(eventCount) { event(millisAtDay(9)) },
                now = now,
                durationMode = DurationMode.NONE,
            ).shouldFire

        assertFalse(fires(6)) // (6/7)*30 ≈ 25.7 per month, short of 30/month
        assertTrue(fires(7)) // (7/7)*30 = 30 per month, exactly at threshold
        assertTrue(fires(8)) // (8/7)*30 ≈ 34.3 per month, above threshold
    }

    @Test
    fun `evaluateOften threshold comparison uses the QUARTER-normalized rate`() {
        val now = millisAtDay(10)

        fun fires(eventCount: Int) =
            evaluateOften(
                notification(threshold = 90, windowDays = 7, expectedPer = ExpectedPer.QUARTER),
                allEvents = List(eventCount) { event(millisAtDay(9)) },
                now = now,
                durationMode = DurationMode.NONE,
            ).shouldFire

        assertFalse(fires(6)) // (6/7)*90 ≈ 77.1 per quarter, short of 90/quarter
        assertTrue(fires(7)) // (7/7)*90 = 90 per quarter, exactly at threshold
        assertTrue(fires(8)) // (8/7)*90 ≈ 102.9 per quarter, above threshold
    }

    // ---- evaluateOften: lookback window length (7/30/90-day presets and a custom value) ----

    @Test
    fun `evaluateOften normalizes the rate correctly across the 7-, 30-, and 90-day lookback presets`() {
        // A mid-year anchor: its 90-day lookback lands after the spring-forward transition and
        // well before the autumn fall-back, so none of the 7/30/90-day windows below straddle a
        // DST change (oftenWindowStart is a raw-millis subtraction, so a straddled window can be
        // off by a day — see VerdictEngineTest's own DST test for the same caveat).
        val now = millisOnDate(2026, 7, 15)
        val eventDay = now - MILLIS_PER_DAY

        listOf(7, 30, 90).forEach { windowDays ->
            fun fires(eventCount: Int) =
                evaluateOften(
                    notification(threshold = 1, windowDays = windowDays, expectedPer = ExpectedPer.DAY),
                    allEvents = List(eventCount) { event(eventDay) },
                    now = now,
                    durationMode = DurationMode.NONE,
                ).shouldFire

            assertFalse("windowDays=$windowDays one event short", fires(windowDays - 1))
            assertTrue("windowDays=$windowDays exactly at threshold", fires(windowDays))
        }
    }

    @Test
    fun `evaluateOften custom lookback compares the normalized rate, not a raw in-window count, against threshold`() {
        // 4 events inside a custom 10-day window would clear a naive "raw count >= 3" check, but
        // normalized to a weekly rate ((4/10)*7 = 2.8) it falls short of "3 per week" — the exact
        // threshold=3/week-over-10-days scenario the naive count-only comparison got wrong.
        val now = millisAtDay(20)
        val events = List(4) { event(millisAtDay(15)) }

        val result =
            evaluateOften(
                notification(threshold = 3, windowDays = 10, expectedPer = ExpectedPer.WEEK),
                allEvents = events,
                now = now,
                durationMode = DurationMode.NONE,
            )

        assertFalse(result.shouldFire)
    }

    // ---- evaluateOften: VerdictMetric.OCCURRENCE_COUNT vs DAYS_ACTIVE ----

    @Test
    fun `evaluateOften DAYS_ACTIVE metric counts distinct active days, not raw event count`() {
        // The "recurring fights" scenario (mirrors VerdictEngineTest): 3 overlapping START_STOP
        // events span 24 distinct days out of a 30-day window, but only 3 raw events.
        val now = millisAtDay(30)
        val events =
            listOf(
                testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(8)),
                testEvent(occurredAt = millisAtDay(6), endedAt = millisAtDay(14)),
                testEvent(occurredAt = millisAtDay(20), endedAt = millisAtDay(28)),
            )

        val occurrenceResult =
            evaluateOften(
                notification(threshold = 4, windowDays = 30, expectedPer = ExpectedPer.MONTH, metric = VerdictMetric.OCCURRENCE_COUNT),
                allEvents = events,
                now = now,
                durationMode = DurationMode.START_STOP,
            )
        val daysActiveResult =
            evaluateOften(
                notification(threshold = 4, windowDays = 30, expectedPer = ExpectedPer.MONTH, metric = VerdictMetric.DAYS_ACTIVE),
                allEvents = events,
                now = now,
                durationMode = DurationMode.START_STOP,
            )

        assertFalse(occurrenceResult.shouldFire) // 3 raw events per month, short of 4/month
        assertTrue(daysActiveResult.shouldFire) // 24 active days per month, well past 4/month
    }

    // ---- evaluateOften: minIntensity filter ----

    @Test
    fun `evaluateOften intensity filter excludes events with no recorded intensity once a minimum is set`() {
        val now = millisAtDay(10)
        val events = listOf(event(millisAtDay(9), intensity = null), event(millisAtDay(9), intensity = null))

        val result =
            evaluateOften(
                notification(threshold = 1, windowDays = 7, minIntensity = 3),
                allEvents = events,
                now = now,
                durationMode = DurationMode.NONE,
            )

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateOften intensity filter excludes an event below the minimum`() {
        val now = millisAtDay(10)
        val events = listOf(event(millisAtDay(9), intensity = 2))

        val result =
            evaluateOften(
                notification(threshold = 1, windowDays = 7, minIntensity = 3),
                allEvents = events,
                now = now,
                durationMode = DurationMode.NONE,
            )

        assertFalse(result.shouldFire)
    }

    @Test
    fun `evaluateOften intensity filter includes an event at exactly the minimum`() {
        val now = millisAtDay(10)
        val events = listOf(event(millisAtDay(9), intensity = 3))

        val result =
            evaluateOften(
                notification(threshold = 1, windowDays = 7, minIntensity = 3),
                allEvents = events,
                now = now,
                durationMode = DurationMode.NONE,
            )

        assertTrue(result.shouldFire)
    }

    // ---- evaluateOften: span-overlap, not occurredAt-only, windowing ----

    @Test
    fun `evaluateOften counts a duration event that started before the lookback window but is still active inside it`() {
        // Old occurredAt-only windowing would exclude this: it started day 0, well before the
        // 7-day lookback opens at day 23. Its span reaches to day 40 — active inside the window —
        // so honest span-overlap counting must include it.
        val now = millisAtDay(30)
        val stillActiveDuringWindow = testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(40))

        val result =
            evaluateOften(
                notification(threshold = 1, windowDays = 7),
                allEvents = listOf(stillActiveDuringWindow),
                now = now,
                durationMode = DurationMode.MANUAL,
            )

        assertTrue(result.shouldFire)
    }

    @Test
    fun `evaluateOften excludes a duration event that ended before the lookback window opened`() {
        val now = millisAtDay(30)
        val endedBeforeWindow = testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(10))

        val result =
            evaluateOften(
                notification(threshold = 1, windowDays = 7),
                allEvents = listOf(endedBeforeWindow),
                now = now,
                durationMode = DurationMode.MANUAL,
            )

        assertFalse(result.shouldFire)
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
