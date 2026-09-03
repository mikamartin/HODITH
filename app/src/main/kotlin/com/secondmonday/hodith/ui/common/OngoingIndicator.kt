package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.formatElapsedDuration

/**
 * The "Ongoing" chip that marks a running event (spec §6). A plain [Surface] + [Text] rather than
 * an M3 `AssistChip`/`SuggestionChip` on purpose — those are interactive affordances (button
 * semantics, ripple, a 32dp min height); this is a static status label. Sized off
 * [MaterialTheme.typography.labelSmall] so it stays subordinate to the name / time it sits under.
 */
@Composable
private fun OngoingPill(voice: Voice) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = voice.ongoingPillLabel,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * [OngoingPill] plus a trailing summary — elapsed time for one running event, a count for several.
 * Shared by Home's row and Case Detail's header so the two screens can't drift apart in wording or
 * styling.
 */
@Composable
fun OngoingElapsedText(
    startedAt: Long,
    now: Long,
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    OngoingSummary(voice = voice, trailing = formatElapsedDuration(startedAt, now), modifier = modifier)
}

/**
 * Shown wherever a single elapsed time can't stand for the running events on a Case (spec §6): the
 * Case Detail header (always, even for one event, so the header reads the same regardless of count)
 * and Home rows past one running event. Per-event Stop lives on the log rows.
 */
@Composable
fun OngoingCountText(
    count: Int,
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    OngoingSummary(voice = voice, trailing = voice.ongoingCountIndicator(count), modifier = modifier)
}

/** [OngoingPill] with a trailing elapsed time or count, spaced so the chip carries the separation. */
@Composable
private fun OngoingSummary(
    voice: Voice,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OngoingPill(voice)
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val STOP_SQUARE_SIDE_FRACTION = 0.55f
private const val STOP_SQUARE_CORNER_FRACTION = 0.16f

/**
 * A stop-shaped glyph drawn with [Canvas] primitives — the project deliberately doesn't depend on
 * `material-icons-extended` (docs/CLEANUP_LOG.md), where a filled-square "stop" icon would
 * otherwise come from, and the default set's `Icons.Filled.Done` (a ✓) reads as "confirm", not
 * "stop". Same approach as [com.secondmonday.hodith.ui.common.InfoIcon].
 */
@Composable
private fun StopSquare(
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val side = size.minDimension * STOP_SQUARE_SIDE_FRACTION
        drawRoundRect(
            color = tint,
            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * STOP_SQUARE_CORNER_FRACTION),
        )
    }
}

/** Stop is always an immediate one-tap action (spec §6) — no sheet, regardless of `logFlow`. */
@Composable
fun StopIconButton(
    caseName: String,
    voice: Voice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        StopSquare(
            contentDescription = voice.stopActionDescription(caseName),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun OngoingIndicatorPreviewContent(theme: AppTheme) {
    val voice = voiceFor(theme)
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OngoingElapsedText(startedAt = 0L, now = 2 * 60 * 60 * 1000L, voice = voice)
        OngoingCountText(count = 2, voice = voice)
        StopIconButton(caseName = "Migraine", voice = voice, onClick = {})
    }
}

@Preview(name = "OngoingIndicator — Plain light", showBackground = true, widthDp = 380)
@Composable
private fun OngoingIndicatorPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) { OngoingIndicatorPreviewContent(AppTheme.PLAIN) }
}

@Preview(name = "OngoingIndicator — Plain dark", showBackground = true, widthDp = 380)
@Composable
private fun OngoingIndicatorPlainDarkPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = true) { OngoingIndicatorPreviewContent(AppTheme.PLAIN) }
}

@Preview(name = "OngoingIndicator — Intense", showBackground = true, widthDp = 380)
@Composable
private fun OngoingIndicatorIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) { OngoingIndicatorPreviewContent(AppTheme.INTENSE) }
}

@Preview(name = "OngoingIndicator — Bright", showBackground = true, widthDp = 380)
@Composable
private fun OngoingIndicatorBrightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) { OngoingIndicatorPreviewContent(AppTheme.BRIGHT) }
}
