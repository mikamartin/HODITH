package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
) = HunchEntity(
    id = 1,
    caseId = 1,
    direction = direction,
    expectedCount = expectedCount,
    expectedPer = expectedPer,
    createdAt = 0L,
    resolvedAt = null,
)

private fun event(occurredAt: Long) = testEvent(occurredAt = occurredAt)

private fun eventsAt(
    count: Int,
    occurredAt: Long,
): List<EventEntity> = List(count) { event(occurredAt) }

class VerdictEngineTest {
    // ---- confidenceTierFor: both the event-count and day-count bars must clear together ----

    @Test
    fun `confidenceTierFor is NO_VERDICT with zero events and zero days`() {
        assertEquals(ConfidenceTier.NO_VERDICT, confidenceTierFor(eventCount = 0, windowDays = 0))
    }

    @Test
    fun `confidenceTierFor is NO_VERDICT when event count is one short of Preliminary`() {
        assertEquals(ConfidenceTier.NO_VERDICT, confidenceTierFor(eventCount = 4, windowDays = 14))
    }

    @Test
    fun `confidenceTierFor is NO_VERDICT when window is one day short of Preliminary`() {
        assertEquals(ConfidenceTier.NO_VERDICT, confidenceTierFor(eventCount = 5, windowDays = 13))
    }

    @Test
    fun `confidenceTierFor is Preliminary at exactly the 5-event 14-day boundary`() {
        assertEquals(ConfidenceTier.PRELIMINARY, confidenceTierFor(eventCount = 5, windowDays = 14))
    }

    @Test
    fun `confidenceTierFor stays Preliminary when event count is one short of Confident`() {
        assertEquals(ConfidenceTier.PRELIMINARY, confidenceTierFor(eventCount = 14, windowDays = 28))
    }

    @Test
    fun `confidenceTierFor stays Preliminary when window is one day short of Confident`() {
        assertEquals(ConfidenceTier.PRELIMINARY, confidenceTierFor(eventCount = 15, windowDays = 27))
    }

    @Test
    fun `confidenceTierFor is Confident at exactly the 15-event 28-day boundary`() {
        assertEquals(ConfidenceTier.CONFIDENT, confidenceTierFor(eventCount = 15, windowDays = 28))
    }

    @Test
    fun `confidenceTierFor is Confident well past both bars`() {
        assertEquals(ConfidenceTier.CONFIDENT, confidenceTierFor(eventCount = 100, windowDays = 1000))
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

        val result = computeVerdict(hunch(), events = emptyList(), caseCreatedAt = caseCreatedAt, now = now)

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

        val result = computeVerdict(hunch(), events = listOf(retroEvent), caseCreatedAt = caseCreatedAt, now = now)

        // Window starts at day 0 (the retro-log), not day 30 (case creation) — 40 days, not 10.
        assertEquals(40L, result.windowDays)
    }

    @Test
    fun `computeVerdict treats a brand-new case whose only events just fired as a zero-day window`() {
        val now = millisAtDay(100)
        val events = eventsAt(count = 6, occurredAt = now)

        val result = computeVerdict(hunch(), events = events, caseCreatedAt = now, now = now)

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

        val result = computeVerdict(theHunch, events = events, caseCreatedAt = caseCreatedAt, now = now)

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

        val result = computeVerdict(theHunch, events = events, caseCreatedAt = caseCreatedAt, now = now)

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

            val result = computeVerdict(hunch(), events = emptyList(), caseCreatedAt = caseCreatedAt, now = now)

            assertEquals(14L, result.windowDays)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
