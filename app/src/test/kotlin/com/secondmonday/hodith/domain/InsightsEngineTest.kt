package com.secondmonday.hodith.domain

import com.secondmonday.hodith.testsupport.durationEvent
import com.secondmonday.hodith.testsupport.eventAtDay
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class InsightsEngineTest {
    // ---- computeGapStats ----

    @Test
    fun `computeGapStats reports the current gap from the last event to now`() {
        val events = listOf(eventAtDay(0), eventAtDay(5))

        val result = computeGapStats(events, now = millisAtDay(9))

        assertEquals(4L, result.currentGapDays)
    }

    @Test
    fun `computeGapStats flags the current gap as the longest when it exceeds every past gap`() {
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(4))

        val result = computeGapStats(events, now = millisAtDay(15))

        assertEquals(11L, result.currentGapDays)
        assertEquals(11L, result.longestGapDays)
        assertTrue(result.isCurrentGapLongest)
    }

    @Test
    fun `computeGapStats does not flag the current gap as longest when a past gap was bigger`() {
        val events = listOf(eventAtDay(0), eventAtDay(20), eventAtDay(22))

        val result = computeGapStats(events, now = millisAtDay(25))

        assertEquals(3L, result.currentGapDays)
        assertEquals(20L, result.longestGapDays)
        assertTrue(!result.isCurrentGapLongest)
    }

    @Test
    fun `computeGapStats flags the current gap as longest when it exactly ties the biggest past gap`() {
        val events = listOf(eventAtDay(0), eventAtDay(5), eventAtDay(10))

        val result = computeGapStats(events, now = millisAtDay(15))

        assertEquals(5L, result.currentGapDays)
        assertEquals(5L, result.longestGapDays)
        assertTrue(result.isCurrentGapLongest)
    }

    @Test
    fun `computeGapStats averages the past gaps, excluding the current in-progress one`() {
        // Past gaps: 2, 4, 6 days (average 4); current gap (day 12 to now, day 20) must not count.
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(6), eventAtDay(12))

        val result = computeGapStats(events, now = millisAtDay(20))

        assertEquals(4.0, result.averageGapDays, 0.0001)
    }

    @Test
    fun `computeGapStats reports zero average gap with fewer than two events`() {
        val result = computeGapStats(listOf(eventAtDay(0)), now = millisAtDay(5))

        assertEquals(0.0, result.averageGapDays, 0.0001)
    }

    @Test
    fun `computeGapStats reports a zero current gap while an event is running`() {
        val events = listOf(eventAtDay(0), eventAtDay(5))

        val result = computeGapStats(events, now = millisAtDay(12), eventActiveNow = true)

        assertEquals(0L, result.currentGapDays)
    }

    @Test
    fun `computeGapStats keeps the active stretch out of the longest gap`() {
        // Past gaps: 2, 2 days. Without the flag, 30 days to "now" would be the longest.
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(4))

        val result = computeGapStats(events, now = millisAtDay(30), eventActiveNow = true)

        assertEquals(0L, result.currentGapDays)
        assertEquals(2L, result.longestGapDays)
        assertTrue(!result.isCurrentGapLongest)
    }

    @Test
    fun `computeGapStats still surfaces a bigger past gap as the longest while an event runs`() {
        val events = listOf(eventAtDay(0), eventAtDay(20), eventAtDay(22))

        val result = computeGapStats(events, now = millisAtDay(40), eventActiveNow = true)

        assertEquals(0L, result.currentGapDays)
        assertEquals(20L, result.longestGapDays)
        assertTrue(!result.isCurrentGapLongest)
    }

    @Test
    fun `computeGapStats leaves past gaps and average untouched by the active-now flag`() {
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(6), eventAtDay(12))

        val active = computeGapStats(events, now = millisAtDay(20), eventActiveNow = true)
        val idle = computeGapStats(events, now = millisAtDay(20), eventActiveNow = false)

        assertEquals(idle.pastGaps, active.pastGaps)
        assertEquals(idle.averageGapDays, active.averageGapDays, 0.0001)
        assertEquals(idle.isBursty, active.isBursty)
    }

    @Test
    fun `computeGapStats treats no event as running by default`() {
        val events = listOf(eventAtDay(0), eventAtDay(5))

        val result = computeGapStats(events, now = millisAtDay(12))

        assertEquals(7L, result.currentGapDays)
    }

    @Test
    fun `computeGapStats measures the current gap from a finished duration event's end, not its start`() {
        // One event ran days 0..6 and stopped; "now" is day 6 — no silence yet, despite a day-6 start-to-now span.
        val events = listOf(eventAtDay(0), durationEvent(startDay = 1, endDay = 6))

        val result = computeGapStats(events, now = millisAtDay(6))

        assertEquals(0L, result.currentGapDays)
    }

    @Test
    fun `computeGapStats measures a past gap from an event's end to the next event's start`() {
        // Event A ran days 0..5; event B started day 8. The gap between them is 3 days, not 8.
        val events = listOf(durationEvent(startDay = 0, endDay = 5), eventAtDay(8))

        val result = computeGapStats(events, now = millisAtDay(10))

        assertEquals(listOf(3L), result.pastGaps)
    }

    @Test
    fun `computeGapStats floors an overlapping span's past gap at zero`() {
        // Event A ran days 0..10; event B started day 4, while A was still going.
        val events = listOf(durationEvent(startDay = 0, endDay = 10), eventAtDay(4))

        val result = computeGapStats(events, now = millisAtDay(12))

        assertEquals(listOf(0L), result.pastGaps)
    }

    @Test
    fun `computeGapStats takes the current gap from the latest end even when another event started later`() {
        // A: days 0..10. B: started day 4, ended day 6. Latest end is day 10, so the gap runs from there.
        val events = listOf(durationEvent(startDay = 0, endDay = 10), durationEvent(startDay = 4, endDay = 6))

        val result = computeGapStats(events, now = millisAtDay(13))

        assertEquals(3L, result.currentGapDays)
    }

    @Test
    fun `computeGapStats measures a past gap from the furthest end reached, not the previous event's`() {
        // A ran days 0..20; B was a shorter overlapping run (days 5..6) inside it; C started day 25.
        // The silence before C is 5 days (25 minus A's end), not 19 (25 minus B's end) — A was still
        // running when B stopped, so there was no real gap between B and C.
        val events =
            listOf(
                durationEvent(startDay = 0, endDay = 20),
                durationEvent(startDay = 5, endDay = 6),
                durationEvent(startDay = 25, endDay = 26),
            )

        val result = computeGapStats(events, now = millisAtDay(30))

        assertEquals(listOf(0L, 5L), result.pastGaps)
    }

    @Test
    fun `computeGapStats floors a reversed endedAt to the event's own start`() {
        // A bad stored endedAt (day 3) that predates its occurredAt (day 10) — from an old
        // round-trip (spec §6). The reach is the day-10 start, so the gap to now (day 15) is 5,
        // not the 12 an unfloored day-3 end would give.
        val result = computeGapStats(listOf(durationEvent(startDay = 10, endDay = 3)), now = millisAtDay(15))

        assertEquals(5L, result.currentGapDays)
    }

    @Test
    fun `computeGapStats does not let a reversed endedAt shrink a following past gap`() {
        val events = listOf(durationEvent(startDay = 10, endDay = 3), eventAtDay(12))

        val result = computeGapStats(events, now = millisAtDay(20))

        assertEquals(listOf(2L), result.pastGaps)
    }

    @Test
    fun `computeGapStats does not flag bursty with fewer than 3 past gaps even if uneven`() {
        // Only 2 past gaps (1, 20) — below GAP_BURST_MIN_GAP_COUNT regardless of variance.
        val events = listOf(eventAtDay(0), eventAtDay(1), eventAtDay(21))

        val result = computeGapStats(events, now = millisAtDay(22))

        assertTrue(!result.isBursty)
    }

    @Test
    fun `computeGapStats flags bursty when past gaps have high coefficient of variation`() {
        // Past gaps: 1, 1, 1, 30 — a long quiet stretch after a tight cluster.
        val events = listOf(eventAtDay(0), eventAtDay(1), eventAtDay(2), eventAtDay(3), eventAtDay(33))

        val result = computeGapStats(events, now = millisAtDay(35))

        assertTrue(result.isBursty)
    }

    @Test
    fun `computeGapStats does not flag bursty when past gaps are evenly spaced`() {
        // Past gaps: 5, 5, 5, 5 — a steady rhythm, zero variance.
        val events = listOf(eventAtDay(0), eventAtDay(5), eventAtDay(10), eventAtDay(15), eventAtDay(20))

        val result = computeGapStats(events, now = millisAtDay(22))

        assertTrue(!result.isBursty)
    }

    @Test
    fun `computeGapStats counts a past gap that straddles a DST spring-forward in calendar days`() {
        val newYork = ZoneId.of("America/New_York")
        val noonMillis: (LocalDate) -> Long = {
            it
                .atTime(12, 0)
                .atZone(newYork)
                .toInstant()
                .toEpochMilli()
        }
        // Two events a week apart around the 2026-03-08 spring-forward (a 23-hour local day).
        // The event-to-event gap resolves via the newer event's own captured offset (EventEntity.loggedZone()
        // is a fixed ZoneOffset, not a DST-aware ZoneId), so each event carries America/New_York's
        // real historical offset for its own date rather than relying on `newYork`'s own DST rules.
        val events =
            listOf(
                testEvent(occurredAt = noonMillis(LocalDate.of(2026, 3, 6))).copy(utcOffsetMinutes = -300), // EST
                testEvent(occurredAt = noonMillis(LocalDate.of(2026, 3, 13))).copy(utcOffsetMinutes = -240), // EDT
            )

        val result = computeGapStats(events, now = noonMillis(LocalDate.of(2026, 3, 13)), zone = newYork)

        // Seven calendar days — the missing spring-forward hour must not shave it to 6.
        assertEquals(listOf(7L), result.pastGaps)
    }

    @Test
    fun `computeGapStats resolves an event-to-event gap via the newer event's own captured offset`() {
        // 2026-01-05T23:30Z is Jan 5 under UTC but already Jan 6 under a +9h offset — the newer
        // event's own offset decides which calendar day it lands on, not the device's current zone.
        val firstOccurredAt = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()
        val secondOccurredAt = Instant.parse("2026-01-05T23:30:00Z").toEpochMilli()
        val events =
            listOf(
                testEvent(occurredAt = firstOccurredAt).copy(utcOffsetMinutes = 0),
                testEvent(occurredAt = secondOccurredAt).copy(utcOffsetMinutes = 9 * 60),
            )

        val result = computeGapStats(events, now = Instant.parse("2026-01-10T00:00:00Z").toEpochMilli(), zone = ZoneOffset.UTC)

        // Jan 1 to Jan 6 (the second event's own local date) is 5 days, not the 4 days a UTC reading
        // of the same instant (Jan 5) would give.
        assertEquals(listOf(5L), result.pastGaps)
    }

    // ---- computeStreakStats ----

    @Test
    fun `computeStreakStats is all-zero with no active dates`() {
        val result = computeStreakStats(emptyList())

        assertEquals(0, result.longestStreakDays)
        assertEquals(0.0, result.averageStreakDays, 0.0001)
    }

    @Test
    fun `computeStreakStats treats a single active day as a streak of one`() {
        val result = computeStreakStats(listOf(LocalDate.ofEpochDay(5)))

        assertEquals(1, result.longestStreakDays)
        assertEquals(1.0, result.averageStreakDays, 0.0001)
    }

    @Test
    fun `computeStreakStats collapses consecutive days into a single run, duplicates and all`() {
        val dates = listOf(0L, 1L, 1L, 2L, 3L).map { LocalDate.ofEpochDay(it) }

        val result = computeStreakStats(dates)

        assertEquals(4, result.longestStreakDays)
        assertEquals(4.0, result.averageStreakDays, 0.0001)
    }

    @Test
    fun `computeStreakStats reports the longest run and the average across several runs`() {
        // Runs: [0,1,2] (3 days), [5] (1 day), [8,9] (2 days) -> longest 3, average 2.
        val dates = listOf(0L, 1L, 2L, 5L, 8L, 9L).map { LocalDate.ofEpochDay(it) }

        val result = computeStreakStats(dates)

        assertEquals(3, result.longestStreakDays)
        assertEquals(2.0, result.averageStreakDays, 0.0001)
    }

    // ---- computeGapShift ----

    @Test
    fun `computeGapShift is null below the minimum sample count`() {
        val pastGaps = List(GAP_SHIFT_MIN_SAMPLE_COUNT - 1) { 5L }

        assertEquals(null, computeGapShift(pastGaps))
    }

    @Test
    fun `computeGapShift reports UP when the second half's average gap grew noticeably`() {
        val pastGaps = listOf(2L, 2L, 2L, 10L, 10L, 10L)

        val result = computeGapShift(pastGaps)

        assertEquals(ShiftDirection.UP, result?.direction)
        assertEquals(2.0, result?.priorAverageDays ?: -1.0, 0.0001)
        assertEquals(10.0, result?.recentAverageDays ?: -1.0, 0.0001)
        assertEquals(6, result?.sampleCount)
    }

    @Test
    fun `computeGapShift reports DOWN when the second half's average gap shrank noticeably`() {
        val pastGaps = listOf(10L, 10L, 10L, 2L, 2L, 2L)

        assertEquals(ShiftDirection.DOWN, computeGapShift(pastGaps)?.direction)
    }

    @Test
    fun `computeGapShift is null when the change is too small to be noticeable`() {
        val pastGaps = listOf(10L, 10L, 10L, 11L, 11L, 11L)

        assertEquals(null, computeGapShift(pastGaps))
    }

    // ---- computeQuietSignal ----

    /** [pastGaps] never includes [currentGapDays] -- mirrors how [computeGapStats] keeps the two separate. */
    private fun gapStats(
        pastGaps: List<Long>,
        currentGapDays: Long,
        eventActiveNow: Boolean = false,
    ): GapStats {
        val longestPastGap = pastGaps.maxOrNull() ?: 0L
        val effectiveCurrentGapDays = if (eventActiveNow) 0L else currentGapDays
        return GapStats(
            currentGapDays = effectiveCurrentGapDays,
            longestGapDays = maxOf(longestPastGap, effectiveCurrentGapDays),
            isCurrentGapLongest = !eventActiveNow && currentGapDays >= longestPastGap,
            averageGapDays = if (pastGaps.isEmpty()) 0.0 else pastGaps.average(),
            isBursty = false,
            pastGaps = pastGaps,
        )
    }

    @Test
    fun `computeQuietSignal fires when the current gap is a record and the user is active elsewhere`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 20L)

        val result = computeQuietSignal(stats, recentlyActiveElsewhere = true)

        assertEquals(20L, result?.currentGapDays)
        assertEquals(5L, result?.longestPastGapDays)
        assertEquals(QUIET_SIGNAL_MIN_SAMPLE_COUNT, result?.sampleCount)
    }

    @Test
    fun `computeQuietSignal is null below the minimum sample count`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT - 1) { 5L }, currentGapDays = 20L)

        assertEquals(null, computeQuietSignal(stats, recentlyActiveElsewhere = true))
    }

    @Test
    fun `computeQuietSignal fires at exactly the minimum sample count`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 20L)

        assertTrue(computeQuietSignal(stats, recentlyActiveElsewhere = true) != null)
    }

    @Test
    fun `computeQuietSignal fires on a tie with the longest past gap, not just when it's exceeded`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 5L)

        assertTrue(computeQuietSignal(stats, recentlyActiveElsewhere = true) != null)
    }

    @Test
    fun `computeQuietSignal is null when the current gap is not a record`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L } + 30L, currentGapDays = 20L)

        assertEquals(null, computeQuietSignal(stats, recentlyActiveElsewhere = true))
    }

    @Test
    fun `computeQuietSignal is null when the current gap is a record but the user hasn't been active elsewhere`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 20L)

        assertEquals(null, computeQuietSignal(stats, recentlyActiveElsewhere = false))
    }

    @Test
    fun `computeQuietSignal is null while an event is active on the Case`() {
        val stats = gapStats(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 20L, eventActiveNow = true)

        assertEquals(null, computeQuietSignal(stats, recentlyActiveElsewhere = true))
    }

    @Test
    fun `computeQuietSignal is null for a Case with no past gaps`() {
        val stats = gapStats(pastGaps = emptyList(), currentGapDays = 20L)

        assertEquals(null, computeQuietSignal(stats, recentlyActiveElsewhere = true))
    }

    // ---- computeStreakShift ----

    @Test
    fun `computeStreakShift is null below the minimum sample count`() {
        // Only 3 one-day runs (below STREAK_SHIFT_MIN_SAMPLE_COUNT), all isolated days.
        val dates = listOf(0L, 2L, 4L).map { LocalDate.ofEpochDay(it) }

        assertEquals(null, computeStreakShift(dates))
    }

    @Test
    fun `computeStreakShift reports UP when later runs are noticeably longer`() {
        // First half: three 1-day runs. Second half: three 4-day runs.
        val isolatedDays = listOf(0L, 10L, 20L)
        val longRunStarts = listOf(100L, 200L, 300L)
        val dates = (isolatedDays + longRunStarts.flatMap { start -> (start until start + 4) }).map { LocalDate.ofEpochDay(it) }

        assertEquals(ShiftDirection.UP, computeStreakShift(dates)?.direction)
    }

    // ---- computeRecurrenceShape ----

    @Test
    fun `computeRecurrenceShape is null below the minimum sample count`() {
        val stats = gapStats(pastGaps = listOf(2L, 2L, 2L, 2L, 20L), currentGapDays = 0L)

        assertEquals(null, computeRecurrenceShape(stats))
    }

    @Test
    fun `computeRecurrenceShape reports an early-spike finding at exactly the minimum sample count`() {
        // Five short gaps clustered well under half the mean, one long one -- 5 of 6 landed early.
        val stats = gapStats(pastGaps = listOf(2L, 2L, 2L, 2L, 2L, 20L), currentGapDays = 0L)

        val result = computeRecurrenceShape(stats)

        assertEquals(ShiftDirection.UP, result?.direction)
        assertEquals(RECURRENCE_SHAPE_MIN_SAMPLE_COUNT, result?.sampleCount)
    }

    @Test
    fun `computeRecurrenceShape reports an early-spike finding with the threshold and share it used`() {
        // Eight short gaps at 2, one long at 20 -- mean 4.0, threshold 2.0, 8 of 9 gaps landed early.
        val pastGaps = List(8) { 2L } + 20L
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        val result = computeRecurrenceShape(stats)

        assertEquals(ShiftDirection.UP, result?.direction)
        assertEquals(2.0, result?.thresholdDays ?: -1.0, 0.0001)
        assertEquals(8.0 / 9.0, result?.earlyShare ?: -1.0, 0.0001)
        assertEquals(9, result?.sampleCount)
    }

    @Test
    fun `computeRecurrenceShape fires spike right at the share boundary`() {
        // Six gaps at 1, four at 10 -- mean 4.6, threshold 2.3, exactly 6 of 10 landed early.
        val pastGaps = List(6) { 1L } + List(4) { 10L }
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        val result = computeRecurrenceShape(stats)

        assertEquals(ShiftDirection.UP, result?.direction)
        assertEquals(0.6, result?.earlyShare ?: -1.0, 0.0001)
    }

    @Test
    fun `computeRecurrenceShape reports a dead-zone finding with real spread`() {
        // Nine gaps at 10, one at 50 -- mean 14, threshold 7, none of the ten landed early.
        val pastGaps = List(9) { 10L } + 50L
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        val result = computeRecurrenceShape(stats)

        assertEquals(ShiftDirection.DOWN, result?.direction)
        assertEquals(7.0, result?.thresholdDays ?: -1.0, 0.0001)
        assertEquals(0.0, result?.earlyShare ?: -1.0, 0.0001)
        assertEquals(10, result?.sampleCount)
    }

    @Test
    fun `computeRecurrenceShape fires dead zone right at the share boundary, given real spread`() {
        // One gap at 1, nine at 30 -- mean 27.1, threshold 13.55, exactly 1 of 10 landed early.
        val pastGaps = listOf(1L) + List(9) { 30L }
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        val result = computeRecurrenceShape(stats)

        assertEquals(ShiftDirection.DOWN, result?.direction)
        assertEquals(0.1, result?.earlyShare ?: -1.0, 0.0001)
    }

    @Test
    fun `computeRecurrenceShape is null just below the spike share boundary`() {
        // 59 of 100 gaps land early (0.59) -- one short of RECURRENCE_SHAPE_SPIKE_MIN_SHARE (0.6).
        val pastGaps = List(59) { 2L } + List(41) { 20L }
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        assertEquals(null, computeRecurrenceShape(stats))
    }

    @Test
    fun `computeRecurrenceShape is null just below the dead-zone coefficient-of-variation gate, even though the share alone qualifies`() {
        // Nine gaps at 10, one at 20 -- earlyShare is 0 (clears the dead-zone share bar on its own),
        // but coefficient of variation is 0.2727, just short of RECURRENCE_SHAPE_DEAD_ZONE_MIN_COEFFICIENT_OF_VARIATION
        // (0.3) -- distinct from the all-identical steady-rhythm case below, this pins the gate's own
        // boundary with real (if modest) spread in the data.
        val pastGaps = List(9) { 10L } + 20L
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        assertEquals(null, computeRecurrenceShape(stats))
    }

    @Test
    fun `computeRecurrenceShape fires dead zone exactly at the coefficient-of-variation gate boundary`() {
        // Nine gaps at 9, one at 19 -- mean 10, coefficient of variation exactly 0.3.
        val pastGaps = List(9) { 9L } + 19L
        val stats = gapStats(pastGaps = pastGaps, currentGapDays = 0L)

        val result = computeRecurrenceShape(stats)

        assertEquals(ShiftDirection.DOWN, result?.direction)
        assertEquals(0.0, result?.earlyShare ?: -1.0, 0.0001)
    }

    @Test
    fun `computeRecurrenceShape is null for a steady rhythm even though no gap is ever early`() {
        // Every gap identical -- earlyShare is 0, which alone would clear the dead-zone share bar,
        // but there's no real spread (coefficient of variation 0), so this must not fire.
        val stats = gapStats(pastGaps = List(RECURRENCE_SHAPE_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 0L)

        assertEquals(null, computeRecurrenceShape(stats))
    }

    @Test
    fun `computeRecurrenceShape is null for a flat hazard that clears neither bar`() {
        val stats = gapStats(pastGaps = listOf(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), currentGapDays = 0L)

        assertEquals(null, computeRecurrenceShape(stats))
    }

    @Test
    fun `computeRecurrenceShape is null when the average gap is zero`() {
        // Every past gap 0 days apart -- nothing to be "early" relative to.
        val stats = gapStats(pastGaps = List(RECURRENCE_SHAPE_MIN_SAMPLE_COUNT) { 0L }, currentGapDays = 0L)

        assertEquals(null, computeRecurrenceShape(stats))
    }

    @Test
    fun `computeRecurrenceShape and isBursty are independent -- a bursty Case can still land in the flat middle`() {
        // Gaps 2,3,4,5,6,7,8,9,10,40 -- coefficient of variation 1.12 clears isBursty's own bar (a
        // fact this test pins using the real computeGapStats, not the synthetic gapStats() helper
        // above, which always hardcodes isBursty false), but the early-gap share (0.3) clears
        // neither recurrence-shape bar. The two flags describe different things about the same
        // distribution and can disagree.
        val days = listOf(0L, 2L, 5L, 9L, 14L, 20L, 27L, 35L, 44L, 54L, 94L)
        val events = days.map { eventAtDay(it) }

        val stats = computeGapStats(events, now = millisAtDay(96))

        assertTrue(stats.isBursty)
        assertEquals(null, computeRecurrenceShape(stats))
    }

    // ---- heatmapLevelFor ----

    @Test
    fun `heatmapLevelFor is EMPTY for a zero count`() {
        assertEquals(HeatmapLevel.EMPTY, heatmapLevelFor(count = 0, maxCountInRange = 5))
    }

    @Test
    fun `heatmapLevelFor is EMPTY when the range has no events at all`() {
        assertEquals(HeatmapLevel.EMPTY, heatmapLevelFor(count = 0, maxCountInRange = 0))
    }

    @Test
    fun `heatmapLevelFor buckets ratios into all 10 shaded tiers`() {
        assertEquals(HeatmapLevel.L1, heatmapLevelFor(count = 1, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L2, heatmapLevelFor(count = 2, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L3, heatmapLevelFor(count = 3, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L4, heatmapLevelFor(count = 4, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L5, heatmapLevelFor(count = 5, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L6, heatmapLevelFor(count = 6, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L7, heatmapLevelFor(count = 7, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L8, heatmapLevelFor(count = 8, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L9, heatmapLevelFor(count = 9, maxCountInRange = 10))
        assertEquals(HeatmapLevel.L10, heatmapLevelFor(count = 10, maxCountInRange = 10))
    }

    @Test
    fun `heatmapLevelFor always reaches the top tier at the busiest count, regardless of scale`() {
        assertEquals(HeatmapLevel.L10, heatmapLevelFor(count = 4, maxCountInRange = 4))
    }

    @Test
    fun `heatmapLevelFor rounds a ratio up to the next tier rather than down`() {
        // 3 of 10 sits exactly on tier 3's boundary (30%) and stays there.
        assertEquals(HeatmapLevel.L3, heatmapLevelFor(count = 3, maxCountInRange = 10))
        // 1 of 3 is 33% -- just past tier 3's 30% boundary, so it rounds up into tier 4.
        assertEquals(HeatmapLevel.L4, heatmapLevelFor(count = 1, maxCountInRange = 3))
    }

    @Test
    fun `heatmapLevelFor buckets into a custom tier count, for Rhythm's finer 20-tier scale`() {
        assertEquals(HeatmapLevel.L1, heatmapLevelFor(count = 1, maxCountInRange = 20, tierCount = RHYTHM_TIER_COUNT))
        assertEquals(HeatmapLevel.L10, heatmapLevelFor(count = 10, maxCountInRange = 20, tierCount = RHYTHM_TIER_COUNT))
        assertEquals(HeatmapLevel.L20, heatmapLevelFor(count = 20, maxCountInRange = 20, tierCount = RHYTHM_TIER_COUNT))
    }
}
