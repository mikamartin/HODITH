package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `filterAndSortEvents` is Log Share's reusable filter (spec §13) — built as its own unit so a
 * future Log-tab-filter item can call the same function. Pins inclusive date bounds on either
 * side, unbounded-side behavior, and both sort directions.
 */
class LogFilterTest {
    private fun eventAt(occurredAt: Long) =
        EventWithTags(
            event =
                EventEntity(
                    id = occurredAt,
                    caseId = 1L,
                    occurredAt = occurredAt,
                    endedAt = null,
                    intensity = null,
                    note = null,
                    loggedAt = occurredAt,
                ),
            tags = emptyList(),
        )

    private val events = listOf(eventAt(100L), eventAt(200L), eventAt(300L))

    @Test
    fun `both bounds null keeps every event, unbounded`() {
        val result = filterAndSortEvents(events, from = null, to = null, order = ChronologicalOrder.NEWEST_FIRST)

        assertEquals(listOf(300L, 200L, 100L), result.map { it.event.occurredAt })
    }

    @Test
    fun `from bound is inclusive`() {
        val result = filterAndSortEvents(events, from = 200L, to = null, order = ChronologicalOrder.OLDEST_FIRST)

        assertEquals(listOf(200L, 300L), result.map { it.event.occurredAt })
    }

    @Test
    fun `to bound is inclusive`() {
        val result = filterAndSortEvents(events, from = null, to = 200L, order = ChronologicalOrder.OLDEST_FIRST)

        assertEquals(listOf(100L, 200L), result.map { it.event.occurredAt })
    }

    @Test
    fun `both bounds narrow to the range between them`() {
        val result = filterAndSortEvents(events, from = 150L, to = 250L, order = ChronologicalOrder.NEWEST_FIRST)

        assertEquals(listOf(200L), result.map { it.event.occurredAt })
    }

    @Test
    fun `newest first sorts descending by occurredAt`() {
        val result = filterAndSortEvents(events, from = null, to = null, order = ChronologicalOrder.NEWEST_FIRST)

        assertEquals(listOf(300L, 200L, 100L), result.map { it.event.occurredAt })
    }

    @Test
    fun `oldest first sorts ascending by occurredAt`() {
        val result = filterAndSortEvents(events, from = null, to = null, order = ChronologicalOrder.OLDEST_FIRST)

        assertEquals(listOf(100L, 200L, 300L), result.map { it.event.occurredAt })
    }

    @Test
    fun `empty input returns empty output`() {
        val result = filterAndSortEvents(emptyList(), from = null, to = null, order = ChronologicalOrder.NEWEST_FIRST)

        assertEquals(emptyList<EventWithTags>(), result)
    }

    @Test
    fun `a range matching nothing returns empty`() {
        val result = filterAndSortEvents(events, from = 1_000L, to = 2_000L, order = ChronologicalOrder.NEWEST_FIRST)

        assertEquals(emptyList<EventWithTags>(), result)
    }
}
