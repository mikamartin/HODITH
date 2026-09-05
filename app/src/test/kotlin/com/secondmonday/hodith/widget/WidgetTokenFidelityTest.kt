package com.secondmonday.hodith.widget

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.theme.hodithShapes
import com.secondmonday.hodith.ui.theme.hodithTypography
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Glance can't consume M3's `Typography`/`Shapes` directly (DEV_PLAYBOOK.md §4), so
 * `WidgetCommon.kt`'s type/spacing/shape tokens are hand-copied literals, not a shared import.
 * This guards against silent drift: if Plain's `Type.kt`/`Shape.kt` values change later, these
 * assertions fail instead of the widget quietly falling out of sync again, the same failure mode
 * [com.secondmonday.hodith.ui.theme.HodithThemeTest] guards against across the theme definitions
 * themselves.
 *
 * Tokens with no Home/`Type.kt` role ([WidgetPlusGlyphSize], [WidgetHeaderTitleSize],
 * [WidgetInfoMessageSize], and every padding/spacing token) aren't covered here — there's nothing
 * in Plain's theme for them to drift against.
 */
class WidgetTokenFidelityTest {
    private val plainTypography = hodithTypography(AppTheme.PLAIN)
    private val plainShapes = hodithShapes(AppTheme.PLAIN)

    // density = 1f so toPx's output equals the shape's declared dp value directly; the CornerSize
    // this resolves is Dp-based (RoundedCornerShape(x.dp)), so the Size argument is unused by it.
    private fun cornerRadiusDp(shape: CornerBasedShape): Float = shape.topStart.toPx(Size(1000f, 1000f), Density(density = 1f))

    @Test
    fun `widget icon glyph size mirrors Plain's headlineSmall`() {
        assertEquals(plainTypography.headlineSmall.fontSize.value, WidgetIconGlyphSize.value, 0f)
    }

    @Test
    fun `widget case name size mirrors Plain's titleMedium`() {
        assertEquals(plainTypography.titleMedium.fontSize.value, WidgetCaseNameSize.value, 0f)
    }

    @Test
    fun `widget subtitle size mirrors Plain's bodySmall`() {
        assertEquals(plainTypography.bodySmall.fontSize.value, WidgetSubtitleSize.value, 0f)
    }

    @Test
    fun `widget pill text size mirrors Plain's labelSmall`() {
        assertEquals(plainTypography.labelSmall.fontSize.value, WidgetPillTextSize.value, 0f)
    }

    @Test
    fun `widget corner radius mirrors Plain's medium shape`() {
        val plainMediumRadius = cornerRadiusDp(plainShapes.medium)
        assertEquals(plainMediumRadius, WidgetCornerRadius.value, 0.01f)
        assertEquals(plainMediumRadius, WidgetLogButtonCornerRadius.value, 0.01f)
    }
}
