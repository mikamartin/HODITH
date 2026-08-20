package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle

/** Shared shape for a label with a trailing [Switch], themed via [themedSwitchColors]. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = themedSwitchColors())
    }
}

/** Bright-only pill switch colors matching the mockup's `.mswitch`/`.mswitch.on` (white thumb both states, tinted track when on). */
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
