package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

private fun eventAtDay(epochDay: Long) =
    EventEntity(
        id = 0,
        caseId = 1,
        occurredAt = millisAtDay(epochDay),
        endedAt = null,
        intensity = null,
        note = null,
        loggedAt = millisAtDay(epochDay),
    )

class InsightsEngineTest {
    // ---- computeTimelineWindow ----

    @Test
    fun `computeTimelineWindow keeps the default window when event count is under the dot cap`() {
        val today = 100L
        val events = listOf(eventAtDay(today - 30), eventAtDay(today - 10), eventAtDay(today))

        val result = computeTimelineWindow(events, now = millisAtDay(today))

        assertEquals(TIMELINE_DEFAULT_WINDOW_DAYS, result.windowDays)
        assertEquals(3, result.events.size)
    }

    @Test
    fun `computeTimelineWindow excludes events older than the default window`() {
        val today = 100L
        val events = listOf(eventAtDay(today - 40), eventAtDay(today - 10))

        val result = computeTimelineWindow(events, now = millisAtDay(today))

        assertEquals(1, result.events.size)
        assertEquals(millisAtDay(today - 10), result.events.single().occurredAt)
    }

    @Test
    fun `computeTimelineWindow shrinks around the most recent events when the default window is too dense`() {
        val today = 100L
        // One event a day for the last 30 days: 30 events fall in the 35-day default window,
        // more than TIMELINE_MAX_DOTS (24), so the window must shrink to bound just the cap.
        val events = (0 until 30).map { daysAgo -> eventAtDay(today - daysAgo) }

        val result = computeTimelineWindow(events, now = millisAtDay(today))

        assertEquals(TIMELINE_MAX_DOTS, result.events.size)
        assertEquals(TIMELINE_MAX_DOTS - 1L, result.windowDays)
        assertEquals(millisAtDay(today - (TIMELINE_MAX_DOTS - 1)), result.events.first().occurredAt)
    }

    @Test
    fun `computeTimelineWindow floors the shrunk window even when all capped events land on one day`() {
        val today = 100L
        val events = List(TIMELINE_MAX_DOTS + 5) { eventAtDay(today) }

        val result = computeTimelineWindow(events, now = millisAtDay(today))

        assertEquals(TIMELINE_MAX_DOTS, result.events.size)
        assertEquals(TIMELINE_MIN_WINDOW_DAYS, result.windowDays)
    }

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

    // ---- groupEventsByDay ----

    @Test
    fun `groupEventsByDay merges same-day events into a single group, counting them`() {
        val events = listOf(eventAtDay(5), eventAtDay(5), eventAtDay(5))

        val groups = groupEventsByDay(events)

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().count)
        assertEquals(millisAtDay(5), groups.single().representativeMillis)
    }

    @Test
    fun `groupEventsByDay returns one group per distinct day in ascending date order`() {
        val events = listOf(eventAtDay(8), eventAtDay(0), eventAtDay(0), eventAtDay(5))

        val groups = groupEventsByDay(events)

        assertEquals(listOf(0L, 5L, 8L), groups.map { it.date.toEpochDay() })
        assertEquals(listOf(2, 1, 1), groups.map { it.count })
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
    fun `heatmapLevelFor buckets ratios into the four shaded tiers`() {
        assertEquals(HeatmapLevel.L1, heatmapLevelFor(count = 1, maxCountInRange = 4))
        assertEquals(HeatmapLevel.L2, heatmapLevelFor(count = 2, maxCountInRange = 4))
        assertEquals(HeatmapLevel.L3, heatmapLevelFor(count = 3, maxCountInRange = 4))
        assertEquals(HeatmapLevel.L4, heatmapLevelFor(count = 4, maxCountInRange = 4))
    }
}
