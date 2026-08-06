package com.secondmonday.hodith.ui.theme

import com.secondmonday.hodith.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards against a copy-paste mistake in [cardDecorationStyle]'s mapping, same failure mode
 * [BigPictureDecorationTest] guards against for the Big Picture cell-style picker.
 */
class CardDecorationStyleTest {
    @Test
    fun `every theme maps to a distinct decoration style`() {
        val styles = AppTheme.entries.map { cardDecorationStyle(it) }
        assertEquals("expected one distinct style per theme, found a duplicate mapping", styles.size, styles.toSet().size)
    }

    @Test
    fun `plain, intense, and bright map to their own-named style`() {
        assertEquals(CardDecorationStyle.PLAIN, cardDecorationStyle(AppTheme.PLAIN))
        assertEquals(CardDecorationStyle.INTENSE, cardDecorationStyle(AppTheme.INTENSE))
        assertEquals(CardDecorationStyle.BRIGHT, cardDecorationStyle(AppTheme.BRIGHT))
    }
}
