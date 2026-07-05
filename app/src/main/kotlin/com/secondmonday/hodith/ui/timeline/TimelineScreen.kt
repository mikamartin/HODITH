package com.secondmonday.hodith.ui.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.domain.timeline.TimeWindow
import com.secondmonday.hodith.domain.timeline.TimelineEvent
import com.secondmonday.hodith.domain.timeline.TimelineMark
import com.secondmonday.hodith.domain.timeline.ZoomLevel
import com.secondmonday.hodith.domain.timeline.layoutRow
import com.secondmonday.hodith.domain.timeline.nextWindow
import com.secondmonday.hodith.domain.timeline.withDuration
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ROW_HEIGHT = 56.dp
private val MIN_SLOT_WIDTH = 48.dp
private val LEADING_COLUMN_WIDTH = 96.dp
private const val SNAP_ANIMATION_MS = 200
private const val DOT_MAX_RADIUS_RATIO = 0.35f
private const val BAR_HEIGHT_RATIO = 0.3f

data class TimelineRowData(
    val caseId: Long,
    val icon: String,
    val name: String,
    val events: List<TimelineEvent>,
    val intensityEnabled: Boolean,
)

/**
 * Every case as a row sharing one horizontal time axis (spec §9). Gesture handling lives on the
 * single outer [Box] spanning all rows — not per row — so a pinch whose two fingers land on
 * different rows is still recognised as one zoom gesture rather than two independent pans.
 */
@Composable
fun TimelineGrid(
    rows: List<TimelineRowData>,
    initialWindow: TimeWindow,
    onDotTap: (caseId: Long, eventIds: List<Long>) -> Unit,
    onCaseTap: (caseId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var window by remember { mutableStateOf(initialWindow) }
    var timelineWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val minSlotWidthPx = with(density) { MIN_SLOT_WIDTH.toPx() }
    val leadingColumnWidthPx = with(density) { LEADING_COLUMN_WIDTH.toPx() }
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }

    val slotCount = if (timelineWidthPx > 0f) (timelineWidthPx / minSlotWidthPx).toInt().coerceAtLeast(1) else 1
    val marksByCase =
        remember(rows, window, slotCount) {
            rows.associate { it.caseId to layoutRow(it.events, window, slotCount, it.intensityEnabled) }
        }

    val scope = rememberCoroutineScope()
    val snapAnimatable = remember { Animatable(window.durationMillis.toFloat()) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { timelineWidthPx = (it.width - leadingColumnWidthPx).coerceAtLeast(0f) }
                .pointerInput(rows, window, leadingColumnWidthPx, rowHeightPx) {
                    val timelineWidth = (size.width - leadingColumnWidthPx).coerceAtLeast(1f)
                    detectTapOrTimelineGesture(
                        onTap = { position ->
                            val rowIndex = (position.y / rowHeightPx).toInt()
                            val row = rows.getOrNull(rowIndex)
                            if (row != null) {
                                if (position.x < leadingColumnWidthPx) {
                                    onCaseTap(row.caseId)
                                } else {
                                    val fraction = ((position.x - leadingColumnWidthPx) / timelineWidth).coerceIn(0f, 1f)
                                    marksByCase[row.caseId]
                                        ?.filterIsInstance<TimelineMark.Dot>()
                                        ?.minByOrNull { mark -> abs(mark.xFraction - fraction) }
                                        ?.let { dot -> onDotTap(row.caseId, dot.eventIds) }
                                }
                            }
                        },
                        onGesture = { centroid, pan, zoom ->
                            val focalFraction = ((centroid.x - leadingColumnWidthPx) / timelineWidth).coerceIn(0f, 1f)
                            val panFraction = pan.x / timelineWidth
                            window = nextWindow(window, focalFraction, panFraction, zoom)
                        },
                        onGestureEnd = {
                            val target = ZoomLevel.nearestTo(window.durationMillis).durationMillis
                            scope.launch {
                                snapAnimatable.snapTo(window.durationMillis.toFloat())
                                snapAnimatable.animateTo(target.toFloat(), tween(SNAP_ANIMATION_MS)) {
                                    window = window.withDuration(value.toLong())
                                }
                            }
                        },
                    )
                },
    ) {
        Column {
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                    Row(
                        modifier = Modifier.width(LEADING_COLUMN_WIDTH).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.icon)
                        Spacer(Modifier.width(4.dp))
                        Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CaseTimelineRow(
                        marks = marksByCase.getValue(row.caseId),
                        modifier = Modifier.weight(1f).fillMaxWidth().height(ROW_HEIGHT),
                    )
                }
            }
        }
    }
}

@Composable
private fun CaseTimelineRow(
    marks: List<TimelineMark>,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val dotMaxRadius = size.height * DOT_MAX_RADIUS_RATIO
        val barHeight = size.height * BAR_HEIGHT_RATIO
        marks.forEach { mark ->
            when (mark) {
                is TimelineMark.Dot ->
                    drawCircle(
                        color = dotColor,
                        radius = dotMaxRadius * mark.sizeFraction,
                        center = Offset(mark.xFraction * size.width, centerY),
                    )
                is TimelineMark.Bar ->
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(mark.startXFraction * size.width, centerY - barHeight / 2f),
                        size = Size(width = (mark.endXFraction - mark.startXFraction) * size.width, height = barHeight),
                        cornerRadius = CornerRadius(barHeight / 2f),
                    )
            }
        }
    }
}

/**
 * Synthetic rows for the [TimelineGridPreview] and for manually exercising [TimelineGrid] on a
 * device before real repository data is wired in (Phase 2 prototype step — see PROGRESS.md).
 */
fun sampleTimelineRows(now: Long): List<TimelineRowData> {
    val week = ZoomLevel.WEEK.durationMillis
    return listOf(
        TimelineRowData(
            caseId = 1,
            icon = "😤",
            name = "Kiddo was rude",
            events = (0 until 6).map { TimelineEvent(id = it.toLong(), occurredAt = now - it * (week / 6)) },
            intensityEnabled = true,
        ),
        TimelineRowData(
            caseId = 2,
            icon = "☕",
            name = "Perfect coffee",
            events = listOf(TimelineEvent(id = 100, occurredAt = now - week / 3)),
            intensityEnabled = false,
        ),
        TimelineRowData(
            caseId = 3,
            icon = "🤕",
            name = "Migraine",
            events = listOf(TimelineEvent(id = 200, occurredAt = now - week / 2, endedAt = now - week / 2 + week / 20)),
            intensityEnabled = false,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun TimelineGridPreview() {
    val now = System.currentTimeMillis()
    MaterialTheme {
        TimelineGrid(
            rows = sampleTimelineRows(now),
            initialWindow = TimeWindow(now - ZoomLevel.WEEK.durationMillis, now),
            onDotTap = { _, _ -> },
            onCaseTap = {},
        )
    }
}
