package com.secondmonday.hodith.ui.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class EmphasisTextTest {
    private val markColor = Color(0xFFFF0000)

    @Test
    fun `plain text with no markers produces no spans`() {
        val result = parseEmphasis("Each tag counts its own events.", markColor)

        assertEquals("Each tag counts its own events.", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `a single marked term becomes one colored span with the markers stripped`() {
        val result = parseEmphasis("**One tap** logs an event instantly.", markColor)

        assertEquals("One tap logs an event instantly.", result.text)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(markColor, span.item.color)
        assertEquals("One tap", result.text.substring(span.start, span.end))
    }

    @Test
    fun `multiple marked terms each become their own span in order`() {
        val result = parseEmphasis("**None** skips it. **Manual** lets you type it.", markColor)

        assertEquals("None skips it. Manual lets you type it.", result.text)
        assertEquals(2, result.spanStyles.size)
        val spans = result.spanStyles.sortedBy { it.start }
        assertEquals("None", result.text.substring(spans[0].start, spans[0].end))
        assertEquals("Manual", result.text.substring(spans[1].start, spans[1].end))
    }

    @Test
    fun `an unmatched marker is left as literal text with no span`() {
        val text = "This has just **one marker."
        val result = parseEmphasis(text, markColor)

        assertEquals(text, result.text)
        assertEquals(0, result.spanStyles.size)
    }
}
