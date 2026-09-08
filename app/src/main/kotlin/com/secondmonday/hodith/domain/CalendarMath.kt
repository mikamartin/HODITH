package com.secondmonday.hodith.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val fromDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
    val toDate = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(fromDate, toDate)
}
