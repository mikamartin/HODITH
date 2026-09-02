package com.secondmonday.hodith.domain

import com.secondmonday.hodith.testsupport.durationEvent
import com.secondmonday.hodith.testsupport.eventAtDay
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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
        val events =
            listOf(
                testEvent(occurredAt = noonMillis(LocalDate.of(2026, 3, 6))),
                testEvent(occurredAt = noonMillis(LocalDate.of(2026, 3, 13))),
            )

        val result = computeGapStats(events, now = noonMillis(LocalDate.of(2026, 3, 13)), zone = newYork)

        // Seven calendar days — the missing spring-forward hour must not shave it to 6.
        assertEquals(listOf(7L), result.pastGaps)
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

        assertEquals(ShiftDirection.UP, computeGapShift(pastGaps))
    }

    @Test
    fun `computeGapShift reports DOWN when the second half's average gap shrank noticeably`() {
        val pastGaps = listOf(10L, 10L, 10L, 2L, 2L, 2L)

        assertEquals(ShiftDirection.DOWN, computeGapShift(pastGaps))
    }

    @Test
    fun `computeGapShift is null when the change is too small to be noticeable`() {
        val pastGaps = listOf(10L, 10L, 10L, 11L, 11L, 11L)

        assertEquals(null, computeGapShift(pastGaps))
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

        assertEquals(ShiftDirection.UP, computeStreakShift(dates))
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
