package com.secondmonday.hodith.ui.bigpicture

import com.secondmonday.hodith.viewmodel.CalendarCase
import org.junit.Assert.assertEquals
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
}
