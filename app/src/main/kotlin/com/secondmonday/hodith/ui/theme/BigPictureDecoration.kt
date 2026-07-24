package com.secondmonday.hodith.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.AppTheme

/**
 * Which structural treatment Big Picture's day cells use (spec §12 skin, applied to the one
 * screen whose per-theme differences go beyond swapping color/type/shape tokens — Intense's
 * dossier tab header, Bright's shadowed sticker-bubble icons). Picked centrally here, like
 * [hodithColorScheme]/[hodithTypography]/[hodithShapes], so `BigPictureGrid.kt` dispatches on
 * this rather than branching on [AppTheme] itself.
 */
enum class BigPictureCellStyle {
    PLAIN,
    INTENSE,
    BRIGHT,
}

fun bigPictureCellStyle(theme: AppTheme): BigPictureCellStyle =
    when (theme) {
        AppTheme.PLAIN -> BigPictureCellStyle.PLAIN
        AppTheme.INTENSE -> BigPictureCellStyle.INTENSE
        AppTheme.BRIGHT -> BigPictureCellStyle.BRIGHT
    }

val LocalBigPictureCellStyle = staticCompositionLocalOf { BigPictureCellStyle.PLAIN }
