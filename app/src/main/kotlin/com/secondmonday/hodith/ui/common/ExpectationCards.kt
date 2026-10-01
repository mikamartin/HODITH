package com.secondmonday.hodith.ui.common

import com.secondmonday.hodith.domain.PRELIMINARY_MIN_DAYS
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_EVENTS

/**
 * How far toward the Preliminary bar an early-days Expectation's progress bar sits — whichever
 * of the observation-count or window-length requirement is furthest behind, since both must clear
 * together (spec §8). [observationCount] is the event count for an occurrence-count expectation
 * and the active-day count for a days-active one.
 */
internal fun expectationProgressFraction(
    observationCount: Int,
    windowDays: Long,
): Float {
    val countFraction = observationCount.toFloat() / PRELIMINARY_MIN_EVENTS
    val dayFraction = windowDays.toFloat() / PRELIMINARY_MIN_DAYS
    return minOf(countFraction, dayFraction).coerceIn(0f, 1f)
}
