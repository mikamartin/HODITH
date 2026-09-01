package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.testsupport.TEST_ZONE
import com.secondmonday.hodith.testsupport.durationEvent
import com.secondmonday.hodith.testsupport.eventAtDay
import com.secondmonday.hodith.testsupport.finishedPoint
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testCase
import com.secondmonday.hodith.testsupport.testEvent
import com.secondmonday.hodith.testsupport.withoutTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

private val ZONE = TEST_ZONE

class InsightsTabStateTest {
    @Test
    fun `insightsTabState is NotEnoughData below the minimum event count`() {
        val case = testCase(createdAt = millisAtDay(0))

        val state = insightsTabState(case, eventsWithTags = listOf(eventAtDay(0)).withoutTags(), now = millisAtDay(5))

        assertEquals(InsightsTabState.NotEnoughData, state)
    }

    @Test
    fun `insightsTabState is Ready once the minimum event count is met`() {
        val case = testCase(createdAt = millisAtDay(0))

        val state = insightsTabState(case, eventsWithTags = listOf(eventAtDay(0), eventAtDay(3)).withoutTags(), now = millisAtDay(5))

        assertTrue(state is InsightsTabState.Ready)
    }

    @Test
    fun `heatmap spans from the case's earliest month through the current month`() {
        // Case created in Jan 2026, "now" lands in March 2026 — three months of grids expected.
        val createdAt =
            LocalDate
                .of(2026, 1, 15)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val now =
            LocalDate
                .of(2026, 3, 10)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val events = listOf(EventEntity(0, 1, createdAt, null, null, null, createdAt), EventEntity(0, 1, now, null, null, null, now))

        val state = insightsTabState(testCase(createdAt = createdAt), events.withoutTags(), now) as InsightsTabState.Ready

        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            state.heatmapMonths.map { it.month },
        )
    }

    @Test
    fun `heatmap shades a day with the case's busiest count at the top level`() {
        val createdAt =
            LocalDate
                .of(2026, 3, 1)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val eventDate = LocalDate.of(2026, 3, 15)
        val now =
            LocalDate
                .of(2026, 3, 20)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val events = listOf(eventAtDay(eventDate.toEpochDay()), EventEntity(0, 1, now, null, null, null, now))

        val state = insightsTabState(testCase(createdAt = createdAt), events.withoutTags(), now) as InsightsTabState.Ready

        val shadedDay =
            state.heatmapMonths
                .single()
                .weeks
                .flatten()
                .filterNotNull()
                .single { it.date == eventDate }
        assertEquals(HeatmapLevel.L10, shadedDay.level)
    }

    @Test
    fun `heatmap for the current month has no trailing week-row that's entirely in the future`() {
        // "Now" is the 2nd of the month, so only the first week-row has any real days in it —
        // the remaining 4-5 week-rows of the month grid would otherwise be entirely blank.
        val createdAt =
            LocalDate
                .of(2026, 3, 1)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val now =
            LocalDate
                .of(2026, 3, 2)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val events = listOf(EventEntity(0, 1, createdAt, null, null, null, createdAt), EventEntity(0, 1, now, null, null, null, now))

        val state = insightsTabState(testCase(createdAt = createdAt), events.withoutTags(), now) as InsightsTabState.Ready

        val currentMonth = state.heatmapMonths.single { it.month == YearMonth.of(2026, 3) }
        assertTrue(currentMonth.weeks.size <= 2)
        assertTrue(currentMonth.weeks.last().any { it != null })
    }

    // ---- active span: heatmap + streak cover every day an event was active (§9) ----

    private fun InsightsTabState.Ready.shadedDates(): Set<LocalDate> =
        heatmapMonths
            .flatMap { it.weeks.flatten() }
            .filterNotNull()
            .filter { it.level != HeatmapLevel.EMPTY }
            .map { it.date }
            .toSet()

    private fun InsightsTabState.Ready.heatmapLevelOn(epochDay: Long): HeatmapLevel =
        heatmapMonths
            .flatMap { it.weeks.flatten() }
            .filterNotNull()
            .single { it.date == LocalDate.ofEpochDay(epochDay) }
            .level

    @Test
    fun `heatmap shades every day a finished multi-day event covered, not just its start`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        val events = listOf(eventAtDay(0), durationEvent(startDay = 2, endDay = 6))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertTrue(state.shadedDates().containsAll((2L..6L).map { LocalDate.ofEpochDay(it) }))
    }

    @Test
    fun `heatmap shades a still-running event through today`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.START_STOP)
        // finishedPoint(0), not eventAtDay(0): a bare null-ended event would itself read as ongoing
        // on a START_STOP Case and also span to now, masking whether the day-3 event drives this.
        val events = listOf(finishedPoint(0), durationEvent(startDay = 3, endDay = null))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(9)) as InsightsTabState.Ready

        assertEquals(
            (listOf(0L) + (3L..9L)).map { LocalDate.ofEpochDay(it) }.toSet(),
            state.shadedDates(),
        )
    }

    @Test
    fun `streak counts every day a multi-day event covered as one consecutive run`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        // A lone point event (run of 1) plus a 4-day span (days 10..13).
        val events = listOf(eventAtDay(0), durationEvent(startDay = 10, endDay = 13))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(4, state.stats.gaps.longestStreakDays)
    }

    @Test
    fun `an event that crosses midnight marks both calendar days`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        val crossMidnight =
            EventEntity(
                id = 0,
                caseId = 1,
                occurredAt = millisAtDay(5) + 23 * 3_600_000L,
                endedAt = millisAtDay(6) + 1 * 3_600_000L,
                intensity = null,
                note = null,
                loggedAt = millisAtDay(5),
            )
        val events = listOf(eventAtDay(0), crossMidnight)

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertEquals(2, state.stats.gaps.longestStreakDays)
        assertTrue(state.shadedDates().containsAll(listOf(LocalDate.ofEpochDay(5), LocalDate.ofEpochDay(6))))
    }

    @Test
    fun `a NONE-mode null-ended event stays a single point in the heatmap and streak`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.NONE)
        val events = listOf(eventAtDay(0), eventAtDay(5))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(1, state.stats.gaps.longestStreakDays)
        assertEquals(setOf(LocalDate.ofEpochDay(0), LocalDate.ofEpochDay(5)), state.shadedDates())
    }

    @Test
    fun `overlapping duration events merge into one streak and stack on the shared days`() {
        // The reported case: a 5-day event (days 0..4) and a 12-day event (days 1..12) overlap,
        // so their union is a single 13-day run and days 1..4 carry both events.
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        val events = listOf(durationEvent(startDay = 0, endDay = 4), durationEvent(startDay = 1, endDay = 12))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(13, state.stats.gaps.longestStreakDays)
        // A day both events cover shades darker than a day only one covers.
        assertTrue(state.heatmapLevelOn(2).ordinal > state.heatmapLevelOn(0).ordinal)
    }

    @Test
    fun `a still-running event extends the streak through today`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.START_STOP)
        // A finished same-day event on day 0 (a run of 1), then an event started day 5 and never
        // stopped; "now" is day 12, so days 5..12 are all active -> an 8-day run.
        val events = listOf(durationEvent(startDay = 0, endDay = 0), durationEvent(startDay = 5, endDay = null))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(12)) as InsightsTabState.Ready

        assertEquals(8, state.stats.gaps.longestStreakDays)
    }

    @Test
    fun `frequency-over-time is hidden and rhythm relabelled once the Case has a multi-day event`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        // A 4-day event (days 15..18) plus a point event on day 19; "now" is day 20.
        val events = listOf(durationEvent(startDay = 15, endDay = 18), eventAtDay(19))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        // The heatmap still spreads the duration event across its four days...
        assertTrue(state.shadedDates().containsAll((15L..18L).map { LocalDate.ofEpochDay(it) }))
        // ...but a per-bucket count can't say "how often" for a span, so the card is dropped
        // and the rhythm grid announces that it plots starts.
        assertEquals(null, state.stats.frequency)
        assertTrue(state.stats.rhythm.plottedByStart)
    }

    @Test
    fun `frequency-over-time is shown and start-anchored when every event fits within a day`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        // A same-day duration event (day 15) plus four point events; "now" is day 20.
        val events =
            listOf(durationEvent(startDay = 15, endDay = 15)) + (16L..19L).map { eventAtDay(it) }

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(
            5,
            state.stats.frequency
                ?.bars
                ?.sumOf { it.count },
        )
        assertEquals(false, state.stats.rhythm.plottedByStart)
    }

    @Test
    fun `a still-running event that began before today makes the Case multi-day`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.START_STOP)
        // Started day 5, never stopped; "now" is day 12 -> its active span is days 5..12. The other
        // event is a finished point (finishedPoint, not eventAtDay) so it can't be the running one.
        val events = listOf(finishedPoint(0), durationEvent(startDay = 5, endDay = null))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(12)) as InsightsTabState.Ready

        assertEquals(null, state.stats.frequency)
        assertTrue(state.stats.rhythm.plottedByStart)
    }

    @Test
    fun `a NONE Case renders a stored multi-day endedAt as a point in the heatmap and streak`() {
        // The Case was switched to NONE but an old event still carries a 5-day endedAt (§9): it
        // must collapse to its start day, not span.
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.NONE)
        val events = listOf(eventAtDay(0), durationEvent(startDay = 2, endDay = 6))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertEquals(setOf(LocalDate.ofEpochDay(0), LocalDate.ofEpochDay(2)), state.shadedDates())
        assertEquals(1, state.stats.gaps.longestStreakDays)
    }

    @Test
    fun `a NONE Case with a stored multi-day endedAt keeps the frequency card visible`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.NONE)
        val events = listOf(durationEvent(startDay = 15, endDay = 18), eventAtDay(19))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(
            2,
            state.stats.frequency
                ?.bars
                ?.sumOf { it.count },
        )
        assertEquals(false, state.stats.rhythm.plottedByStart)
    }

    @Test
    fun `a NONE Case reads the current gap from occurredAt, not a stored endedAt`() {
        // A duration event ran days 1..14 but the Case is now NONE; "now" is day 20. The silence
        // counts from day 1, where the START_STOP version (below) would report 0 at day 14.
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.NONE)
        val events = listOf(eventAtDay(0), durationEvent(startDay = 1, endDay = 14))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(19L, state.stats.gaps.currentGapDays)
    }

    // ---- stats.totalEventCount / stats.tags ----

    @Test
    fun `totalEventCount reflects every logged event, tagged or not`() {
        val case = testCase(createdAt = millisAtDay(0))
        val tag = TagEntity(id = 1, name = "standup")
        val eventsWithTags =
            listOf(
                EventWithTags(eventAtDay(0), listOf(tag)),
                EventWithTags(eventAtDay(1), emptyList()),
                EventWithTags(eventAtDay(2), emptyList()),
            )

        val state = insightsTabState(case, eventsWithTags, now = millisAtDay(5)) as InsightsTabState.Ready

        assertEquals(3, state.stats.totalEventCount)
    }

    @Test
    fun `tags breakdown counts only tagged events, busiest first, independent of the untagged total`() {
        val case = testCase(createdAt = millisAtDay(0))
        val standup = TagEntity(id = 1, name = "standup")
        val weekend = TagEntity(id = 2, name = "weekend")
        val eventsWithTags =
            listOf(
                EventWithTags(eventAtDay(0), listOf(standup)),
                EventWithTags(eventAtDay(1), listOf(standup, weekend)),
                EventWithTags(eventAtDay(2), emptyList()),
                EventWithTags(eventAtDay(3), emptyList()),
            )

        val state = insightsTabState(case, eventsWithTags, now = millisAtDay(5)) as InsightsTabState.Ready

        assertEquals(4, state.stats.totalEventCount)
        assertEquals(listOf(TagBreakdownEntry("standup", 2), TagBreakdownEntry("weekend", 1)), state.stats.tags)
    }

    // ---- stats.gaps streak fields / stats.trend shift fields ----

    @Test
    fun `gaps display reports the longest and average streak of consecutive active days`() {
        val case = testCase(createdAt = millisAtDay(0))
        // Runs: [0,1,2] (3 days), [10] (1 day) -> longest 3, average 2.
        val events = listOf(eventAtDay(0), eventAtDay(1), eventAtDay(2), eventAtDay(10))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(15)) as InsightsTabState.Ready

        assertEquals(3, state.stats.gaps.longestStreakDays)
        assertEquals(2.0, state.stats.gaps.averageStreakDays, 0.0001)
    }

    @Test
    fun `trend flags a noticeable widening of the average gap once enough history exists`() {
        val case = testCase(createdAt = millisAtDay(0))
        // Past gaps: 4, 4, 4, 20, 20, 20 -> clearly widening in the second half.
        val events =
            listOf(0L, 4L, 8L, 12L, 32L, 52L, 72L).map { eventAtDay(it) }

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(90)) as InsightsTabState.Ready

        assertEquals(ShiftDirection.UP, state.stats.trend?.gapShiftDirection)
    }

    @Test
    fun `trend omits gap and streak shift notes below the trend card's own minimum span`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(4))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertEquals(null, state.stats.trend)
    }

    @Test
    fun `trend still counts a multi-day event once, unaffected by its span (spec section 9)`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        val pointEvents = listOf(eventAtDay(0), eventAtDay(20), eventAtDay(70), eventAtDay(80))
        val asPoint = insightsTabState(case, (pointEvents + eventAtDay(85)).withoutTags(), now = millisAtDay(90))
        val asSpan = insightsTabState(case, (pointEvents + durationEvent(85, 95)).withoutTags(), now = millisAtDay(90))

        val point = (asPoint as InsightsTabState.Ready).stats.trend
        val span = (asSpan as InsightsTabState.Ready).stats.trend
        // The span hides the frequency card (tested elsewhere) but must not inflate the trend counts.
        assertNull(asSpan.stats.frequency)
        assertNotNull(point)
        assertEquals(point?.recentCount, span?.recentCount)
        assertEquals(point?.priorCount, span?.priorCount)
    }

    // ---- stats.duration card gate (spec section 10) ----

    @Test
    fun `duration card is present for a MANUAL Case with finished durations`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL)
        val events = listOf(testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(0) + 30 * 60_000L), eventAtDay(3))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertEquals(30L, state.stats.duration?.longestMinutes)
    }

    @Test
    fun `duration card is absent for a NONE Case even when events carry a stored endedAt`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.NONE)
        val events = listOf(testEvent(occurredAt = millisAtDay(0), endedAt = millisAtDay(0) + 30 * 60_000L), eventAtDay(3))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertNull(state.stats.duration)
    }

    // ---- stats.gaps while an event is running (A1) ----

    @Test
    fun `gaps display reports a zero current gap while a START_STOP event is running`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.START_STOP)
        // The day-5 event is still open (endedAt == null), so the Case is running right now.
        val events = listOf(eventAtDay(0), eventAtDay(5))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(0L, state.stats.gaps.currentGapDays)
    }

    @Test
    fun `gaps display keeps the current gap growing for a NONE-mode case with a null-ended event`() {
        // Same events, but a NONE-mode Case can't be "running" — a null endedAt there is just a
        // one-tap event, so the current gap still counts from the last one.
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.NONE)
        val events = listOf(eventAtDay(0), eventAtDay(5))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(15L, state.stats.gaps.currentGapDays)
    }

    @Test
    fun `gaps display reads the current gap from a finished duration event's end`() {
        // A duration event ran days 1..14 and was stopped; "now" is day 14. No silence yet — where
        // the old start-anchored math reported 13 days. Both events are finished, so nothing is
        // "running" (finishedPoint, not eventAtDay) — this exercises the end-anchored reach itself,
        // not the eventActiveNow short-circuit that a bare null-ended filler would trip.
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.START_STOP)
        val events = listOf(finishedPoint(0), durationEvent(startDay = 1, endDay = 14))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(14)) as InsightsTabState.Ready

        assertEquals(0L, state.stats.gaps.currentGapDays)
    }

    @Test
    fun `gaps display keeps the active stretch out of the longest gap`() {
        val case = testCase(createdAt = millisAtDay(0), durationMode = DurationMode.START_STOP)
        // Past gaps: 2, 2 days. The 36-day active stretch to "now" must not become the longest.
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(4))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(40)) as InsightsTabState.Ready

        assertEquals(2L, state.stats.gaps.longestGapDays)
    }
}
