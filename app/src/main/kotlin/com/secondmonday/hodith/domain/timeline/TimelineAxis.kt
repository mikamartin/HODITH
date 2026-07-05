package com.secondmonday.hodith.domain.timeline

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TICK_COUNT = 5

/**
 * Evenly spaced timestamps across [window], first at the start and last at the end — matched to
 * a [Row] using `Arrangement.SpaceBetween` in the UI layer so tick labels land under the same
 * fractional x-positions as [TimelineMark] dots, with no pixel math needed on either side.
 */
fun axisTickMillis(window: TimeWindow): List<Long> =
    (0 until TICK_COUNT).map { index ->
        window.startMillis + (window.durationMillis * index / (TICK_COUNT - 1))
    }

/**
 * Formats one axis tick, choosing precision from [zoomLevel] — day-of-week at week zoom, down to
 * bare month once a tick can represent a multi-week span at year zoom.
 */
fun axisTickLabel(
    millis: Long,
    zoneId: ZoneId,
    zoomLevel: ZoomLevel,
): String {
    val pattern =
        when (zoomLevel) {
            ZoomLevel.WEEK -> "EEE d"
            ZoomLevel.MONTH, ZoomLevel.THREE_MONTH -> "MMM d"
            ZoomLevel.YEAR -> "MMM"
        }
    return Instant
        .ofEpochMilli(millis)
        .atZone(zoneId)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}
