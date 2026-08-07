package com.secondmonday.hodith.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.AppTheme

/**
 * Which structural treatment shared list/card composables use (spec §12; Soft Glow mockup,
 * docs/mockups/bright-theme-soft-glow.html) — Plain and Intense keep today's plain [androidx
 * .compose.material3.Card]/row chrome, Bright picks up the gradient-wash [GlowCard]/[IconHalo]
 * treatment. Picked centrally here, like [hodithColorScheme]/[hodithTypography]/[hodithShapes]
 * and [bigPictureCellStyle]/[shareCardSkin], so Home/Insights/Settings/Edit Case/
 * `SegmentedChoiceRow` dispatch on this rather than branching on [AppTheme] itself.
 */
enum class CardDecorationStyle {
    PLAIN,
    INTENSE,
    BRIGHT,
}

fun cardDecorationStyle(theme: AppTheme): CardDecorationStyle =
    when (theme) {
        AppTheme.PLAIN -> CardDecorationStyle.PLAIN
        AppTheme.INTENSE -> CardDecorationStyle.INTENSE
        AppTheme.BRIGHT -> CardDecorationStyle.BRIGHT
    }

val LocalCardDecorationStyle = staticCompositionLocalOf { CardDecorationStyle.PLAIN }
