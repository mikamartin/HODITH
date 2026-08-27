package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Shared shape for a section with a label, a tappable info icon explaining it, and its content below. */
@Composable
fun SectionWithInfo(
    label: String,
    infoTitle: String,
    infoBody: String,
    infoDescription: String,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    content: @Composable () -> Unit,
) {
    Column {
        LabelWithInfo(label, infoTitle, infoBody, infoDescription, labelStyle)
        content()
    }
}

/** Shared shape for a label with a tappable info icon and a trailing control on the same row (e.g. a toggle). */
@Composable
fun RowWithInfo(
    label: String,
    infoTitle: String,
    infoBody: String,
    infoDescription: String,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    trailingContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelWithInfo(label, infoTitle, infoBody, infoDescription, labelStyle)
        trailingContent()
    }
}

@Composable
private fun LabelWithInfo(
    label: String,
    infoTitle: String,
    infoBody: String,
    infoDescription: String,
    labelStyle: TextStyle,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(title = infoTitle, onDismiss = { showInfo = false }) { Text(infoBody) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = labelStyle)
        IconButton(onClick = { showInfo = true }) {
            InfoIcon(contentDescription = infoDescription, modifier = Modifier.size(18.dp))
        }
    }
}

private const val INFO_ICON_STROKE_WIDTH_FRACTION = 0.09f
private const val INFO_ICON_DOT_RADIUS_FRACTION = 0.06f
private const val INFO_ICON_DOT_CENTER_Y_FRACTION = 0.29f
private const val INFO_ICON_STEM_TOP_FRACTION = 0.46f
private const val INFO_ICON_STEM_BOTTOM_FRACTION = 0.73f

/**
 * A lighter, outlined stand-in for [androidx.compose.material.icons.filled.Info]'s solid filled
 * dot — more legible at 18dp than a filled Material icon (see
 * docs/mockups/plain-theme-light-neutrals.html). Drawn with [Canvas] primitives rather than a
 * hand-built [androidx.compose.ui.graphics.vector.ImageVector] path, since the project
 * deliberately doesn't depend on `material-icons-extended` (docs/CLEANUP_LOG.md), which is
 * where a ready-made outlined info icon would otherwise come from.
 */
@Composable
private fun InfoIcon(
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val strokeWidth = size.minDimension * INFO_ICON_STROKE_WIDTH_FRACTION
        drawCircle(color = tint, style = Stroke(width = strokeWidth))
        drawCircle(
            color = tint,
            radius = size.minDimension * INFO_ICON_DOT_RADIUS_FRACTION,
            center = Offset(size.width / 2f, size.height * INFO_ICON_DOT_CENTER_Y_FRACTION),
        )
        drawLine(
            color = tint,
            start = Offset(size.width / 2f, size.height * INFO_ICON_STEM_TOP_FRACTION),
            end = Offset(size.width / 2f, size.height * INFO_ICON_STEM_BOTTOM_FRACTION),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
