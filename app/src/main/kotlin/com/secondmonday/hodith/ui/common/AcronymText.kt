package com.secondmonday.hodith.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Bolds and colors the first letter of each of [text]'s first six words. Used for the Home
 * header, where every theme's [com.secondmonday.hodith.ui.voice.Voice.homeHeaderTitle] phrasing
 * is a different wording of "how often does it truly happen", but all six words' first letters
 * still spell H-O-D-I-T-H, matching the app's own name.
 */
fun acronymHighlighted(
    text: String,
    markColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        var wordIndex = 0
        for (i in text.indices) {
            val isWordStart = i == 0 || text[i - 1] == ' '
            if (isWordStart && wordIndex < 6 && text[i].isLetter()) {
                withStyle(SpanStyle(color = markColor, fontWeight = FontWeight.Bold)) {
                    append(text[i])
                }
                wordIndex++
            } else {
                append(text[i])
            }
        }
    }
