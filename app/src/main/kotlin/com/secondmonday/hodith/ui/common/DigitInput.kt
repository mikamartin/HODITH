package com.secondmonday.hodith.ui.common

/**
 * Strips non-digit characters from a text field's typed value and caps its length, so a plain
 * `toIntOrNull()` downstream can never silently overflow to null. [maxDigits] should be sized to
 * the field's actual domain — a duration field and a small day-count field don't need the same
 * cap.
 */
fun filterDigitInput(
    value: String,
    maxDigits: Int,
): String = value.filter(Char::isDigit).take(maxDigits)
