package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CaseWithEvents
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import org.junit.Assert.assertEquals
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
        val rows = homeCaseRows(listOf(caseWithEvents(events = emptyList())), now.toInstant().toEpochMilli())

        assertEquals(0, rows.single().todayCount)
        assertEquals(0, rows.single().weekCount)
    }

    @Test
    fun `event exactly at start of today counts as today and this week`() {
        val event = testEvent(startOfToday.toInstant().toEpochMilli())
        val rows = homeCaseRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(1, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `event one millisecond before start of today counts as this week but not today`() {
        val event = testEvent(startOfToday.toInstant().toEpochMilli() - 1)
        val rows = homeCaseRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(0, rows.single().todayCount)
        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `event exactly at start of week counts as this week`() {
        val event = testEvent(startOfWeek.toInstant().toEpochMilli())
        val rows = homeCaseRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(1, rows.single().weekCount)
    }

    @Test
    fun `event one millisecond before start of week does not count as this week`() {
        val event = testEvent(startOfWeek.toInstant().toEpochMilli() - 1)
        val rows = homeCaseRows(listOf(caseWithEvents(events = listOf(event))), now.toInstant().toEpochMilli())

        assertEquals(0, rows.single().weekCount)
    }

    @Test
    fun `maps case identity fields through`() {
        val rows =
            homeCaseRows(
                listOf(caseWithEvents(caseId = 7L, icon = "☕️", name = "Coffee", events = emptyList())),
                now.toInstant().toEpochMilli(),
            )

        val row = rows.single()
        assertEquals(7L, row.caseId)
        assertEquals("☕️", row.icon)
        assertEquals("Coffee", row.name)
    }

    @Test
    fun `maps logFlow, durationMode and intensityEnabled through`() {
        val rows =
            homeCaseRows(
                listOf(
                    caseWithEvents(
                        events = emptyList(),
                        logFlow = LogFlow.DETAIL_SHEET,
                        durationMode = DurationMode.MANUAL,
                        intensityEnabled = true,
                    ),
                ),
                now.toInstant().toEpochMilli(),
            )

        val row = rows.single()
        assertEquals(LogFlow.DETAIL_SHEET, row.logFlow)
        assertEquals(DurationMode.MANUAL, row.durationMode)
        assertEquals(true, row.intensityEnabled)
    }

    private fun caseWithEvents(
        caseId: Long = 1L,
        icon: String = "🐛",
        name: String = "Test Case",
        events: List<EventEntity>,
        logFlow: LogFlow = LogFlow.ONE_TAP,
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
    ) = CaseWithEvents(
        case =
            CaseEntity(
                id = caseId,
                name = name,
                icon = icon,
                createdAt = 0L,
                logFlow = logFlow,
                durationMode = durationMode,
                intensityEnabled = intensityEnabled,
                hunchNudgeDismissed = false,
                pinned = false,
                checkInDays = null,
                lastCheckInAt = null,
                sortOrder = 0,
                archived = false,
            ),
        events = events,
    )

    private fun testEvent(occurredAt: Long) =
        EventEntity(
            caseId = 1L,
            occurredAt = occurredAt,
            endedAt = null,
            intensity = null,
            note = null,
            loggedAt = occurredAt,
        )
}
