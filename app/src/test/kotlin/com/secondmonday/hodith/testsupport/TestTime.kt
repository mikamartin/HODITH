package com.secondmonday.hodith.testsupport

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The zone JVM fixtures resolve against — the system default, matching production's
 * `Clock`-injected `ZoneId.systemDefault()` default. Shared so every day-math test agrees on
 * one anchor instead of each file re-declaring its own `private val ZONE`.
 */
val TEST_ZONE: ZoneId = ZoneId.systemDefault()

/** Epoch millis at the start of [epochDay] (days since 1970-01-01) in [zone]. */
fun millisAtDay(
    epochDay: Long,
    zone: ZoneId = TEST_ZONE,
): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

/** Epoch millis at [hour]:00 on [epochDay] in [zone]. */
fun millisAt(
    epochDay: Long,
    hour: Int,
    zone: ZoneId = TEST_ZONE,
): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atTime(LocalTime.of(hour, 0))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
