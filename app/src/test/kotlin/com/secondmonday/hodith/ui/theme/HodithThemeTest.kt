package com.secondmonday.hodith.ui.theme

import androidx.compose.ui.graphics.Color
import com.secondmonday.hodith.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Guards against a copy-paste mistake across the six near-simultaneous scheme/typography
 * definitions — the same failure mode [com.secondmonday.hodith.ui.voice.VoiceTest] guards
 * against for strings.
 */
class HodithThemeTest {
    private val schemesByThemeAndMode =
        AppTheme.entries.associateWith { theme ->
            listOf(false, true).associateWith { dark -> hodithColorScheme(theme, darkTheme = dark) }
        }

    @Test
    fun `every theme's light and dark scheme have distinct primary colors`() {
        val allPrimaries =
            schemesByThemeAndMode.values.flatMap { byMode -> byMode.values.map { it.primary } }
        assertEquals("expected 6 distinct primary colors, found a duplicate", allPrimaries.size, allPrimaries.toSet().size)
    }

    @Test
    fun `light and dark schemes within a theme differ`() {
        for ((_, byMode) in schemesByThemeAndMode) {
            assertNotEquals(byMode.getValue(false).background, byMode.getValue(true).background)
        }
    }

    @Test
    fun `every theme has a distinct typography and shape scale`() {
        val typographies = AppTheme.entries.map { hodithTypography(it) }
        val shapes = AppTheme.entries.map { hodithShapes(it) }
        assertEquals(3, typographies.map { it.headlineSmall.fontFamily }.toSet().size)
        assertEquals(3, shapes.map { it.extraLarge }.toSet().size)
    }

    /**
     * WCAG 2.1 relative luminance / contrast ratio (§1.4.3), scoped to Plain only: Bright
     * light's `onSurfaceVariant` is a known pre-existing AA gap (~3.9-4.1:1) unrelated to this
     * theme, so a general 6-combo assertion would fail on unrelated code.
     */
    @Test
    fun `Plain's text roles clear WCAG AA contrast against their surfaces`() {
        fun channelLuminance(c: Float): Double {
            val cs = c.toDouble()
            return if (cs <= 0.03928) cs / 12.92 else ((cs + 0.055) / 1.055).pow(2.4)
        }

        fun relativeLuminance(color: Color) =
            0.2126 * channelLuminance(color.red) + 0.7152 * channelLuminance(color.green) + 0.0722 * channelLuminance(color.blue)

        fun contrastRatio(
            a: Color,
            b: Color,
        ): Double {
            val (lighter, darker) = listOf(relativeLuminance(a), relativeLuminance(b)).sortedDescending()
            return (lighter + 0.05) / (darker + 0.05)
        }

        for (dark in listOf(false, true)) {
            val scheme = schemesByThemeAndMode.getValue(AppTheme.PLAIN).getValue(dark)
            val label = "Plain dark=$dark"
            assertTrue("$label onSurface vs surface", contrastRatio(scheme.onSurface, scheme.surface) >= 4.5)
            assertTrue("$label onSurface vs background", contrastRatio(scheme.onSurface, scheme.background) >= 4.5)
            assertTrue("$label onSurfaceVariant vs surface", contrastRatio(scheme.onSurfaceVariant, scheme.surface) >= 4.5)
            assertTrue("$label onSurfaceVariant vs background", contrastRatio(scheme.onSurfaceVariant, scheme.background) >= 4.5)
        }
    }

    @Test
    fun `intense keeps a single accent while bright uses its accent pair`() {
        val intenseLight = hodithColorScheme(AppTheme.INTENSE, darkTheme = false)
        val brightLight = hodithColorScheme(AppTheme.BRIGHT, darkTheme = false)
        assertTrue(
            "Bright's secondary is meant to be a distinct second accent, not a muted neutral",
            brightLight.secondary != brightLight.onSurfaceVariant,
        )
        assertNotEquals(
            "Intense's primary and secondary should not collide now that purple is gone",
            intenseLight.primary,
            intenseLight.secondary,
        )
    }
}
