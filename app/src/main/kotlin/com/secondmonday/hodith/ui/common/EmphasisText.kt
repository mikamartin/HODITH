package com.secondmonday.hodith.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Parses `**term**` markers in [text] into spans tinted with [color], so info-dialog bodies can
 * call out a defined term without adding font weight (see [InfoDialog]). An odd number of `**`
 * markers is left as literal text rather than guessing at an unmatched pair.
 */
fun parseEmphasis(
    text: String,
    color: Color,
): AnnotatedString {
    val segments = text.split("**")
    if (segments.size % 2 == 0) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            if (index % 2 == 1) {
                withStyle(SpanStyle(color = color)) { append(segment) }
            } else {
                append(segment)
            }
        }
    }
}
