package com.secondmonday.hodith.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

/**
 * Every calendar day the inclusive span [[startMillis], [endMillis]] touches in [zone], from the
 * start's local date through the end's local date. A same-day span yields one date; an [endMillis]
 * before [startMillis] is floored to the start day. This is spec §9's "active span": for day-counting
 * visuals a duration event covers every day it was active, not just the day it started. Shared by the
 * per-case Insights heatmap and streak count.
 */
internal fun datesCovered(
    startMillis: Long,
    endMillis: Long,
    zone: ZoneId,
): List<LocalDate> {
    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(maxOf(startMillis, endMillis)).atZone(zone).toLocalDate()
    return generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
}
