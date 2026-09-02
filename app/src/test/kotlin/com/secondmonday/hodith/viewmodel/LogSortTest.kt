package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.testsupport.Fixtures
import com.secondmonday.hodith.testsupport.withoutTags
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [sortEventsForLog] — the Log tab's start/end sort (spec §6). `BY_START` must stay
 * byte-identical to Room's own `ORDER BY occurredAt DESC`; `BY_END` is the new view.
 */
class LogSortTest {
    private fun events(vararg spec: Pair<Long, Long?>): List<EventWithTags> =
        spec
            .mapIndexed { index, (occurredAt, endedAt) ->
                Fixtures.event(id = index + 1L, occurredAt = occurredAt, endedAt = endedAt)
            }.withoutTags()

    private fun idsAfter(
        events: List<EventWithTags>,
        order: LogSortOrder,
        durationMode: DurationMode,
    ): List<Long> = sortEventsForLog(events, order, durationMode).map { it.event.id }

    @Test
    fun `BY_START is newest occurredAt first`() {
        val list = events(100L to null, 300L to null, 200L to null)
        assertEquals(listOf(2L, 3L, 1L), idsAfter(list, LogSortOrder.BY_START, DurationMode.START_STOP))
    }

    @Test
    fun `BY_START breaks occurredAt ties by id descending for a stable order`() {
        val list = events(100L to null, 100L to null, 100L to null)
        assertEquals(listOf(3L, 2L, 1L), idsAfter(list, LogSortOrder.BY_START, DurationMode.START_STOP))
    }

    @Test
    fun `BY_END floats still-running START_STOP events above finished ones`() {
        // id1 finished recently, id2 still running (older start), id3 finished long ago.
        val list = events(500L to 900L, 200L to null, 100L to 150L)
        assertEquals(listOf(2L, 1L, 3L), idsAfter(list, LogSortOrder.BY_END, DurationMode.START_STOP))
    }

    @Test
    fun `BY_END orders several running events newest-start first`() {
        val list = events(100L to null, 300L to null, 200L to null)
        assertEquals(listOf(2L, 3L, 1L), idsAfter(list, LogSortOrder.BY_END, DurationMode.START_STOP))
    }

    @Test
    fun `BY_END orders finished events by endedAt descending, not by start`() {
        // id1 started last but ended first; id2 started first but ended last.
        val list = events(400L to 450L, 100L to 900L)
        assertEquals(listOf(2L, 1L), idsAfter(list, LogSortOrder.BY_END, DurationMode.START_STOP))
    }

    @Test
    fun `BY_END on a MANUAL Case treats an end-less entry as ending at its start, not as running`() {
        // id1 MANUAL entry with no duration (endedAt null, occurredAt 800); id2 finished at 500.
        val list = events(800L to null, 300L to 500L)
        assertEquals(listOf(1L, 2L), idsAfter(list, LogSortOrder.BY_END, DurationMode.MANUAL))
    }

    @Test
    fun `BY_END on a NONE Case never floats an end-less event and orders by the start fallback`() {
        val list = events(800L to null, 300L to 500L)
        assertEquals(listOf(1L, 2L), idsAfter(list, LogSortOrder.BY_END, DurationMode.NONE))
    }

    @Test
    fun `BY_END breaks endedAt ties by id descending`() {
        val list = events(100L to 500L, 200L to 500L, 150L to 500L)
        // occurredAt then id both descending: id2 (200) > id3 (150) > id1 (100).
        assertEquals(listOf(2L, 3L, 1L), idsAfter(list, LogSortOrder.BY_END, DurationMode.START_STOP))
    }
}
