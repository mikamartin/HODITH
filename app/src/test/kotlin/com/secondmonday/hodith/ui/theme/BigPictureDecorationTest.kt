package com.secondmonday.hodith.ui.theme

import com.secondmonday.hodith.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards against a copy-paste mistake in [bigPictureCellStyle]'s mapping, same failure mode
 * [HodithThemeTest] guards against for the color/type/shape pickers.
 */
class BigPictureDecorationTest {
    @Test
    fun `every theme maps to a distinct cell style`() {
        val styles = AppTheme.entries.map { bigPictureCellStyle(it) }
        assertEquals("expected one distinct style per theme, found a duplicate mapping", styles.size, styles.toSet().size)
    }

    @Test
    fun `plain, intense, and bright map to their own-named style`() {
        assertEquals(BigPictureCellStyle.PLAIN, bigPictureCellStyle(AppTheme.PLAIN))
        assertEquals(BigPictureCellStyle.INTENSE, bigPictureCellStyle(AppTheme.INTENSE))
        assertEquals(BigPictureCellStyle.BRIGHT, bigPictureCellStyle(AppTheme.BRIGHT))
    }
}
