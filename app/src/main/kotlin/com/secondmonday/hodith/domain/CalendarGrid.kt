package com.secondmonday.hodith.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * A Monday-start, Sunday-end grid of weeks covering [month], padded with the neighbouring
 * months' leading/trailing days so every week is a full 7 days (spec §9). Shared by Big Picture's
 * multi-case grid and a single Case's calendar heatmap — both lay out days the same way, only the
 * cell content differs.
 */
internal fun weeksInGrid(month: YearMonth): List<List<LocalDate>> {
    val firstOfMonth = month.atDay(1)
    val lastOfMonth = month.atEndOfMonth()
    val gridStart = firstOfMonth.previousOrSame(DayOfWeek.MONDAY)
    val gridEnd = lastOfMonth.nextOrSame(DayOfWeek.SUNDAY)

    val days = generateSequence(gridStart) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= gridEnd }.toList()
    return days.chunked(7)
}

/**
 * Every calendar day the inclusive span [[startMillis], [endMillis]] touches, from the start's
 * local date (in [zone]) through the end's local date (in [endZone], defaulting to [zone]). A
 * same-day span yields one date; an [endMillis] before [startMillis] is floored to the start day.
 * [endZone] exists for a still-running span: its caller passes the live current zone there instead
 * of the event's own (possibly stale, pre-travel) captured offset, so "today" always means today
 * where the device actually is right now. This is spec §9's "active span": for day-counting visuals
 * a duration event covers every day it was active, not just the day it started. Shared by the
 * per-case Insights heatmap and streak count.
 */
internal fun datesCovered(
    startMillis: Long,
    endMillis: Long,
    zone: TimeZone,
    endZone: TimeZone = zone,
): List<LocalDate> {
    val startDate = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(zone).date
    val endDate =
        if (endMillis <= startMillis) {
            startDate
        } else {
            Instant.fromEpochMilliseconds(endMillis).toLocalDateTime(endZone).date
        }
    return generateSequence(startDate) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= endDate }.toList()
}

/**
 * Whether the active span [[startMillis], [endMillis]] touches more than one calendar day — i.e.
 * [datesCovered] would return more than one date. See [datesCovered] for [endZone]'s purpose. Drives
 * the Big Picture's spanned treatment and the Insights tab's frequency-hide / rhythm-relabel: a
 * same-day duration event reads exactly like a moment event everywhere.
 */
internal fun spansMultipleDays(
    startMillis: Long,
    endMillis: Long,
    zone: TimeZone,
    endZone: TimeZone = zone,
): Boolean {
    if (endMillis <= startMillis) return false
    val startDate = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(zone).date
    val endDate = Instant.fromEpochMilliseconds(endMillis).toLocalDateTime(endZone).date
    return endDate > startDate
}
