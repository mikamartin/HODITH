package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

private const val DELTA = 0.0001

// hunch.createdAt isn't read by the engine (spec §8 keys the window off the Case's own
// createdAt, passed separately) — no test needs to vary it, so it's hardcoded rather than
// exposed as a parameter nothing uses.
private fun hunch(
    expectedCount: Int = 5,
    expectedPer: ExpectedPer = ExpectedPer.WEEK,
    direction: HunchDirection = HunchDirection.TOO_OFTEN,
    metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
    observationWindow: ObservationWindow = ObservationWindow.SINCE_START,
    windowStartDate: Long? = null,
) = HunchEntity(
    id = 1,
    caseId = 1,
    direction = direction,
    expectedCount = expectedCount,
    expectedPer = expectedPer,
    createdAt = 0L,
    resolvedAt = null,
    metric = metric,
    observationWindow = observationWindow,
    windowStartDate = windowStartDate,
)

private fun event(occurredAt: Long) = testEvent(occurredAt = occurredAt)

private fun eventsAt(
    count: Int,
    occurredAt: Long,
): List<EventEntity> = List(count) { event(occurredAt) }

class VerdictEngineTest {
    // ---- confidenceTierFor: both the observation-count and day-count bars must clear together ----

    @Test
    fun `confidenceTierFor is NO_VERDICT with zero events and zero days`() {
        assertEquals(ConfidenceTier.NO_VERDICT, confidenceTierFor(observationCount = 0, windowDays = 0))
    }

    @Test
    fun `confidenceTierFor is NO_VERDICT when event count is one short of Preliminary`() {
        assertEquals(ConfidenceTier.NO_VERDICT, confidenceTierFor(observationCount = 4, windowDays = 14))
    }

    @Test
    fun `confidenceTierFor is NO_VERDICT when window is one day short of Preliminary`() {
        assertEquals(ConfidenceTier.NO_VERDICT, confidenceTierFor(observationCount = 5, windowDays = 13))
    }

    @Test
    fun `confidenceTierFor is Preliminary at exactly the 5-event 14-day boundary`() {
        assertEquals(ConfidenceTier.PRELIMINARY, confidenceTierFor(observationCount = 5, windowDays = 14))
    }

    @Test
    fun `confidenceTierFor stays Preliminary when event count is one short of Confident`() {
        assertEquals(ConfidenceTier.PRELIMINARY, confidenceTierFor(observationCount = 14, windowDays = 28))
    }

    @Test
    fun `confidenceTierFor stays Preliminary when window is one day short of Confident`() {
        assertEquals(ConfidenceTier.PRELIMINARY, confidenceTierFor(observationCount = 15, windowDays = 27))
    }

    @Test
    fun `confidenceTierFor is Confident at exactly the 15-event 28-day boundary`() {
        assertEquals(ConfidenceTier.CONFIDENT, confidenceTierFor(observationCount = 15, windowDays = 28))
    }

    @Test
    fun `confidenceTierFor is Confident well past both bars`() {
        assertEquals(ConfidenceTier.CONFIDENT, confidenceTierFor(observationCount = 100, windowDays = 1000))
    }

    // ---- comparisonBandFor: every named cutoff, and the value just below it ----

    @Test
    fun `comparisonBandFor is MUCH_LESS well under the 0-5 cutoff`() {
        assertEquals(ComparisonBand.MUCH_LESS, comparisonBandFor(observedRate = 2.0, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is LESS at exactly the 0-5 cutoff`() {
        assertEquals(ComparisonBand.LESS, comparisonBandFor(observedRate = 5.0, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is MUCH_LESS just under the 0-5 cutoff`() {
        assertEquals(ComparisonBand.MUCH_LESS, comparisonBandFor(observedRate = 4.999, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is ABOUT_RIGHT at exactly the 0-8 cutoff`() {
        assertEquals(ComparisonBand.ABOUT_RIGHT, comparisonBandFor(observedRate = 8.0, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is LESS just under the 0-8 cutoff`() {
        assertEquals(ComparisonBand.LESS, comparisonBandFor(observedRate = 7.999, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is ABOUT_RIGHT for an exact match`() {
        assertEquals(ComparisonBand.ABOUT_RIGHT, comparisonBandFor(observedRate = 10.0, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is MORE at exactly the 1-25 cutoff`() {
        assertEquals(ComparisonBand.MORE, comparisonBandFor(observedRate = 12.5, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is ABOUT_RIGHT just under the 1-25 cutoff`() {
        assertEquals(ComparisonBand.ABOUT_RIGHT, comparisonBandFor(observedRate = 12.499, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is MUCH_MORE at exactly the 2-0 cutoff`() {
        assertEquals(ComparisonBand.MUCH_MORE, comparisonBandFor(observedRate = 20.0, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is MORE just under the 2-0 cutoff`() {
        assertEquals(ComparisonBand.MORE, comparisonBandFor(observedRate = 19.99, expectedRate = 10.0))
    }

    @Test
    fun `comparisonBandFor is MUCH_MORE well past the 2-0 cutoff`() {
        assertEquals(ComparisonBand.MUCH_MORE, comparisonBandFor(observedRate = 50.0, expectedRate = 10.0))
    }

    // ---- observedRateFor: normalizes a per-day rate up to the Hunch's own unit ----

    @Test
    fun `observedRateFor returns the per-day rate unchanged for DAY`() {
        assertEquals(1.0, observedRateFor(eventCount = 10, windowDays = 10, expectedPer = ExpectedPer.DAY), DELTA)
    }

    @Test
    fun `observedRateFor scales up to a weekly rate for WEEK`() {
        assertEquals(7.0, observedRateFor(eventCount = 14, windowDays = 14, expectedPer = ExpectedPer.WEEK), DELTA)
    }

    @Test
    fun `observedRateFor scales up to a monthly rate for MONTH`() {
        assertEquals(15.0, observedRateFor(eventCount = 15, windowDays = 30, expectedPer = ExpectedPer.MONTH), DELTA)
    }

    @Test
    fun `observedRateFor scales up to a quarterly rate for QUARTER`() {
        // 9 active days over a 90-day window, stated per 3 months, is exactly 9.
        assertEquals(9.0, observedRateFor(eventCount = 9, windowDays = 90, expectedPer = ExpectedPer.QUARTER), DELTA)
    }

    @Test
    fun `observedRateFor is zero when the window is zero days, regardless of event count`() {
        assertEquals(0.0, observedRateFor(eventCount = 5, windowDays = 0, expectedPer = ExpectedPer.DAY), DELTA)
        assertEquals(0.0, observedRateFor(eventCount = 5, windowDays = 0, expectedPer = ExpectedPer.WEEK), DELTA)
        assertEquals(0.0, observedRateFor(eventCount = 5, windowDays = 0, expectedPer = ExpectedPer.MONTH), DELTA)
    }

    @Test
    fun `observedRateFor is zero with zero events over a non-zero window`() {
        assertEquals(0.0, observedRateFor(eventCount = 0, windowDays = 30, expectedPer = ExpectedPer.WEEK), DELTA)
    }

    // ---- computeVerdict: the full pipeline, window + tier + rate + band together ----

    @Test
    fun `computeVerdict reports NO_VERDICT and a null band for a brand new case with no events`() {
        val caseCreatedAt = millisAtDay(0)
        val now = millisAtDay(10)

        val result = computeVerdict(hunch(), emptyList(), caseCreatedAt, now, DurationMode.NONE)

        assertEquals(ConfidenceTier.NO_VERDICT, result.tier)
        assertEquals(0, result.eventCount)
        assertEquals(10L, result.windowDays)
        assertEquals(0.0, result.observedRate, DELTA)
        assertNull(result.comparisonBand)
    }

    @Test
    fun `computeVerdict starts the window at a retro-logged event earlier than the case's own creation`() {
        val caseCreatedAt = millisAtDay(30)
        val retroEvent = event(millisAtDay(0))
        val now = millisAtDay(40)

        val result = computeVerdict(hunch(), listOf(retroEvent), caseCreatedAt, now, DurationMode.NONE)

        // Window starts at day 0 (the retro-log), not day 30 (case creation) — 40 days, not 10.
        assertEquals(40L, result.windowDays)
    }

    @Test
    fun `computeVerdict treats a brand-new case whose only events just fired as a zero-day window`() {
        val now = millisAtDay(100)
        val events = eventsAt(count = 6, occurredAt = now)

        val result = computeVerdict(hunch(), events, caseCreatedAt = now, now = now, DurationMode.NONE)

        assertEquals(0L, result.windowDays)
        assertEquals(6, result.eventCount)
        // 6 events clears the Preliminary event bar, but a same-instant case still fails the day bar.
        assertEquals(ConfidenceTier.NO_VERDICT, result.tier)
        assertEquals(0.0, result.observedRate, DELTA)
        assertNull(result.comparisonBand)
    }

    @Test
    fun `computeVerdict reproduces the spec's Confident sample, 15 events over 50 days at 5-per-week TOO_OFTEN`() {
        val caseCreatedAt = millisAtDay(0)
        val now = millisAtDay(50)
        val events = eventsAt(count = 15, occurredAt = millisAtDay(25))
        val theHunch = hunch(expectedCount = 5, expectedPer = ExpectedPer.WEEK, direction = HunchDirection.TOO_OFTEN)

        val result = computeVerdict(theHunch, events, caseCreatedAt, now, DurationMode.NONE)

        assertEquals(ConfidenceTier.CONFIDENT, result.tier)
        assertEquals(2.1, result.observedRate, DELTA)
        assertEquals(5.0, result.expectedRate, DELTA)
        assertEquals(ComparisonBand.MUCH_LESS, result.comparisonBand)
    }

    @Test
    fun `computeVerdict at a DAY expectedPer wires the per-day rate straight through`() {
        // 14 events over 14 days also happens to be the exact Preliminary boundary, so this
        // doubles as confirmation that a comparison band is actually produced (non-null) there.
        val caseCreatedAt = millisAtDay(0)
        val now = millisAtDay(14)
        val events = eventsAt(count = 14, occurredAt = millisAtDay(7))
        val theHunch = hunch(expectedCount = 1, expectedPer = ExpectedPer.DAY)

        val result = computeVerdict(theHunch, events, caseCreatedAt, now, DurationMode.NONE)

        assertEquals(ConfidenceTier.PRELIMINARY, result.tier)
        assertEquals(1.0, result.observedRate, DELTA)
        assertEquals(ComparisonBand.ABOUT_RIGHT, result.comparisonBand)
    }

    @Test
    fun `computeVerdict window-day math is unaffected by a spring-forward DST transition`() {
        val originalDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            // 2026-03-08 is America/New_York's spring-forward day, inside this 14-day window;
            // a raw-millis ÷ 86_400_000 computation would undercount by the missing hour.
            val zone = ZoneId.systemDefault()
            val caseCreatedAt =
                LocalDate
                    .of(2026, 3, 1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val now =
                LocalDate
                    .of(2026, 3, 15)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

            val result = computeVerdict(hunch(), emptyList(), caseCreatedAt, now, DurationMode.NONE)

            assertEquals(14L, result.windowDays)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    // ---- days-active metric ----

    @Test
    fun `computeVerdict days-active metric is driven by distinct active days, not raw event count, for overlapping spans`() {
        // The "recurring fights" scenario: 3 START_STOP events, each 8 days, overlapping, spread
        // across a 30-day window. Days 0-14 and 20-28 are all active — 24 of 30 days.
        val events =
            listOf(
                testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(8)),
                testEvent(occurredAt = millisAtDay(6), endedAt = millisAtDay(14)),
                testEvent(occurredAt = millisAtDay(20), endedAt = millisAtDay(28)),
            )
        val now = millisAtDay(30)
        val theHunch = hunch(expectedCount = 4, expectedPer = ExpectedPer.MONTH, metric = VerdictMetric.DAYS_ACTIVE)

        val result = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = now, DurationMode.START_STOP)

        assertEquals(3, result.eventCount)
        assertEquals(24, result.activeDayCount)
        // 24 active days over 30 days, stated monthly, is 24 — six times the "3 a month" the raw
        // occurrence count would report.
        assertEquals(24.0, result.observedRate, DELTA)
        assertEquals(VerdictMetric.DAYS_ACTIVE, result.metric)
    }

    @Test
    fun `computeVerdict metric parity - both metrics agree when nothing overlaps`() {
        val events =
            listOf(
                testEvent(occurredAt = millisAtDay(1)),
                testEvent(occurredAt = millisAtDay(10)),
                testEvent(occurredAt = millisAtDay(20)),
            )
        val now = millisAtDay(30)
        val occurrence =
            computeVerdict(
                hunch(expectedPer = ExpectedPer.MONTH, metric = VerdictMetric.OCCURRENCE_COUNT),
                events,
                caseCreatedAt = millisAtDay(0),
                now = now,
                DurationMode.MANUAL,
            )
        val daysActive =
            computeVerdict(
                hunch(expectedPer = ExpectedPer.MONTH, metric = VerdictMetric.DAYS_ACTIVE),
                events,
                caseCreatedAt = millisAtDay(0),
                now = now,
                DurationMode.MANUAL,
            )

        assertEquals(3, occurrence.eventCount)
        assertEquals(3, daysActive.activeDayCount)
        assertEquals(occurrence.observedRate, daysActive.observedRate, DELTA)
    }

    @Test
    fun `computeVerdict days-active - two same-day point events read as one active day`() {
        val events =
            listOf(
                testEvent(occurredAt = millisAtDay(5)),
                testEvent(occurredAt = millisAtDay(5)),
            )
        val result =
            computeVerdict(
                hunch(metric = VerdictMetric.DAYS_ACTIVE),
                events,
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(30),
                DurationMode.MANUAL,
            )

        assertEquals(2, result.eventCount)
        assertEquals(1, result.activeDayCount)
    }

    @Test
    fun `computeVerdict days-active - a single long event clears the Preliminary bar alone`() {
        // PROGRESS.md's accepted tradeoff: one 10-day event, 20 days after creation, reads Preliminary.
        val events = listOf(testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(10)))
        val result =
            computeVerdict(
                hunch(expectedCount = 2, expectedPer = ExpectedPer.MONTH, metric = VerdictMetric.DAYS_ACTIVE),
                events,
                caseCreatedAt = millisAtDay(0),
                now = millisAtDay(20),
                DurationMode.MANUAL,
            )

        assertEquals(1, result.eventCount)
        assertEquals(11, result.activeDayCount)
        assertEquals(ConfidenceTier.PRELIMINARY, result.tier)
    }

    // ---- bounded observation windows ----

    @Test
    fun `computeVerdict rolling window excludes events before the 90-day start`() {
        val events = listOf(event(millisAtDay(0)), event(millisAtDay(100)), event(millisAtDay(150)))
        val theHunch = hunch(observationWindow = ObservationWindow.LAST_3_MONTHS)

        val result = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(160), DurationMode.NONE)

        // Window is ~day 70..160: the day-0 event falls outside it. windowDays is a fixed 90-day
        // millis subtraction, so it can land a day either side of 90 across a DST transition —
        // the same approximation DAYS_PER_MONTH already makes.
        assertEquals(2, result.eventCount)
        assertTrue(result.windowDays in 89L..91L)
    }

    @Test
    fun `computeVerdict rolling window result changes as now advances over the same event set`() {
        val events =
            listOf(event(millisAtDay(10)), event(millisAtDay(20)), event(millisAtDay(30)), event(millisAtDay(130)), event(millisAtDay(140)))
        val theHunch = hunch(observationWindow = ObservationWindow.LAST_3_MONTHS)

        val early = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(50), DurationMode.NONE)
        val late = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(150), DurationMode.NONE)

        // Early: window floored at creation (day 0..50) catches the first three; late: window
        // day 60..150 catches only the last two.
        assertEquals(3, early.eventCount)
        assertEquals(2, late.eventCount)
    }

    @Test
    fun `computeVerdict custom window excludes events before the picked start`() {
        val events = listOf(event(millisAtDay(5)), event(millisAtDay(50)), event(millisAtDay(90)))
        val theHunch = hunch(observationWindow = ObservationWindow.CUSTOM, windowStartDate = millisAtDay(40))

        val result = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(100), DurationMode.NONE)

        assertEquals(2, result.eventCount)
        assertEquals(60L, result.windowDays)
    }

    @Test
    fun `computeVerdict custom window start is floored at the case's creation`() {
        val events = listOf(event(millisAtDay(5)), event(millisAtDay(50)))
        val theHunch = hunch(observationWindow = ObservationWindow.CUSTOM, windowStartDate = millisAtDay(-30))

        val result = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(100), DurationMode.NONE)

        // A start before the case existed degrades to "since the start", not a negative window.
        assertEquals(2, result.eventCount)
        assertEquals(100L, result.windowDays)
    }

    @Test
    fun `computeVerdict counts a duration event whose span starts before the window but reaches into it`() {
        // Span day 10..60; custom window opens day 40. occurredAt alone would exclude it.
        val events = listOf(testEvent(occurredAt = millisAtDay(10), endedAt = millisAtDay(60)))
        val theHunch =
            hunch(observationWindow = ObservationWindow.CUSTOM, windowStartDate = millisAtDay(40), metric = VerdictMetric.DAYS_ACTIVE)

        val result = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(100), DurationMode.MANUAL)

        assertEquals(1, result.eventCount)
        // Only the in-window days count: day 40 through day 60 inclusive is 21 days.
        assertEquals(21, result.activeDayCount)
    }

    @Test
    fun `computeVerdict since-the-start mode is unchanged from all-time behaviour`() {
        val events = eventsAt(count = 15, occurredAt = millisAtDay(25))
        val theHunch = hunch(expectedCount = 5, expectedPer = ExpectedPer.WEEK, observationWindow = ObservationWindow.SINCE_START)

        val result = computeVerdict(theHunch, events, caseCreatedAt = millisAtDay(0), now = millisAtDay(50), DurationMode.NONE)

        assertEquals(15, result.eventCount)
        assertEquals(50L, result.windowDays)
        assertEquals(2.1, result.observedRate, DELTA)
        assertEquals(ComparisonBand.MUCH_LESS, result.comparisonBand)
    }
}
