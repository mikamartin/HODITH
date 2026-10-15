package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEventSpan
import com.secondmonday.hodith.data.CaseWithEvents
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.testsupport.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeViewModelMappingTest {
    private val zone = ZoneId.systemDefault()

    // A Wednesday, so "start of today" and "start of week" fall on different days.
    private val now = ZonedDateTime.of(2026, 7, 8, 15, 0, 0, 0, zone)
    private val startOfToday = now.toLocalDate().atStartOfDay(zone)
    private val startOfWeek = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zone)

    @Test
    fun `case with no events gets zero counts`() {
        val rows = homeRows(listOf(caseWithEvents(events = emptyList())), now.toInstant().toEpochMilli())

        assertEquals(0, rows.single().todayCount)
        assertEquals(0, rows.single().weekCount)
    }

    @Test
    fun `event exactly at start of today counts as today and this week`() {
        val event = testEvent(startOfToday.toInstant().toEpochMilli())
        val rows = homeRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(1, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `event one millisecond before start of today counts as this week but not today`() {
        val event = testEvent(startOfToday.toInstant().toEpochMilli() - 1)
        val rows = homeRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(0, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `event exactly at start of week counts as this week`() {
        val event = testEvent(startOfWeek.toInstant().toEpochMilli())
        val rows = homeRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `event one millisecond before start of week does not count as this week`() {
        val event = testEvent(startOfWeek.toInstant().toEpochMilli() - 1)
        val rows = homeRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(0, rows.single().weekCount)
    }

    // --- Active-span day attribution (spec §9/§14): an event counts toward a window if its span
    // reaches into it, counted once. A Case that doesn't track duration collapses events to a
    // point at occurredAt. ---

    @Test
    fun `running event started before this week counts for today and this week`() {
        val running = testEvent(now.minusDays(5).toInstant().toEpochMilli()).copy(endedAt = null)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(running), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(1, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `running event started earlier today counts for today`() {
        val running = testEvent(startOfToday.plusHours(2).toInstant().toEpochMilli()).copy(endedAt = null)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(running), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(1, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `duration event that started earlier but ended today counts for today and this week`() {
        val event =
            testEvent(now.minusDays(6).toInstant().toEpochMilli())
                .copy(endedAt = startOfToday.plusHours(9).toInstant().toEpochMilli())
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(event), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(1, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `duration event that ended earlier this week counts for this week but not today`() {
        val event =
            testEvent(now.minusDays(9).toInstant().toEpochMilli())
                .copy(endedAt = startOfWeek.plusDays(1).toInstant().toEpochMilli())
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(event), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(0, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `a case that doesn't track duration collapses a stored endedAt to a point so a pre-week event does not count`() {
        val event =
            testEvent(now.minusDays(9).toInstant().toEpochMilli())
                .copy(endedAt = now.toInstant().toEpochMilli())
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(event), tracksDuration = false)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(0, rows.single().todayCount)
        assertEquals(0, rows.single().weekCount)
    }

    @Test
    fun `an open event on a tracking case runs to now and counts from before the window`() {
        val event = testEvent(now.minusDays(9).toInstant().toEpochMilli()).copy(endedAt = null)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(event), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(1, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `a multi-day running span counts once alongside point events on the same day`() {
        val running = testEvent(now.minusDays(3).toInstant().toEpochMilli()).copy(id = 1L, endedAt = null)
        val pointA =
            testEvent(startOfToday.plusHours(1).toInstant().toEpochMilli())
                .copy(id = 2L, endedAt = startOfToday.plusHours(2).toInstant().toEpochMilli())
        val pointB =
            testEvent(startOfToday.plusHours(3).toInstant().toEpochMilli())
                .copy(id = 3L, endedAt = startOfToday.plusHours(4).toInstant().toEpochMilli())
        val rows =
            homeRows(
                listOf(
                    caseWithEvents(
                        events = listOf(running, pointA, pointB),
                        tracksDuration = true,
                    ),
                ),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(3, rows.single().todayCount)
    }

    @Test
    fun `maps several cases with independent counts in input order`() {
        val caseAEvent = testEvent(startOfToday.plusHours(1).toInstant().toEpochMilli())
        val caseBEvents =
            listOf(
                testEvent(startOfToday.plusHours(2).toInstant().toEpochMilli()),
                testEvent(startOfWeek.plusHours(1).toInstant().toEpochMilli()),
            )
        val rows =
            homeRows(
                listOf(
                    caseWithEvents(caseId = 1L, name = "A", events = listOf(caseAEvent)),
                    caseWithEvents(caseId = 2L, name = "B", events = caseBEvents),
                ),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(listOf(1L, 2L), rows.map { it.caseId })
        assertEquals(1, rows[0].todayCount)
        assertEquals(1, rows[0].weekCount)
        assertEquals(1, rows[1].todayCount)
        assertEquals(2, rows[1].weekCount)
    }

    @Test
    fun `spans for cases not in the list are ignored (widget passes the full set)`() {
        val todayMillis = startOfToday.plusHours(1).toInstant().toEpochMilli()
        val rows =
            homeCaseRows(
                cases = listOf(Fixtures.case(id = 1L, name = "Shown")),
                eventSpans =
                    listOf(
                        CaseEventSpan(caseId = 1L, occurredAt = todayMillis, endedAt = null, tracksDuration = false),
                        CaseEventSpan(caseId = 2L, occurredAt = todayMillis, endedAt = null, tracksDuration = true),
                    ),
                nowMillis = now.toInstant().toEpochMilli(),
            )

        assertEquals(1, rows.single().todayCount)
        assertNull(rows.single().ongoingEvent)
    }

    @Test
    fun `maps case identity fields through`() {
        val rows =
            homeRows(
                listOf(
                    caseWithEvents(
                        caseId = 7L,
                        icon = "☕️",
                        name = "Coffee",
                        description = "Any cup counted",
                        events = emptyList(),
                    ),
                ),
                now.toInstant().toEpochMilli(),
            )

        val row = rows.single()
        assertEquals(7L, row.caseId)
        assertEquals("☕️", row.icon)
        assertEquals("Coffee", row.name)
        assertEquals("Any cup counted", row.description)
    }

    @Test
    fun `description is null when the case has none`() {
        val rows = homeRows(listOf(caseWithEvents(events = emptyList())), now.toInstant().toEpochMilli())

        assertNull(rows.single().description)
    }

    @Test
    fun `maps logFlow, tracksDuration and intensityEnabled through`() {
        val rows =
            homeRows(
                listOf(
                    caseWithEvents(
                        events = emptyList(),
                        logFlow = LogFlow.DETAIL_SHEET,
                        tracksDuration = true,
                        intensityEnabled = true,
                    ),
                ),
                now.toInstant().toEpochMilli(),
            )

        val row = rows.single()
        assertEquals(LogFlow.DETAIL_SHEET, row.logFlow)
        assertEquals(true, row.tracksDuration)
        assertEquals(true, row.intensityEnabled)
    }

    @Test
    fun `maps the open event as ongoingEvent for a tracking case`() {
        val open = testEvent(occurredAt = 0L).copy(endedAt = null)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(open), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(
            CaseEventSpan(caseId = 1L, occurredAt = 0L, endedAt = null, tracksDuration = true),
            rows.single().ongoingEvent,
        )
    }

    @Test
    fun `maps an event whose endedAt is still in the future as ongoingEvent`() {
        val futureEndedAt = now.toInstant().toEpochMilli() + 1
        val stillRunning = testEvent(occurredAt = 0L).copy(endedAt = futureEndedAt)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(stillRunning), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(
            CaseEventSpan(caseId = 1L, occurredAt = 0L, endedAt = futureEndedAt, tracksDuration = true),
            rows.single().ongoingEvent,
        )
        assertEquals(1, rows.single().runningCount)
    }

    @Test
    fun `ongoingEvent is null for an event whose endedAt has already passed`() {
        val pastEndedAt = now.toInstant().toEpochMilli() - 1
        val finished = testEvent(occurredAt = 0L).copy(endedAt = pastEndedAt)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(finished), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertNull(rows.single().ongoingEvent)
        assertEquals(0, rows.single().runningCount)
    }

    @Test
    fun `ongoingEvent is null for a tracking case with no open event`() {
        val closed = testEvent(occurredAt = 0L).copy(endedAt = 1_000L)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(closed), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertNull(rows.single().ongoingEvent)
    }

    @Test
    fun `ongoingEvent and runningCount ignore a null endedAt event on a case that doesn't track duration`() {
        val event = testEvent(occurredAt = 0L).copy(endedAt = null)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(event), tracksDuration = false)),
                now.toInstant().toEpochMilli(),
            )

        assertNull(rows.single().ongoingEvent)
        assertEquals(0, rows.single().runningCount)
    }

    @Test
    fun `runningCount is zero when nothing is running`() {
        val closed = testEvent(occurredAt = 0L).copy(endedAt = 1_000L)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(closed), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        assertEquals(0, rows.single().runningCount)
    }

    @Test
    fun `runningCount and ongoingEvent reflect several concurrent open events`() {
        val first = testEvent(occurredAt = 100L).copy(id = 1L, endedAt = null)
        val second = testEvent(occurredAt = 500L).copy(id = 2L, endedAt = null)
        val third = testEvent(occurredAt = 900L).copy(id = 3L, endedAt = null)
        val rows =
            homeRows(
                listOf(caseWithEvents(events = listOf(third, first, second), tracksDuration = true)),
                now.toInstant().toEpochMilli(),
            )

        val row = rows.single()
        assertEquals(3, row.runningCount)
        assertEquals(
            CaseEventSpan(caseId = 1L, occurredAt = 100L, endedAt = null, tracksDuration = true),
            row.ongoingEvent,
        )
    }

    /**
     * Bridges the old `CaseWithEvents`-shaped fixtures to [homeCaseRows]' current inputs: the flat
     * Case list and the `CaseEventSpan` projection (`observeActiveCaseEventSpans`' flat join).
     * Keeps every boundary assertion below unchanged.
     */
    private fun homeRows(
        casesWithEvents: List<CaseWithEvents>,
        nowMillis: Long,
    ): List<HomeCaseRow> {
        val spans =
            casesWithEvents.flatMap { (case, events) ->
                events.map { CaseEventSpan(case.id, it.occurredAt, it.endedAt, case.tracksDuration) }
            }
        return homeCaseRows(casesWithEvents.map { it.case }, spans, nowMillis)
    }

    private fun caseWithEvents(
        caseId: Long = 1L,
        icon: String = "🐛",
        name: String = "Test Case",
        description: String? = null,
        events: List<EventEntity>,
        logFlow: LogFlow = LogFlow.ONE_TAP,
        tracksDuration: Boolean = false,
        intensityEnabled: Boolean = false,
    ) = CaseWithEvents(
        case =
            Fixtures.case(
                id = caseId,
                name = name,
                description = description,
                icon = icon,
                logFlow = logFlow,
                tracksDuration = tracksDuration,
                intensityEnabled = intensityEnabled,
            ),
        events = events,
    )

    private fun testEvent(occurredAt: Long) = Fixtures.event(occurredAt = occurredAt)
}
