package com.secondmonday.hodith.testsupport

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The zone JVM fixtures resolve against — the system default, matching production's
 * `Clock`-injected `TimeZone.currentSystemDefault()` default. Shared so every day-math test agrees
 * on one anchor instead of each file re-declaring its own `private val ZONE`.
 */
val TEST_ZONE: TimeZone = TimeZone.currentSystemDefault()

/** Epoch millis at the start of [epochDay] (days since 1970-01-01) in [zone]. */
fun millisAtDay(
    epochDay: Long,
    zone: TimeZone = TEST_ZONE,
): Long =
    LocalDate
        .fromEpochDays(epochDay.toInt())
        .atStartOfDayIn(zone)
        .toEpochMilliseconds()

/** Epoch millis at [hour]:00 on [epochDay] in [zone]. */
fun millisAt(
    epochDay: Long,
    hour: Int,
    zone: TimeZone = TEST_ZONE,
): Long =
    LocalDate
        .fromEpochDays(epochDay.toInt())
        .atTime(LocalTime(hour, 0))
        .toInstant(zone)
        .toEpochMilliseconds()
