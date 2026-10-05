package com.secondmonday.hodith.viewmodel

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A figure for a tight space: one decimal place with a whole number's ".0" dropped ("2.3", "4"), and a
 * value of 10 or more rounded to a whole number ("12"). Shared by the average intensity and the Square
 * share card's rates, so a whole figure never reads as "3.0".
 */
internal fun formatCompactDecimal(value: Double): String {
    if (value >= COMPACT_DECIMAL_WHOLE_NUMBER_FROM) return value.roundToInt().toString()
    return String.format(Locale.US, "%.1f", value).removeSuffix(".0")
}

/** From this value up, [formatCompactDecimal] drops the decimal place entirely. */
private const val COMPACT_DECIMAL_WHOLE_NUMBER_FROM = 10.0

/** A day count in the share card's compact notation, e.g. "3.1d" or "9d". */
internal fun formatDaysCompact(days: Double): String = "${formatCompactDecimal(days)}d"
