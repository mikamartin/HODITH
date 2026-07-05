package com.secondmonday.hodith.domain.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val WEEK_WINDOW = TimeWindow(startMillis = 0L, endMillis = ZoomLevel.WEEK.durationMillis)

class TimelineLayoutTest {
    @Test
    fun `no events produces no marks`() {
        val marks = layoutRow(events = emptyList(), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        assertTrue(marks.isEmpty())
    }

    @Test
    fun `event before the window is excluded`() {
        val event = TimelineEvent(id = 1, occurredAt = -1L)

        val marks = layoutRow(events = listOf(event), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        assertTrue(marks.isEmpty())
    }

    @Test
    fun `event after the window is excluded`() {
        val event = TimelineEvent(id = 1, occurredAt = WEEK_WINDOW.endMillis + 1)

        val marks = layoutRow(events = listOf(event), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        assertTrue(marks.isEmpty())
    }

    @Test
    fun `events exactly on the window boundaries are included`() {
        val atStart = TimelineEvent(id = 1, occurredAt = WEEK_WINDOW.startMillis)
        val atEnd = TimelineEvent(id = 2, occurredAt = WEEK_WINDOW.endMillis)

        val marks = layoutRow(events = listOf(atStart, atEnd), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        assertEquals(2, marks.size)
    }

    @Test
    fun `single point event becomes one dot at its slot center`() {
        val event = TimelineEvent(id = 1, occurredAt = WEEK_WINDOW.startMillis)

        val marks = layoutRow(events = listOf(event), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        val dot = marks.single() as TimelineMark.Dot
        assertEquals(listOf(1L), dot.eventIds)
        assertEquals(MIN_DOT_SIZE_FRACTION, dot.sizeFraction)
    }

    @Test
    fun `two events in the same slot cluster into one larger dot`() {
        val slotWidth = WEEK_WINDOW.durationMillis / 10
        val first = TimelineEvent(id = 1, occurredAt = 0L)
        val second = TimelineEvent(id = 2, occurredAt = slotWidth / 2)

        val marks = layoutRow(events = listOf(first, second), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        val dot = marks.single() as TimelineMark.Dot
        assertEquals(setOf(1L, 2L), dot.eventIds.toSet())
        assertEquals(MAX_DOT_SIZE_FRACTION, dot.sizeFraction)
    }

    @Test
    fun `two events in different slots stay as separate dots`() {
        val first = TimelineEvent(id = 1, occurredAt = WEEK_WINDOW.startMillis)
        val second = TimelineEvent(id = 2, occurredAt = WEEK_WINDOW.endMillis)

        val marks = layoutRow(events = listOf(first, second), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        assertEquals(2, marks.size)
        val ids = marks.filterIsInstance<TimelineMark.Dot>().flatMap { it.eventIds }
        assertEquals(setOf(1L, 2L), ids.toSet())
    }

    @Test
    fun `duration event with an end after its start becomes a bar`() {
        val event = TimelineEvent(id = 1, occurredAt = 0L, endedAt = WEEK_WINDOW.durationMillis / 2)

        val marks = layoutRow(events = listOf(event), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        val bar = marks.single() as TimelineMark.Bar
        assertEquals(1L, bar.eventId)
        assertEquals(0f, bar.startXFraction)
        assertEquals(0.5f, bar.endXFraction)
    }

    @Test
    fun `duration event whose end equals its start is treated as a point`() {
        val event = TimelineEvent(id = 1, occurredAt = 0L, endedAt = 0L)

        val marks = layoutRow(events = listOf(event), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = false)

        assertTrue(marks.single() is TimelineMark.Dot)
    }

    @Test
    fun `intensity scales dot size between min and max`() {
        val low = TimelineEvent(id = 1, occurredAt = 0L, intensity = MIN_INTENSITY)
        val high = TimelineEvent(id = 2, occurredAt = WEEK_WINDOW.endMillis, intensity = MAX_INTENSITY)

        val marks = layoutRow(events = listOf(low, high), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = true)

        val dots = marks.filterIsInstance<TimelineMark.Dot>().associateBy { it.eventIds.single() }
        assertEquals(MIN_DOT_SIZE_FRACTION, dots.getValue(1L).sizeFraction)
        assertEquals(MAX_DOT_SIZE_FRACTION, dots.getValue(2L).sizeFraction)
    }

    @Test
    fun `intensity enabled but event has no intensity falls back to bucket size rule`() {
        val event = TimelineEvent(id = 1, occurredAt = 0L, intensity = null)

        val marks = layoutRow(events = listOf(event), window = WEEK_WINDOW, slotCount = 10, intensityEnabled = true)

        val dot = marks.single() as TimelineMark.Dot
        assertEquals(MIN_DOT_SIZE_FRACTION, dot.sizeFraction)
    }

    @Test
    fun `zoom level nearestTo picks the closest preset`() {
        assertEquals(ZoomLevel.WEEK, ZoomLevel.nearestTo(ZoomLevel.WEEK.durationMillis))
        assertEquals(ZoomLevel.YEAR, ZoomLevel.nearestTo(ZoomLevel.YEAR.durationMillis * 2))

        val midpoint = (ZoomLevel.MONTH.durationMillis + ZoomLevel.THREE_MONTH.durationMillis) / 2
        assertEquals(ZoomLevel.THREE_MONTH, ZoomLevel.nearestTo(midpoint + 1))
    }
}
