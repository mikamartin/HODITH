package com.secondmonday.hodith.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalBigPictureCellStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalShareCardSkin
import com.secondmonday.hodith.ui.theme.bigPictureCellStyle
import com.secondmonday.hodith.ui.theme.cardDecorationStyle
import com.secondmonday.hodith.ui.theme.shareCardSkin
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor

/**
 * Mirrors [com.secondmonday.hodith.ui.HodithApp]'s own composition (real [HodithTheme] colors plus
 * every theme-driven composition local, all from one [AppTheme]) so a UI test under [theme] renders
 * the same combination a real user on that theme actually sees, instead of independently-set voice/
 * decoration-style/color axes that can never occur together in the app.
 */
fun ComposeContentTestRule.setHodithContent(
    theme: AppTheme = AppTheme.PLAIN,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent {
        CompositionLocalProvider(
            LocalVoice provides voiceFor(theme),
            LocalBigPictureCellStyle provides bigPictureCellStyle(theme),
            LocalCardDecorationStyle provides cardDecorationStyle(theme),
            LocalShareCardSkin provides shareCardSkin(theme),
        ) {
            HodithTheme(theme = theme, darkTheme = darkTheme, content = content)
        }
    }
}
