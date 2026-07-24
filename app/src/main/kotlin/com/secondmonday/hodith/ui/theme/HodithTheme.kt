package com.secondmonday.hodith.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.secondmonday.hodith.data.AppTheme

/** Applies the selected [AppTheme]'s palette, type, and shape scale (spec §12). */
@Composable
fun HodithTheme(
    theme: AppTheme,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = hodithColorScheme(theme, darkTheme),
        typography = hodithTypography(theme),
        shapes = hodithShapes(theme),
        content = content,
    )
}
