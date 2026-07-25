package com.secondmonday.hodith.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * A Monday-start, Sunday-end grid of weeks covering [month], padded with the neighbouring
 * months' leading/trailing days so every week is a full 7 days (spec §9). Shared by Big Picture's
 * multi-case grid and a single Case's calendar heatmap — both lay out days the same way, only the
 * cell content differs.
 */
internal fun weeksInGrid(month: YearMonth): List<List<LocalDate>> {
    val firstOfMonth = month.atDay(1)
    val lastOfMonth = month.atEndOfMonth()
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    val days = generateSequence(gridStart) { it.plusDays(1) }.takeWhile { !it.isAfter(gridEnd) }.toList()
    return days.chunked(7)
}
