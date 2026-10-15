package com.secondmonday.hodith.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle

/** Bright-only pill switch colors (white thumb both states, tinted track when on). */
@Composable
fun themedSwitchColors(): SwitchColors =
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                uncheckedBorderColor = Color.Transparent,
            )
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> SwitchDefaults.colors()
    }
