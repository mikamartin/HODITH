package com.secondmonday.hodith.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/** No seconds: precise enough to tell apart same-day exports without a needlessly long filename. Shared by every export file name - JSON/CSV backups and PDF records alike (feature/pdf-export) - so they all sort and read the same way. */
private val EXPORT_FILE_TIMESTAMP_FORMAT =
    LocalDateTime.Format {
        year()
        monthNumber()
        day()
        char('-')
        hour()
        minute()
    }

fun exportFileTimestamp(
    nowMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = EXPORT_FILE_TIMESTAMP_FORMAT.format(Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone))
