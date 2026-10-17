package com.secondmonday.hodith.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** No seconds: precise enough to tell apart same-day exports without a needlessly long filename. Shared by every export file name - JSON/CSV backups and PDF records alike (feature/pdf-export) - so they all sort and read the same way. */
private val EXPORT_FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

fun exportFileTimestamp(
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(nowMillis).atZone(zone).format(EXPORT_FILE_TIMESTAMP_FORMATTER)
