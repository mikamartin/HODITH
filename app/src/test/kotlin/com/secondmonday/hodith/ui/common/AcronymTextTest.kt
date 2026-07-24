package com.secondmonday.hodith.ui.common

import androidx.compose.ui.graphics.Color
import com.secondmonday.hodith.ui.voice.BrightVoice
import com.secondmonday.hodith.ui.voice.IntenseVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcronymTextTest {
    private val markColor = Color(0xFFFF0000)

    @Test
    fun `bolds and colors the first letter of each of the first six words`() {
        val text = "How often does it truly happen?"
        val result = acronymHighlighted(text, markColor)

        assertEquals(text, result.text)
        val wordStarts = wordStartIndices(text).take(6)
        assertEquals(6, result.spanStyles.size)
        for (start in wordStarts) {
            val span = result.spanStyles.first { it.start == start && it.end == start + 1 }
            assertEquals(markColor, span.item.color)
            assertEquals(text[start].toString(), text.substring(span.start, span.end))
        }
    }

    @Test
    fun `does not mark a seventh word`() {
        val text = "How oft dares it truly haunt tonight?"
        val result = acronymHighlighted(text, markColor)

        val seventhWordStart = text.indexOf("tonight")
        assertTrue(result.spanStyles.none { it.start == seventhWordStart })
    }

    @Test
    fun `real voice phrasings each mark exactly six letters spelling HODITH`() {
        val phrasings = listOf(PlainVoice.homeHeaderTitle, IntenseVoice.homeHeaderTitle, BrightVoice.homeHeaderTitle)
        for (text in phrasings) {
            val result = acronymHighlighted(text, markColor)
            assertEquals(text, 6, result.spanStyles.size)
            val markedLetters =
                result.spanStyles
                    .sortedBy { it.start }
                    .map { text[it.start].uppercaseChar() }
                    .joinToString("")
            assertEquals(text, "HODITH", markedLetters)
        }
    }

    @Test
    fun `single word text marks only its first letter`() {
        val result = acronymHighlighted("Hodith", markColor)

        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles.first().start)
    }

    private fun wordStartIndices(text: String): List<Int> =
        text.indices.filter { i -> i == 0 || text[i - 1] == ' ' }.filter { text[it].isLetter() }
}
