package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareUiState
import com.secondmonday.hodith.viewmodel.availableShareSections
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.shareCardState
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Test hook only — `onNodeWithText` is ambiguous once a section's label also appears in the live card preview above. */
internal const val SECTION_TOGGLE_TAG_PREFIX = "section_toggle_"

/** Test hook only — the drag handle of each picker row, so a test can assert one exists per row. */
internal const val SECTION_HANDLE_TAG_PREFIX = "section_handle_"

/**
 * The Summary and Insights tabs: one Insight card in the format the tab names. Insights (Story) adds the section picker
 * above the preview; Summary (Square) is a fixed preset built from the Case's own settings, so it has no controls.
 */
@Composable
internal fun InsightShareTab(
    format: ShareCardFormat,
    uiState: ShareUiState,
    now: Long,
    displayName: String,
    graphicsLayer: GraphicsLayer,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
    onSectionMove: (available: List<ShareInsightsSection>, from: Int, to: Int) -> Unit,
) {
    val voice = LocalVoice.current
    val case = uiState.case ?: return
    val events = uiState.events.map { it.event }
    // insightsTabState only cares about "now" down to the calendar day (see CaseDetailScreen.kt's
    // matching comment) -- memoizing on that day, not the raw value `now` returns fresh on every
    // recomposition, keeps this tab's own recomposition (e.g. every keystroke in the name field, every
    // section toggle) from re-running the full stats/Trends computation, now including Story C T4's permutation test.
    val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
    val insightsState = remember(case, uiState.events, today) { insightsTabState(case, uiState.events, now) }
    val selection = uiState.selection

    val cardData =
        shareCardState(
            case = case,
            displayName = displayName,
            insightsState = insightsState,
            eventCount = events.size,
            observedDays = observationSpanDays(events, case.createdAt, now),
            format = format,
            selectedSections = selection.selectedSections,
            generatedAtMillis = now,
            sectionOrder = uiState.sectionOrder,
        )

    if (format == ShareCardFormat.STORY) {
        // The picker lists only the sections this Case has data for, in the user's saved order.
        val stats = (insightsState as? InsightsTabState.Ready)?.stats
        val availableSections = availableShareSections(stats)
        val orderedAvailable = uiState.sectionOrder.filter { it in availableSections }
        SectionsPicker(
            availableSections = orderedAvailable,
            selectedSections = selection.selectedSections,
            voice = voice,
            onSectionToggle = onSectionToggle,
            onSectionMove = { from, to -> onSectionMove(orderedAvailable, from, to) },
        )
    }

    SharePreviewStage(voice = voice, graphicsLayer = graphicsLayer) { captureModifier ->
        ShareCardTemplate(data = cardData, voice = voice, modifier = captureModifier)
    }
}

/**
 * [availableSections] arrives in the user's saved order, so only sections the Case has data for get a
 * row; with none, the picker is omitted. Long-pressing a row's handle and dragging moves it: the
 * dragged row follows the finger, the rows it passes slide one slot, and the new order is saved on
 * release via [onSectionMove] (visible-row indices).
 */
@Composable
private fun SectionsPicker(
    availableSections: List<ShareInsightsSection>,
    selectedSections: Set<ShareInsightsSection>,
    voice: Voice,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
    onSectionMove: (from: Int, to: Int) -> Unit,
) {
    if (availableSections.isEmpty()) return

    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    fun targetIndex(from: Int): Int =
        if (rowHeightPx == 0f) from else (from + (dragOffsetPx / rowHeightPx).roundToInt()).coerceIn(0, availableSections.lastIndex)

    /** How far a row sits from its resting slot while a drag is in flight: the dragged row follows the finger, the rows between slide one slot. */
    fun rowShiftPx(index: Int): Float {
        val from = dragIndex ?: return 0f
        val target = targetIndex(from)
        return when {
            index == from -> dragOffsetPx
            from < target && index in (from + 1)..target -> -rowHeightPx
            target < from && index in target until from -> rowHeightPx
            else -> 0f
        }
    }

    fun endDrag() {
        dragIndex?.let { from ->
            val to = targetIndex(from)
            if (to != from) onSectionMove(from, to)
        }
        dragIndex = null
        dragOffsetPx = 0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(voice.shareSectionsPickerLabel, style = MaterialTheme.typography.labelLarge)
        availableSections.forEachIndexed { index, section ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { rowHeightPx = it.height.toFloat() }
                        .zIndex(if (index == dragIndex) 1f else 0f)
                        .graphicsLayer { translationY = rowShiftPx(index) }
                        // Long-press anywhere on the row to drag it. A short tap still reaches the row's toggle, since a press
                        // that never moves past the long-press timeout is not a drag.
                        .pointerInput(index, availableSections) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragIndex = index
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx += dragAmount.y
                                },
                                onDragEnd = { endDrag() },
                                onDragCancel = {
                                    dragIndex = null
                                    dragOffsetPx = 0f
                                },
                            )
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DragGrip(
                    contentDescription = voice.shareSectionDragHandleDescription,
                    modifier =
                        Modifier
                            .padding(end = 8.dp)
                            .testTag(SECTION_HANDLE_TAG_PREFIX + section.name),
                )
                ToggleRow(
                    label = sectionLabel(section, voice),
                    checked = section in selectedSections,
                    onCheckedChange = { onSectionToggle(section, it) },
                    modifier = Modifier.weight(1f).testTag(SECTION_TOGGLE_TAG_PREFIX + section.name),
                )
            }
        }
    }
}

/**
 * Six dots in two columns, the picker's drag affordance. Drawn rather than taken from the icon set
 * because material-icons-core ships no drag handle.
 */
@Composable
private fun DragGrip(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.size(24.dp).semantics { this.contentDescription = contentDescription }) {
        val radius = 2.dp.toPx()
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            drawCircle(color = color, radius = radius, center = Offset(size.width * 0.35f, size.height * fraction))
            drawCircle(color = color, radius = radius, center = Offset(size.width * 0.65f, size.height * fraction))
        }
    }
}

/** Each row reads as the title of the card section it toggles. */
internal fun sectionLabel(
    section: ShareInsightsSection,
    voice: Voice,
): String =
    when (section) {
        ShareInsightsSection.GAPS -> voice.shareGapsTitle
        ShareInsightsSection.STREAKS -> voice.shareStreaksTitle
        ShareInsightsSection.DURATION -> voice.shareDurationTitle
        ShareInsightsSection.RHYTHM -> voice.insightsSectionLabelRhythmStarts
        ShareInsightsSection.INTENSITY -> voice.insightsSectionLabelIntensity
        ShareInsightsSection.TRENDS -> voice.insightsSectionLabelTrends
        ShareInsightsSection.TAGS -> voice.shareTopTagsTitle
    }
