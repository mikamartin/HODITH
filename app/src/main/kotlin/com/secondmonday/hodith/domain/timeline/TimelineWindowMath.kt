package com.secondmonday.hodith.domain.timeline

/**
 * Computes the [TimeWindow] that results from one frame of a pan/zoom gesture. Pure Kotlin so the
 * anchoring/clamping math is unit-testable without a device — the UI layer only has to convert
 * pixel offsets to fractions of the row width before calling this.
 *
 * @param focalXFraction where the gesture's centroid sits across the row (0f-1f); the instant in
 *   time at that point stays fixed under the fingers as the window zooms.
 * @param panXFraction the frame's pan delta as a fraction of row width.
 * @param zoomChange the frame's zoom multiplier (>1 = fingers spreading apart = zooming in).
 */
fun nextWindow(
    window: TimeWindow,
    focalXFraction: Float,
    panXFraction: Float,
    zoomChange: Float,
    minDurationMillis: Long = ZoomLevel.WEEK.durationMillis,
    maxDurationMillis: Long = ZoomLevel.YEAR.durationMillis,
): TimeWindow {
    val focalMillis = window.startMillis + (focalXFraction * window.durationMillis).toLong()
    val newDuration = (window.durationMillis / zoomChange).toLong().coerceIn(minDurationMillis, maxDurationMillis)
    val zoomedStart = focalMillis - (focalXFraction * newDuration).toLong()
    val panMillis = (panXFraction * newDuration).toLong()
    val newStart = zoomedStart - panMillis
    return TimeWindow(newStart, newStart + newDuration)
}

/** Re-centers a window on its current midpoint at a new duration — used when snapping zoom to a [ZoomLevel] preset. */
fun TimeWindow.withDuration(newDurationMillis: Long): TimeWindow {
    val centerMillis = startMillis + durationMillis / 2
    val halfDuration = newDurationMillis / 2
    return TimeWindow(centerMillis - halfDuration, centerMillis - halfDuration + newDurationMillis)
}
