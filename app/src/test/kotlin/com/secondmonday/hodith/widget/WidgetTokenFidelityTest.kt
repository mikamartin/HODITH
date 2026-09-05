package com.secondmonday.hodith.widget

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.home.PlainPlankVerticalMargin
import com.secondmonday.hodith.ui.theme.hodithShapes
import com.secondmonday.hodith.ui.theme.hodithTypography
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.glance.text.FontWeight as GlanceFontWeight

/**
 * Glance can't consume M3's `Typography`/`Shapes` directly (DEV_PLAYBOOK.md §4), so
 * `WidgetCommon.kt`'s type/spacing/shape tokens are hand-copied literals, not a shared import.
 * This guards against silent drift: if Plain's `Type.kt`/`Shape.kt` values change later, these
 * assertions fail instead of the widget quietly falling out of sync again, the same failure mode
 * [com.secondmonday.hodith.ui.theme.HodithThemeTest] guards against across the theme definitions
 * themselves.
 *
 * Token *sizes* with no Home/`Type.kt` role ([WidgetPlusGlyphSize], [WidgetHeaderTitleSize],
 * [WidgetInfoMessageSize]) aren't covered here — there's nothing in Plain's theme for them to drift
 * against. Tokens anchored to a Home value that isn't a `Type.kt` size — [WidgetPlankSpacing] (a
 * `HomeScreen.kt` plank-layout value) and [WidgetHeaderTitleWeight] (Plain's `headlineSmall`
 * weight, the header's in-app role, even though the widget keeps the header size compact) — are
 * checked against that value.
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

    @Test
    fun `widget plank spacing mirrors Home's Plain plank vertical margins`() {
        // Home's PlainPlankHomeCaseListItem Card carries PlainPlankVerticalMargin above and below,
        // so adjacent planks sit twice that apart. The List widget uses one token for that gap, so
        // its rows read as the same discrete cards.
        assertEquals(PlainPlankVerticalMargin.value * 2, WidgetPlankSpacing.value, 0f)
    }

    @Test
    fun `widget header weight tracks Plain's headlineSmall, at the nearest Glance rung`() {
        // Home's header Text renders at headlineSmall, which carries Plain's display weight,
        // FontWeight.SemiBold. Glance's FontWeight has no 600 rung, so the widget header uses Bold.
        // If Plain's headlineSmall weight moves off SemiBold the nod is wrong: fail here rather than
        // let the widget drift silently.
        assertEquals(FontWeight.SemiBold, plainTypography.headlineSmall.fontWeight)
        assertEquals(GlanceFontWeight.Bold, WidgetHeaderTitleWeight)
    }
}
