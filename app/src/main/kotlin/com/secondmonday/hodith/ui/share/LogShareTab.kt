package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.ui.common.DateRangeFilterDialog
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.LogShareSelection
import com.secondmonday.hodith.viewmodel.LogShareUiState
import com.secondmonday.hodith.viewmodel.isUnsetLogRange
import com.secondmonday.hodith.viewmodel.logShareCardState
import com.secondmonday.hodith.viewmodel.logShareSelectorValue
import com.secondmonday.hodith.viewmodel.toLocalDateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * The History tab: the Case's own entries, as a card. Controls (range, fields, sort) sit above the preview, since a long
 * log's rows would otherwise crowd the filters below the fold (spec §13 design pass, `docs/mockups/log-share-prototype.html`).
 * The card is capped the same way the final card is (`logShareCardState`), and its own truncation note says when the
 * range holds more than fits.
 */
@Composable
internal fun LogShareTab(
    uiState: LogShareUiState,
    now: Long,
    displayName: String,
    graphicsLayer: GraphicsLayer,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    onFieldToggle: (LogRowField, Boolean) -> Unit,
    onSortOrderSelect: (ChronologicalOrder) -> Unit,
) {
    val voice = LocalVoice.current
    val use24Hour = LocalTimeFormat.current.is24Hour
    val zone = remember { ZoneId.systemDefault() }
    val case = uiState.case ?: return
    val selection = uiState.selection

    DateRangeSection(case.createdAt, selection, now, zone, onDateFromPicked, onDateToPicked, voice)
    FieldsSection(case, selection.fields, onFieldToggle, voice)
    // No label here: the fields section above already carries the "Include" heading.
    SegmentedChoiceRow(
        options =
            listOf(
                ChronologicalOrder.NEWEST_FIRST to voice.shareLogSortNewestLabel,
                ChronologicalOrder.OLDEST_FIRST to voice.shareLogSortOldestLabel,
            ),
        selected = selection.sortOrder,
        onSelect = onSortOrderSelect,
    )

    val cardData =
        logShareCardState(
            case = case,
            displayName = displayName,
            events = uiState.events,
            sortOrder = selection.sortOrder,
            dateFrom = selection.dateFrom,
            dateTo = selection.dateTo,
            fields = selection.fields,
            use24Hour = use24Hour,
            voice = voice,
            now = now,
            generatedAtMillis = now,
            zone = zone,
        )

    SharePreviewStage(voice = voice, graphicsLayer = graphicsLayer) { captureModifier ->
        ShareCardTemplate(data = cardData, voice = voice, modifier = captureModifier)
    }
}

@Composable
private fun DateRangeSection(
    createdAt: Long,
    selection: LogShareSelection,
    now: Long,
    zone: ZoneId,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    voice: Voice,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    val isDefaultRange = isUnsetLogRange(selection.dateFrom, selection.dateTo, now, zone)

    OutlinedButton(onClick = { showRangePicker = true }, modifier = Modifier.fillMaxWidth()) {
        // Unset reads "All time", matching Case History's Range chip; the card above carries the resolved span.
        Text("${voice.shareLogRangeLabel}: ${logShareSelectorValue(createdAt, selection.dateFrom, selection.dateTo, now, zone, voice)}")
    }

    if (showRangePicker) {
        DateRangeFilterDialog(
            dateFrom = selection.dateFrom,
            dateTo = selection.dateTo,
            isRangeFiltered = !isDefaultRange,
            now = now,
            zone = zone,
            voice = voice,
            onDismiss = { showRangePicker = false },
            onConfirm = { from, to ->
                onDateFromPicked(from)
                onDateToPicked(to ?: now.toLocalDateIn(zone))
                showRangePicker = false
            },
        )
    }
}

@Composable
private fun FieldsSection(
    case: CaseEntity,
    selectedFields: Set<LogRowField>,
    onFieldToggle: (LogRowField, Boolean) -> Unit,
    voice: Voice,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(voice.shareSectionsPickerLabel, style = MaterialTheme.typography.labelLarge)
        availableLogRowFields(case).forEach { field ->
            ToggleRow(
                label = logShareFieldLabel(field, voice),
                checked = field in selectedFields,
                onCheckedChange = { onFieldToggle(field, it) },
                modifier = Modifier.testTag(LOG_SHARE_FIELD_TOGGLE_TAG_PREFIX + field.name),
            )
        }
    }
}

/**
 * Prefix for each field-toggle row's `testTag` — clicking by tag rather than by label text, same
 * as Big Picture's own `BIG_PICTURE_DETAIL_TOGGLE_TAG_PREFIX` and the Log tab's own
 * `LOG_DETAIL_FIELD_TOGGLE_TAG_PREFIX`, since the label/Switch semantics don't reliably merge into
 * one clickable node in every context.
 */
internal const val LOG_SHARE_FIELD_TOGGLE_TAG_PREFIX = "log_share_field_toggle_"

/** Notes/Tags always offered; Duration/Intensity only when the Case tracks them — same gating shape as [availableShareSections][com.secondmonday.hodith.viewmodel.availableShareSections]. */
private fun availableLogRowFields(case: CaseEntity): List<LogRowField> =
    buildList {
        add(LogRowField.NOTES)
        add(LogRowField.TAGS)
        if (case.durationMode.tracksDuration) add(LogRowField.DURATION)
        if (case.intensityEnabled) add(LogRowField.INTENSITY)
    }

private fun logShareFieldLabel(
    field: LogRowField,
    voice: Voice,
): String =
    when (field) {
        LogRowField.NOTES -> voice.shareLogFieldNotesLabel
        LogRowField.TAGS -> voice.shareLogFieldTagsLabel
        LogRowField.DURATION -> voice.insightsSectionLabelDuration
        LogRowField.INTENSITY -> voice.insightsSectionLabelIntensity
    }
