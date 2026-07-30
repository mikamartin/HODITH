package com.secondmonday.hodith.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar period lengths in days, shared by domain math converting between per-day/week/month rates. */
internal const val DAYS_PER_WEEK = 7.0
internal const val DAYS_PER_MONTH = 30.0

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
