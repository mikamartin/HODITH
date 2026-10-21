package com.secondmonday.hodith.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileTimestampTest {
    @Test
    fun `exportFileTimestamp formats as yyyyMMdd-HHmm, no seconds`() {
        val millis = LocalDateTime(2026, 3, 7, 14, 5, 30).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertEquals("20260307-1405", exportFileTimestamp(millis, TimeZone.UTC))
    }

    @Test
    fun `exportFileTimestamp zero-pads a single-digit month, day, hour and minute`() {
        val millis = LocalDateTime(2026, 1, 2, 3, 4, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertEquals("20260102-0304", exportFileTimestamp(millis, TimeZone.UTC))
    }

    @Test
    fun `exportFileTimestamp resolves in the given zone, not UTC`() {
        // 2026-03-07T23:30Z is already 2026-03-08 local under a +1h offset.
        val millis = LocalDateTime(2026, 3, 7, 23, 30, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertEquals("20260308-0030", exportFileTimestamp(millis, TimeZone.of("Europe/Paris")))
    }
}
