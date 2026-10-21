package com.secondmonday.hodith.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Calendar period lengths in days, shared by domain math converting between per-day/week/month rates. */
internal const val DAYS_PER_WEEK = 7.0
internal const val DAYS_PER_MONTH = 30.0

/**
 * A "3 months" period, as a fixed 90-day span — deliberately the round `3 × DAYS_PER_MONTH`
 * approximation, not a calendar quarter, matching [DAYS_PER_MONTH]'s own approximation. Backs
 * `ExpectedPer.QUARTER` normalization and the rolling `ObservationWindow.LAST_3_MONTHS` start.
 */
internal const val DAYS_PER_QUARTER = 90.0

/** Calendar-date day difference — not raw millis ÷ a fixed day length, which undercounts across a DST transition. */
internal fun daysBetween(
    fromMillis: Long,
    toMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val fromDate = Instant.fromEpochMilliseconds(fromMillis).toLocalDateTime(zone).date
    val toDate = Instant.fromEpochMilliseconds(toMillis).toLocalDateTime(zone).date
    return fromDate.daysUntil(toDate).toLong()
}

/**
 * `TemporalAdjusters.previousOrSame`/`nextOrSame` replacement — kotlinx-datetime ships no adjuster
 * API, so this is hand-rolled via `isoDayNumber` (1=Monday..7=Sunday). Shared by `CalendarGrid`'s
 * month-grid math, `HomeViewModel`'s week-boundary math, and `DemoDataSeeder`'s weekend showcase.
 */
internal fun LocalDate.previousOrSame(target: DayOfWeek): LocalDate =
    minus((dayOfWeek.isoDayNumber - target.isoDayNumber).mod(7), DateTimeUnit.DAY)

internal fun LocalDate.nextOrSame(target: DayOfWeek): LocalDate =
    plus((target.isoDayNumber - dayOfWeek.isoDayNumber).mod(7), DateTimeUnit.DAY)
