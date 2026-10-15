package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle

/**
 * A label + [Switch] where the whole row toggles, not just the switch — a bigger tap target, and
 * it merges label and control into one accessible node (a screen reader announces "Rhythm,
 * Switch, on" rather than an unlabelled switch). [Switch]'s own `onCheckedChange` is null because
 * [Modifier.toggleable] on the row already owns the click, per Material's label+control guidance.
 *
 * [labelStyle] and [colors] default to the ambient text style and M3's default switch colors —
 * every existing call site keeps its current look; pass them to opt into a different label weight
 * (e.g. a bolder sibling of another toggle on the same screen) or [themedSwitchColors].
 *
 * Shared by Share's section picker (spec §13), Big Picture's overview-detail dialog (spec §9), and
 * Case Edit's Track intensity toggle.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = LocalTextStyle.current,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = labelStyle)
        Switch(checked = checked, onCheckedChange = null, colors = colors)
    }
}
