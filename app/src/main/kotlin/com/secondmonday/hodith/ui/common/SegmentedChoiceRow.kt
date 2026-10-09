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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle

/**
 * Collects each segment's own autoSize result (reported once via [report]) across one or more
 * [SegmentedChoiceRow]s, and once every registered segment has reported, exposes the smallest of
 * them as [resolved] so every segment can switch from independently auto-shrinking to that one
 * shared size. A row joins by [registerRow]ing its own segment count on entering composition and
 * [unregisterRow]ing on leaving (e.g. a dismissed dialog), so a row that disappears can't block
 * [resolved] from ever settling for the rows that remain.
 *
 * By default each [SegmentedChoiceRow] gets its own private instance, so only its own segments
 * are coordinated. Providing one instance via [LocalSegmentedRowFontSizeCoordinator] over several
 * rows (e.g. Settings' Appearance section, around its Theme and Time format rows) extends that
 * same one-size guarantee across all of them, not just within each -- otherwise a 3-option row and
 * a 2-option row stacked together can land on two different sizes, since each divides the same
 * width by a different number of segments.
 *
 * Not re-triggered by a later width change (e.g. a rotation) -- an accepted limitation rather than
 * an oversight, since this control's real callers don't change width after first layout.
 */
class SegmentedRowFontSizeCoordinator {
    private val expectedSegmentCountByRow = mutableStateMapOf<Any, Int>()
    private val measured = mutableStateMapOf<Pair<Any, Int>, TextUnit>()

    val resolved: TextUnit?
        get() {
            val expectedTotal = expectedSegmentCountByRow.values.sum()
            return if (expectedTotal > 0 && measured.size >= expectedTotal) {
                measured.values.minByOrNull { it.value }
            } else {
                null
            }
        }

    fun registerRow(
        rowId: Any,
        segmentCount: Int,
    ) {
        expectedSegmentCountByRow[rowId] = segmentCount
    }

    fun unregisterRow(rowId: Any) {
        expectedSegmentCountByRow.remove(rowId)
        measured.keys.filter { it.first == rowId }.forEach(measured::remove)
    }

    fun report(
        rowId: Any,
        index: Int,
        fontSize: TextUnit,
    ) {
        val key = rowId to index
        if (measured[key] != fontSize) measured[key] = fontSize
    }
}

/**
 * Shares one [SegmentedRowFontSizeCoordinator] across several [SegmentedChoiceRow]s so they render
 * at one common font size instead of each only coordinating its own segments -- see that class's
 * doc comment. `null` (the default) leaves each row with its own private instance.
 */
val LocalSegmentedRowFontSizeCoordinator = compositionLocalOf<SegmentedRowFontSizeCoordinator?> { null }

/** A [SegmentedRowFontSizeCoordinator] plus the caller's own stable key into it ([rowId]). */
private class SegmentedRowFontSizeHandle(
    val coordinator: SegmentedRowFontSizeCoordinator,
    val rowId: Any,
)

@Composable
private fun <T> rememberSegmentedRowFontSizeHandle(options: List<Pair<T, String>>): SegmentedRowFontSizeHandle {
    val ambient = LocalSegmentedRowFontSizeCoordinator.current
    val coordinator = ambient ?: remember(options) { SegmentedRowFontSizeCoordinator() }
    val rowId = remember { Any() }
    DisposableEffect(coordinator, rowId, options.size) {
        coordinator.registerRow(rowId, options.size)
        onDispose { coordinator.unregisterRow(rowId) }
    }
    return SegmentedRowFontSizeHandle(coordinator, rowId)
}

/**
 * Shared shape for a single-choice segmented row (Case Edit's logFlow/durationMode/check-in,
 * Settings' theme picker, Insights' frequency granularity). By default it is a full-width row with
 * a gap above it, for stacking under a section label; [stretchToFill] `= false` drops both so it
 * can sit inline beside a label instead. The row's own layout is applied before [modifier], so a
 * caller's modifier (a test tag, semantics) adds to it rather than replacing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    stretchToFill: Boolean = true,
    enabled: (T) -> Boolean = { true },
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    // Bright-only: PLAIN/INTENSE's `SegmentedButton` manages its own chrome and ignores these.
    segmentHorizontalPadding: Dp = if (stretchToFill) 0.dp else 16.dp,
    segmentVerticalPadding: Dp = 7.dp,
) {
    val rowModifier = (if (stretchToFill) Modifier.fillMaxWidth().padding(top = 8.dp) else Modifier).then(modifier)
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            BrightSegmentedChoiceRow(
                options = options,
                selected = selected,
                onSelect = onSelect,
                modifier = rowModifier,
                stretchToFill = stretchToFill,
                enabled = enabled,
                textStyle = textStyle,
                segmentHorizontalPadding = segmentHorizontalPadding,
                segmentVerticalPadding = segmentVerticalPadding,
            )
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> {
            // Plain uses tertiaryContainer for the selected segment instead of the default
            // secondaryContainer — same reasoning as ActionRow's colors override, see its doc
            // comment.
            val colors =
                if (LocalCardDecorationStyle.current == CardDecorationStyle.PLAIN) {
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    SegmentedButtonDefaults.colors()
                }
            val fontSizeHandle = rememberSegmentedRowFontSizeHandle(options)
            SingleChoiceSegmentedButtonRow(modifier = rowModifier) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = selected == option,
                        enabled = enabled(option),
                        onClick = { onSelect(option) },
                        // M3's own default baseShape is a fully-rounded pill regardless of theme;
                        // using the app's own small shape keeps this in step with Plain/Intense's
                        // actual corner-radius scale instead of a hardcoded rounder default.
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = options.size,
                                baseShape = MaterialTheme.shapes.small,
                            ),
                        colors = colors,
                        // The active/inactive color swap already reads clearly as selection --
                        // M3's default checkmark icon on top of that is redundant visual noise.
                        icon = {},
                    ) {
                        // textStyle defaults to labelLarge, which is the theme's bold-weight
                        // display font (SemiBold/Bold per theme) -- appropriate for a real label,
                        // but noticeably heavier and wider than this control needs, which was
                        // making longer options wrap. Forcing Normal weight keeps the same font
                        // family/size (so it still matches the theme) without the extra bulk.
                        // Each segment auto-shrinks to fit on one line, then fontSizeHandle's
                        // coordinator pins every segment -- in this row, and in any sibling rows
                        // sharing it via LocalSegmentedRowFontSizeCoordinator -- to the smallest
                        // size any of them needed. Without it, a short label like "None" would
                        // stay at its own larger natural size while "Start/stop" shrank, reading
                        // as inconsistent rather than uniform.
                        val coordinatedFontSize = fontSizeHandle.coordinator.resolved
                        Text(
                            label,
                            style = textStyle,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            fontSize = coordinatedFontSize ?: TextUnit.Unspecified,
                            // maxFontSize capped at the style's own size: autoSize should only
                            // ever shrink this control to fit, never grow it past its intended
                            // size (which it otherwise could, up to StepBased's 112sp default, if
                            // a layout pass ever hands the search an unbounded width).
                            autoSize = if (coordinatedFontSize == null) TextAutoSize.StepBased(maxFontSize = textStyle.fontSize) else null,
                            onTextLayout = { result ->
                                if (coordinatedFontSize == null) {
                                    fontSizeHandle.coordinator.report(fontSizeHandle.rowId, index, result.layoutInput.style.fontSize)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bright-only continuous "pill track" segmented control: a single tinted track holding every
 * option, with the selected one popped forward as a floating capsule. That's a different visual
 * metaphor than M3's bordered per-segment [SegmentedButton] chrome (which only rounds the
 * group's outer ends, not each segment), so this
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
    textStyle: TextStyle,
    segmentHorizontalPadding: Dp,
    segmentVerticalPadding: Dp,
) {
    val fontSizeHandle = rememberSegmentedRowFontSizeHandle(options)
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .padding(4.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEachIndexed { index, (option, label) ->
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
                        .padding(horizontal = segmentHorizontalPadding, vertical = segmentVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                // See the Plain/Intense branch's matching comment -- fontSizeHandle's coordinator
                // pins every segment to the smallest size any of them needed, instead of only
                // "Start/stop" shrinking while its shorter siblings stay at their own larger
                // natural size.
                val coordinatedFontSize = fontSizeHandle.coordinator.resolved
                Text(
                    text = label,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontSize = coordinatedFontSize ?: TextUnit.Unspecified,
                    // See the Plain/Intense branch's matching comment -- capped so autoSize can
                    // only shrink this control, never grow it past its intended size.
                    autoSize = if (coordinatedFontSize == null) TextAutoSize.StepBased(maxFontSize = textStyle.fontSize) else null,
                    onTextLayout = { result ->
                        if (coordinatedFontSize == null) {
                            fontSizeHandle.coordinator.report(fontSizeHandle.rowId, index, result.layoutInput.style.fontSize)
                        }
                    },
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
 * Exercises [SegmentedChoiceRow] in both a two-option shape (Edit Case's logFlow row, with its
 * disabled-when-unavailable "One tap" option) and a three-option shape (durationMode/Settings'
 * theme picker), the two contexts named in PROGRESS.md's validation note for this control. Shared
 * across the Plain/Intense and Bright previews below since the content itself is decoration-style
 * agnostic -- [LocalCardDecorationStyle] is what picks [BrightSegmentedChoiceRow] vs. the M3
 * `SegmentedButton` branch.
 */
@Composable
private fun SegmentedChoiceRowPreviewContent() {
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
            SegmentedChoiceRowPreviewContent()
        }
    }
}

@Preview(name = "SegmentedChoiceRow — Bright dark", showBackground = true, widthDp = 340, heightDp = 220)
@Composable
private fun SegmentedChoiceRowBrightDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            SegmentedChoiceRowPreviewContent()
        }
    }
}

/**
 * Plain's case for the "Start/stop" wrap regression: Plain's Inter font is wider per character
 * than Intense's condensed Oswald, so a durationMode-sized row (None/Manual/Start-stop) only
 * overflowed here -- SegmentedChoiceRowTest has the automated version of this check.
 */
@Preview(name = "SegmentedChoiceRow — Plain light", showBackground = true, widthDp = 340, heightDp = 220)
@Composable
private fun SegmentedChoiceRowPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.PLAIN) {
            SegmentedChoiceRowPreviewContent()
        }
    }
}

@Preview(name = "SegmentedChoiceRow — Plain dark", showBackground = true, widthDp = 340, heightDp = 220)
@Composable
private fun SegmentedChoiceRowPlainDarkPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = true) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.PLAIN) {
            SegmentedChoiceRowPreviewContent()
        }
    }
}

/**
 * Settings' Appearance section shape: a 3-option row (Theme) stacked directly above a 2-option
 * row (Time format), both stretched to the same full width but dividing it by a different segment
 * count -- without sharing one [LocalSegmentedRowFontSizeCoordinator], the 3-option row's longer
 * word ("Intense") can need a smaller size than the roomier 2-option row ever does, leaving the
 * two rows visibly mismatched even though each is internally consistent.
 */
@Composable
private fun SegmentedChoiceRowSharedCoordinatorPreviewContent() {
    var theme by remember { mutableIntStateOf(0) }
    var timeFormat by remember { mutableIntStateOf(0) }
    val coordinator = remember { SegmentedRowFontSizeCoordinator() }
    CompositionLocalProvider(LocalSegmentedRowFontSizeCoordinator provides coordinator) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SegmentedChoiceRow(
                options = listOf(0 to "Plain", 1 to "Intense", 2 to "Bright"),
                selected = theme,
                onSelect = { theme = it },
            )
            SegmentedChoiceRow(
                options = listOf(0 to "12-hour", 1 to "24-hour"),
                selected = timeFormat,
                onSelect = { timeFormat = it },
            )
        }
    }
}

@Preview(name = "SegmentedChoiceRow — shared coordinator", showBackground = true, widthDp = 340, heightDp = 220)
@Composable
private fun SegmentedChoiceRowSharedCoordinatorPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.PLAIN) {
            SegmentedChoiceRowSharedCoordinatorPreviewContent()
        }
    }
}
