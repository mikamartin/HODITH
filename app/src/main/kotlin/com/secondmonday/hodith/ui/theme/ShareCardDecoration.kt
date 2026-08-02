package com.secondmonday.hodith.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.AppTheme

/**
 * Which structural treatment the share card's chrome uses (spec §13 skin, validated in
 * `docs/mockups/share-cards-prototype.html`) — Plain's top border/divider, Intense's bordered
 * "Case File" stamp, Bright's banner header and sticker. Picked centrally here, like
 * [hodithColorScheme]/[hodithTypography]/[hodithShapes] and [bigPictureCellStyle], so
 * `ShareCardTemplate.kt` dispatches on this rather than branching on [AppTheme] itself.
 */
enum class ShareCardSkin {
    PLAIN,
    INTENSE,
    BRIGHT,
}

fun shareCardSkin(theme: AppTheme): ShareCardSkin =
    when (theme) {
        AppTheme.PLAIN -> ShareCardSkin.PLAIN
        AppTheme.INTENSE -> ShareCardSkin.INTENSE
        AppTheme.BRIGHT -> ShareCardSkin.BRIGHT
    }

val LocalShareCardSkin = staticCompositionLocalOf { ShareCardSkin.PLAIN }
