package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle

/**
 * Shared shape for a single-choice segmented row (Case Edit's logFlow/durationMode/check-in,
 * Settings' theme picker, Insights' frequency granularity). Defaults to a full-width row with a
 * gap above it, for stacking under a section label; the Log tab passes [modifier] `= Modifier`
 * and [stretchToFill] `= false` to sit inline beside its "Sort" label instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    stretchToFill: Boolean = true,
    enabled: (T) -> Boolean = { true },
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            BrightSegmentedChoiceRow(
                options = options,
                selected = selected,
                onSelect = onSelect,
                modifier = modifier,
                stretchToFill = stretchToFill,
                enabled = enabled,
            )
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> {
            // Plain uses tertiaryContainer for the selected segment instead of the default
            // secondaryContainer — same reasoning as ActionRow's colors override, see its doc
            // comment (docs/mockups/plain-theme-light-neutrals.html).
            val colors =
                if (LocalCardDecorationStyle.current == CardDecorationStyle.PLAIN) {
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    SegmentedButtonDefaults.colors()
                }
            SingleChoiceSegmentedButtonRow(modifier = modifier) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = selected == option,
                        enabled = enabled(option),
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = colors,
                    ) { Text(label) }
                }
            }
        }
    }
}

/**
 * Bright-only continuous "pill track" segmented control (Soft Glow mockup's `.segrow`/`.seg`/
 * `.seg.on`): a single tinted track holding every option, with the selected one popped forward as
 * a floating capsule. That's a different visual metaphor than M3's bordered per-segment
 * [SegmentedButton] chrome (which only rounds the group's outer ends, not each segment), so this
 * builds the track directly rather than reskinning the M3 primitive — same call already made for
 * [com.secondmonday.hodith.ui.theme.GlowCard] over a restyled [androidx.compose.material3.Card].
 * Manually replicates the selectable-group semantics [SegmentedButton] normally provides, same
 * idiom as [com.secondmonday.hodith.ui.case.CaseEditScreen]'s icon-picker grid.
 */
@Composable
private fun <T> BrightSegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier,
    stretchToFill: Boolean,
    enabled: (T) -> Boolean,
) {
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .padding(4.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selected
            val isEnabled = enabled(option)
            Box(
                modifier =
                    Modifier
                        .then(if (stretchToFill) Modifier.weight(1f) else Modifier)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.shadow(elevation = 3.dp, shape = CircleShape).background(MaterialTheme.colorScheme.surface)
                            } else {
                                Modifier
                            },
                        ).selectable(selected = isSelected, enabled = isEnabled, onClick = { onSelect(option) }, role = Role.RadioButton)
                        .padding(horizontal = if (stretchToFill) 0.dp else 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color =
                        when {
                            !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

/**
 * Exercises [BrightSegmentedChoiceRow] in both a two-option shape (Edit Case's logFlow row, with
 * its disabled-when-unavailable "One tap" option) and a three-option shape (durationMode/Settings'
 * theme picker), the two contexts named in PROGRESS.md's validation note for this control.
 */
@Composable
private fun SegmentedChoiceRowBrightPreviewContent() {
    var logFlow by remember { mutableIntStateOf(0) }
    var durationMode by remember { mutableIntStateOf(1) }
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SegmentedChoiceRow(
            options = listOf(0 to "One tap", 1 to "Detail sheet"),
            selected = logFlow,
            onSelect = { logFlow = it },
            enabled = { it != 0 },
        )
        SegmentedChoiceRow(
            options = listOf(0 to "None", 1 to "Manual", 2 to "Start/stop"),
            selected = durationMode,
            onSelect = { durationMode = it },
        )
    }
}

@Preview(name = "SegmentedChoiceRow — Bright light", showBackground = true, widthDp = 340, heightDp = 220)
@Composable
private fun SegmentedChoiceRowBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            SegmentedChoiceRowBrightPreviewContent()
        }
    }
}

@Preview(name = "SegmentedChoiceRow — Bright dark", showBackground = true, widthDp = 340, heightDp = 220)
@Composable
private fun SegmentedChoiceRowBrightDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            SegmentedChoiceRowBrightPreviewContent()
        }
    }
}
