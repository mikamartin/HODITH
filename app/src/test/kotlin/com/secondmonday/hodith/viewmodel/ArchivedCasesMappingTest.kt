package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CaseWithEvents
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchivedCasesMappingTest {
    @Test
    fun `maps case identity fields and event count through`() {
        val rows =
            archivedCaseRows(
                listOf(
                    caseWithEvents(
                        caseId = 3L,
                        icon = "☕️",
                        name = "Coffee",
                        events = listOf(testEvent(), testEvent()),
                    ),
                ),
            )

        val row = rows.single()
        assertEquals(3L, row.caseId)
        assertEquals("☕️", row.icon)
        assertEquals("Coffee", row.name)
        assertEquals(2, row.eventCount)
    }

    @Test
    fun `case with no events gets zero event count`() {
        val rows = archivedCaseRows(listOf(caseWithEvents(events = emptyList())))

        assertEquals(0, rows.single().eventCount)
    }

    @Test
    fun `preserves list order`() {
        val rows =
            archivedCaseRows(
                listOf(
                    caseWithEvents(caseId = 1L, name = "First", events = emptyList()),
                    caseWithEvents(caseId = 2L, name = "Second", events = emptyList()),
                ),
            )

        assertEquals(listOf("First", "Second"), rows.map { it.name })
    }

    private fun caseWithEvents(
        caseId: Long = 1L,
        icon: String = "🐛",
        name: String = "Test Case",
        events: List<EventEntity>,
    ) = CaseWithEvents(
        case =
            CaseEntity(
                id = caseId,
                name = name,
                icon = icon,
                createdAt = 0L,
                logFlow = LogFlow.ONE_TAP,
                durationMode = DurationMode.NONE,
                intensityEnabled = false,
                hunchNudgeDismissed = false,
                pinned = false,
                checkInsEnabled = true,
                lastCheckInAt = null,
                sortOrder = 0,
                archived = true,
            ),
        events = events,
    )

    private fun testEvent() =
        EventEntity(
            caseId = 1L,
            occurredAt = 0L,
            endedAt = null,
            intensity = null,
            note = null,
            loggedAt = 0L,
        )
}
