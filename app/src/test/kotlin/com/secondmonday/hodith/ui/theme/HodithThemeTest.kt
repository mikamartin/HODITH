package com.secondmonday.hodith.ui.theme

import com.secondmonday.hodith.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
