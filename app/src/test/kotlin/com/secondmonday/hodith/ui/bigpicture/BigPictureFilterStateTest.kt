package com.secondmonday.hodith.ui.bigpicture

import com.secondmonday.hodith.data.zoneOffsetFromMinutes
import com.secondmonday.hodith.viewmodel.CalendarCase
import com.secondmonday.hodith.viewmodel.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BigPictureFilterStateTest {
    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `isPastOrToday is true for today itself`() {
        assertEquals(true, isPastOrToday(today, today))
    }

    @Test
    fun `isPastOrToday is true for a date before today`() {
        assertEquals(true, isPastOrToday(today.minusDays(1), today))
    }

    @Test
    fun `isPastOrToday is false for a date after today`() {
        assertEquals(false, isPastOrToday(today.plusDays(1), today))
    }

    @Test
    fun `isTagVisible shows every event when all tags are selected`() {
        assertEquals(true, isTagVisible(eventTags = listOf("work"), visibleTagNames = setOf("work"), allTagCount = 1))
        assertEquals(true, isTagVisible(eventTags = emptyList(), visibleTagNames = setOf("work"), allTagCount = 1))
    }

    @Test
    fun `isTagVisible shows only untagged events when zero tags are selected`() {
        assertEquals(true, isTagVisible(eventTags = emptyList(), visibleTagNames = emptySet(), allTagCount = 2))
        assertEquals(false, isTagVisible(eventTags = listOf("work"), visibleTagNames = emptySet(), allTagCount = 2))
    }

    @Test
    fun `isTagVisible with a partial selection matches only events carrying a selected tag`() {
        assertEquals(
            true,
            isTagVisible(eventTags = listOf("work"), visibleTagNames = setOf("work"), allTagCount = 2),
        )
        assertEquals(
            false,
            isTagVisible(eventTags = listOf("weekend"), visibleTagNames = setOf("work"), allTagCount = 2),
        )
    }

    @Test
    fun `isTagVisible with a partial selection still hides untagged events`() {
        assertEquals(
            false,
            isTagVisible(eventTags = emptyList(), visibleTagNames = setOf("work"), allTagCount = 2),
        )
    }

    private val coffee = CalendarCase(id = 1L, icon = "☕", name = "Coffee")
    private val tea = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
    private val allCases = listOf(coffee, tea)

    @Test
    fun `bigPictureCaseLegend is AllSelected when every case is selected`() {
        assertEquals(CaseLegendState.AllSelected, bigPictureCaseLegend(allCases, setOf(1L, 2L)))
    }

    @Test
    fun `bigPictureCaseLegend is NoneSelected when zero cases are selected`() {
        assertEquals(CaseLegendState.NoneSelected, bigPictureCaseLegend(allCases, emptySet()))
    }

    @Test
    fun `bigPictureCaseLegend itemizes a partial selection, preserving case order`() {
        assertEquals(CaseLegendState.Some(listOf(coffee)), bigPictureCaseLegend(allCases, setOf(1L)))
    }

    private val allTags = listOf("work", "weekend")

    @Test
    fun `bigPictureTagLegend is AllSelected when every tag is selected`() {
        assertEquals(TagLegendState.AllSelected, bigPictureTagLegend(allTags, setOf("work", "weekend")))
    }

    @Test
    fun `bigPictureTagLegend is UntaggedOnly when zero tags are selected`() {
        assertEquals(TagLegendState.UntaggedOnly, bigPictureTagLegend(allTags, emptySet()))
    }

    @Test
    fun `bigPictureTagLegend itemizes a partial selection, preserving tag order`() {
        assertEquals(TagLegendState.Some(listOf("work")), bigPictureTagLegend(allTags, setOf("work")))
    }

    @Test
    fun `bigPictureTagLegend is AllSelected, never UntaggedOnly, when no tags exist at all`() {
        assertEquals(TagLegendState.AllSelected, bigPictureTagLegend(emptyList(), emptySet()))
    }

    @Test
    fun `bigPictureLegendFits is true for no labels at all`() {
        assertTrue(bigPictureLegendFits(emptyList(), emptyList(), budget = 0))
    }

    @Test
    fun `bigPictureLegendFits is true right at the budget boundary, false one over it`() {
        val cost = "Coffee".length + LEGEND_CHIP_CHROME_CHARS
        assertTrue(bigPictureLegendFits(listOf("Coffee"), emptyList(), budget = cost))
        assertFalse(bigPictureLegendFits(listOf("Coffee"), emptyList(), budget = cost - 1))
    }

    @Test
    fun `bigPictureLegendFits counts case and tag labels against one shared budget`() {
        val caseCost = "Coffee".length + LEGEND_CHIP_CHROME_CHARS
        val tagCost = "weekend".length + LEGEND_CHIP_CHROME_CHARS
        assertTrue(bigPictureLegendFits(listOf("Coffee"), listOf("weekend"), budget = caseCost + tagCost))
        assertFalse(bigPictureLegendFits(listOf("Coffee"), listOf("weekend"), budget = caseCost + tagCost - 1))
    }

    @Test
    fun `bigPictureLegendFits is false once a long partial selection exceeds the real budget`() {
        val manySelectedCases = (1..10).map { "A fairly long case name number $it" }
        assertFalse(bigPictureLegendFits(manySelectedCases, emptyList(), budget = LEGEND_VISIBLE_CHAR_BUDGET))
    }

    @Test
    fun `bigPictureLegendFits is true for a short partial selection at the real default budget`() {
        assertTrue(bigPictureLegendFits(listOf("Coffee"), listOf("weekend")))
    }

    private val jan2026 = YearMonth.of(2026, 1)
    private val mar2026 = YearMonth.of(2026, 3)
    private val dec2025 = YearMonth.of(2025, 12)
    private val ascendingMonths = listOf(dec2025, jan2026, YearMonth.of(2026, 2), mar2026)

    @Test
    fun `monthsNewestFirst reverses an ascending list`() {
        assertEquals(listOf(mar2026, YearMonth.of(2026, 2), jan2026, dec2025), monthsNewestFirst(ascendingMonths))
    }

    @Test
    fun `bigPictureYearFilterVisible is false when earliest and current month share a year`() {
        assertEquals(false, bigPictureYearFilterVisible(jan2026, mar2026))
    }

    @Test
    fun `bigPictureYearFilterVisible is true when earliest and current month fall in different years`() {
        assertEquals(true, bigPictureYearFilterVisible(dec2025, mar2026))
    }

    @Test
    fun `bigPictureYearOptions lists distinct years, current year first`() {
        assertEquals(listOf(2026, 2025), bigPictureYearOptions(ascendingMonths))
    }

    @Test
    fun `filterMonthsByYear with a null year returns every month unchanged`() {
        assertEquals(ascendingMonths, filterMonthsByYear(ascendingMonths, null))
    }

    @Test
    fun `filterMonthsByYear narrows to only the given year`() {
        assertEquals(listOf(dec2025), filterMonthsByYear(ascendingMonths, 2025))
    }

    @Test
    fun `filterMonthsByYear narrows to a year with several months`() {
        assertEquals(listOf(jan2026, YearMonth.of(2026, 2), mar2026), filterMonthsByYear(ascendingMonths, 2026))
    }

    @Test
    fun `filterMonthsByYear returns an empty list for a year not present in the data`() {
        assertEquals(emptyList<YearMonth>(), filterMonthsByYear(ascendingMonths, 2024))
    }

    @Test
    fun `filterMonthsByYear returns an empty list unchanged, with or without a year`() {
        assertEquals(emptyList<YearMonth>(), filterMonthsByYear(emptyList(), null))
        assertEquals(emptyList<YearMonth>(), filterMonthsByYear(emptyList(), 2026))
    }

    @Test
    fun `bigPictureYearOptions is empty for an empty months list`() {
        assertEquals(emptyList<Int>(), bigPictureYearOptions(emptyList()))
    }

    @Test
    fun `monthsNewestFirst is empty for an empty months list`() {
        assertEquals(emptyList<YearMonth>(), monthsNewestFirst(emptyList()))
    }

    @Test
    fun `bigPictureYearOptions returns a single year when every month falls in it`() {
        assertEquals(listOf(2026), bigPictureYearOptions(listOf(jan2026, YearMonth.of(2026, 2), mar2026)))
    }

    @Test
    fun `bigPictureYearFilterVisible is false when earliest and current month are the same month`() {
        assertEquals(false, bigPictureYearFilterVisible(mar2026, mar2026))
    }

    @Test
    fun `normalizeVisibleSelection collapses a full selection to null`() {
        assertNull(normalizeVisibleSelection(setOf(1L, 2L), setOf(1L, 2L)))
    }

    @Test
    fun `normalizeVisibleSelection leaves a partial selection unchanged`() {
        assertEquals(setOf(1L), normalizeVisibleSelection(setOf(1L), setOf(1L, 2L)))
    }

    @Test
    fun `normalizeVisibleSelection leaves an empty selection unchanged, distinct from null`() {
        assertEquals(emptySet<Long>(), normalizeVisibleSelection(emptySet(), setOf(1L, 2L)))
    }

    @Test
    fun `resolveVisibleSelection returns everything for a null stored value`() {
        assertEquals(setOf(1L, 2L), resolveVisibleSelection(null, setOf(1L, 2L)))
    }

    @Test
    fun `resolveVisibleSelection intersects a stored value, dropping stale entries`() {
        assertEquals(setOf(1L), resolveVisibleSelection(setOf(1L, 99L), setOf(1L, 2L)))
    }

    @Test
    fun `resolveVisibleSelection returns a real empty set unchanged, not everything`() {
        assertEquals(emptySet<Long>(), resolveVisibleSelection(emptySet(), setOf(1L, 2L)))
    }

    @Test
    fun `isAllSelected is true only when every item is selected`() {
        assertTrue(isAllSelected(setOf("a", "b"), listOf("a", "b")))
        assertFalse(isAllSelected(setOf("a"), listOf("a", "b")))
    }

    @Test
    fun `isAllSelected ignores a stale selected id that no longer exists`() {
        // Same size as the full list, but "b" is missing, so it is not all selected.
        assertFalse(isAllSelected(setOf("a", "stale"), listOf("a", "b")))
    }

    @Test
    fun `isAllSelected is vacuously true for an empty list`() {
        assertTrue(isAllSelected(emptySet<String>(), emptyList()))
    }

    @Test
    fun `caseFilterChange always resets the tag filter to null`() {
        assertNull(caseFilterChange(setOf(1L), setOf(1L, 2L)).visibleTagNames)
        assertNull(caseFilterChange(setOf(1L, 2L), setOf(1L, 2L)).visibleTagNames)
    }

    @Test
    fun `caseFilterChange normalizes a full case selection to null and keeps a partial one`() {
        assertNull(caseFilterChange(setOf(1L, 2L), setOf(1L, 2L)).visibleCaseIds)
        assertEquals(setOf(1L), caseFilterChange(setOf(1L), setOf(1L, 2L)).visibleCaseIds)
    }

    private fun event(
        caseId: Long,
        tags: List<String>,
    ) = CalendarEvent(id = 1L, caseId = caseId, occurredAt = 0L, tags = tags)

    @Test
    fun `bigPictureAllTagNames scopes to visible cases, dedupes, and sorts`() {
        val events =
            listOf(
                event(caseId = 1L, tags = listOf("weekend", "work")),
                event(caseId = 1L, tags = listOf("work")),
                event(caseId = 2L, tags = listOf("hidden")),
            )
        assertEquals(listOf("weekend", "work"), bigPictureAllTagNames(events, visibleCaseIds = setOf(1L)))
    }

    @Test
    fun `bigPictureAllTagNames is empty when no visible case has events`() {
        val events = listOf(event(caseId = 2L, tags = listOf("hidden")))
        assertEquals(emptyList<String>(), bigPictureAllTagNames(events, visibleCaseIds = setOf(1L)))
    }

    private fun dayEvent(
        id: Long,
        occurredAt: Long,
        endedAt: Long? = null,
        isSpanStart: Boolean = false,
        isSpanEnd: Boolean = false,
        isAllDay: Boolean = false,
    ) = DayEvent(
        event = CalendarEvent(id = id, caseId = 1L, occurredAt = occurredAt, endedAt = endedAt),
        isSpanStart = isSpanStart,
        isSpanEnd = isSpanEnd,
        isAllDay = isAllDay,
    )

    @Test
    fun `sortDayEvents pins all-day events ahead of timed ones regardless of timestamp`() {
        val allDay = dayEvent(id = 1L, occurredAt = 9_000L, isAllDay = true)
        val timed = dayEvent(id = 2L, occurredAt = 1_000L)
        assertEquals(listOf(1L, 2L), sortDayEvents(listOf(timed, allDay)).map { it.event.id })
    }

    @Test
    fun `sortDayEvents sorts a span-start event by its own start time among timed events`() {
        val startsLater = dayEvent(id = 1L, occurredAt = 5_000L, endedAt = 9_000L, isSpanStart = true)
        val plainEarlier = dayEvent(id = 2L, occurredAt = 1_000L)
        assertEquals(listOf(2L, 1L), sortDayEvents(listOf(startsLater, plainEarlier)).map { it.event.id })
    }

    @Test
    fun `sortDayEvents sorts a span-end event by its end time, not its original start time`() {
        // Started way before today but ends today at 2_000 — must sort by the end time, not occurredAt.
        val endsEarly = dayEvent(id = 1L, occurredAt = 0L, endedAt = 2_000L, isSpanEnd = true)
        val plainLater = dayEvent(id = 2L, occurredAt = 5_000L)
        assertEquals(listOf(1L, 2L), sortDayEvents(listOf(plainLater, endsEarly)).map { it.event.id })
    }

    @Test
    fun `sortDayEvents keeps plain point events chronological by occurredAt`() {
        val events =
            listOf(
                dayEvent(id = 3L, occurredAt = 3_000L),
                dayEvent(id = 1L, occurredAt = 1_000L),
                dayEvent(id = 2L, occurredAt = 2_000L),
            )
        assertEquals(listOf(1L, 2L, 3L), sortDayEvents(events).map { it.event.id })
    }

    @Test
    fun `sortDayEvents keeps multiple all-day events together, ahead of every timed one`() {
        val allDayA = dayEvent(id = 1L, occurredAt = 9_000L, isAllDay = true)
        val allDayB = dayEvent(id = 2L, occurredAt = 1_000L, isAllDay = true)
        val timed = dayEvent(id = 3L, occurredAt = 500L)
        assertEquals(listOf(2L, 1L, 3L), sortDayEvents(listOf(timed, allDayA, allDayB)).map { it.event.id })
    }

    @Test
    fun `sortDayEvents interleaves a span-start and a span-end from different events by their real times`() {
        // Event 1 starts at 10_000 today (continues into tomorrow); event 2 ends at 5_000 today
        // (started days ago) -- the end-day event's boundary is earlier today, so it sorts first.
        val startsAt10 = dayEvent(id = 1L, occurredAt = 10_000L, endedAt = 99_000L, isSpanStart = true)
        val endsAt5 = dayEvent(id = 2L, occurredAt = 0L, endedAt = 5_000L, isSpanEnd = true)
        assertEquals(listOf(2L, 1L), sortDayEvents(listOf(startsAt10, endsAt5)).map { it.event.id })
    }

    @Test
    fun `sortDayEvents is stable for events at the identical boundary time`() {
        val first = dayEvent(id = 1L, occurredAt = 5_000L)
        val second = dayEvent(id = 2L, occurredAt = 5_000L)
        assertEquals(listOf(1L, 2L), sortDayEvents(listOf(first, second)).map { it.event.id })
    }

    @Test
    fun `sortDayEvents on an empty list returns an empty list`() {
        assertEquals(emptyList<DayEvent>(), sortDayEvents(emptyList()))
    }

    private fun millisAt(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
        offsetMinutes: Int = 0,
    ): Long = date.atTime(hour, minute).toInstant(zoneOffsetFromMinutes(offsetMinutes)).toEpochMilli()

    @Test
    fun `coveredDates returns a single date for a point event`() {
        val point = CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 9))
        assertEquals(listOf(today), coveredDates(point, today))
    }

    @Test
    fun `coveredDates returns a single date for a same-day duration event, however many hours long`() {
        val sameDay =
            CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 9), endedAt = millisAt(today, 22))
        assertEquals(listOf(today), coveredDates(sameDay, today))
    }

    @Test
    fun `coveredDates spans every calendar day a finished event crosses midnight on`() {
        val span =
            CalendarEvent(
                id = 1L,
                caseId = 1L,
                occurredAt = millisAt(today, 23, 30),
                endedAt = millisAt(today.plusDays(1), 0, 30),
            )
        assertEquals(listOf(today, today.plusDays(1)), coveredDates(span, today))
    }

    @Test
    fun `coveredDates spans a year boundary correctly`() {
        val newYearsEve = LocalDate.of(2025, 12, 30)
        val span =
            CalendarEvent(
                id = 1L,
                caseId = 1L,
                occurredAt = millisAt(newYearsEve, 9),
                endedAt = millisAt(LocalDate.of(2026, 1, 2), 9),
            )
        assertEquals(
            listOf(LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)),
            coveredDates(span, today = LocalDate.of(2026, 1, 2)),
        )
    }

    @Test
    fun `coveredDates for a still-running event spans from its start date through today`() {
        val ongoing = CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today.minusDays(2), 9), isOngoing = true)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), coveredDates(ongoing, today))
    }

    @Test
    fun `coveredDates for a still-running event started today is just today`() {
        val ongoing = CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 9), isOngoing = true)
        assertEquals(listOf(today), coveredDates(ongoing, today))
    }

    @Test
    fun `coveredDates resolves dates in the event's own captured offset, not UTC`() {
        // 23:30 UTC on Jan 1 is 09:30 the next local day at UTC+10 -- a traveler's event must land
        // on the day it happened in their own timezone, not whatever day the raw instant falls on.
        val lateUtc =
            CalendarEvent(
                id = 1L,
                caseId = 1L,
                occurredAt = millisAt(LocalDate.of(2026, 1, 1), 23, 30, offsetMinutes = 0),
                utcOffsetMinutes = 600,
            )
        assertEquals(listOf(LocalDate.of(2026, 1, 2)), coveredDates(lateUtc, today = LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `buildEventsByDay does not flag a same-day duration event as a span`() {
        val sameDay =
            CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 9), endedAt = millisAt(today, 22))
        val byDay = buildEventsByDay(listOf(sameDay), today)
        assertEquals(listOf(today), byDay.keys.toList())
        val dayEvent = byDay[today]!!.single()
        assertFalse(dayEvent.isSpanStart)
        assertFalse(dayEvent.isSpanEnd)
        assertFalse(dayEvent.isAllDay)
    }

    @Test
    fun `buildEventsByDay flags a 2-day span's start and end days with no middle isAllDay day`() {
        val span =
            CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 23), endedAt = millisAt(today.plusDays(1), 1))
        val byDay = buildEventsByDay(listOf(span), today)
        assertEquals(setOf(today, today.plusDays(1)), byDay.keys)

        val startDayEvent = byDay[today]!!.single()
        assertTrue(startDayEvent.isSpanStart)
        assertFalse(startDayEvent.isSpanEnd)
        assertFalse(startDayEvent.isAllDay)

        val endDayEvent = byDay[today.plusDays(1)]!!.single()
        assertFalse(endDayEvent.isSpanStart)
        assertTrue(endDayEvent.isSpanEnd)
        assertFalse(endDayEvent.isAllDay)
    }

    @Test
    fun `buildEventsByDay flags only the middle days of a longer span as isAllDay`() {
        val span =
            CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 9), endedAt = millisAt(today.plusDays(3), 17))
        val byDay = buildEventsByDay(listOf(span), today)

        assertTrue(byDay[today]!!.single().isSpanStart)
        assertTrue(byDay[today.plusDays(1)]!!.single().isAllDay)
        assertTrue(byDay[today.plusDays(2)]!!.single().isAllDay)
        assertTrue(byDay[today.plusDays(3)]!!.single().isSpanEnd)
    }

    @Test
    fun `buildEventsByDay keeps a still-running span's start day as isSpanStart but every carried day isAllDay, never isSpanEnd`() {
        val ongoing = CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today.minusDays(2), 9), isOngoing = true)
        val byDay = buildEventsByDay(listOf(ongoing), today)

        val startDayEvent = byDay[today.minusDays(2)]!!.single()
        assertTrue(startDayEvent.isSpanStart)
        assertFalse(startDayEvent.isAllDay)

        val carriedYesterday = byDay[today.minusDays(1)]!!.single()
        assertTrue(carriedYesterday.isAllDay)
        assertFalse(carriedYesterday.isSpanEnd)

        // Even on the real "today" cell, a still-running event never gets isSpanEnd -- it hasn't
        // actually ended, so it stays pinned as all-day rather than claiming a false end boundary.
        val todayEvent = byDay[today]!!.single()
        assertTrue(todayEvent.isAllDay)
        assertFalse(todayEvent.isSpanEnd)
    }

    @Test
    fun `buildEventsByDay treats a still-running event started today as a plain timed row, not all-day`() {
        val ongoing = CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today, 9), isOngoing = true)
        val byDay = buildEventsByDay(listOf(ongoing), today)
        assertEquals(listOf(today), byDay.keys.toList())
        val dayEvent = byDay[today]!!.single()
        assertFalse(dayEvent.isSpanStart)
        assertFalse(dayEvent.isSpanEnd)
        assertFalse(dayEvent.isAllDay)
    }

    @Test
    fun `buildEventsByDay orders a mixed day by real boundary time, all-day pinned first`() {
        val carriedAllDay = CalendarEvent(id = 1L, caseId = 1L, occurredAt = millisAt(today.minusDays(5), 9), isOngoing = true)
        val startsAt8 = CalendarEvent(id = 2L, caseId = 1L, occurredAt = millisAt(today, 8), endedAt = millisAt(today.plusDays(2), 9))
        val endsAt9 = CalendarEvent(id = 3L, caseId = 1L, occurredAt = millisAt(today.minusDays(3), 9), endedAt = millisAt(today, 9))
        val plainAt10 = CalendarEvent(id = 4L, caseId = 1L, occurredAt = millisAt(today, 10))

        val byDay = buildEventsByDay(listOf(carriedAllDay, startsAt8, endsAt9, plainAt10), today)

        assertEquals(listOf(1L, 2L, 3L, 4L), byDay[today]!!.map { it.event.id })
    }

    @Test
    fun `buildEventsByDay returns an empty map for an empty events list`() {
        assertEquals(emptyMap<LocalDate, List<DayEvent>>(), buildEventsByDay(emptyList(), today))
    }
}
