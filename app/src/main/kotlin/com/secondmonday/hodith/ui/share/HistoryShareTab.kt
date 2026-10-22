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
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.ui.common.DateRangeFilterDialog
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.HistoryShareSelection
import com.secondmonday.hodith.viewmodel.HistoryShareUiState
import com.secondmonday.hodith.viewmodel.historyShareCardState
import com.secondmonday.hodith.viewmodel.historyShareSelectorValue
import com.secondmonday.hodith.viewmodel.isUnsetHistoryRange
import com.secondmonday.hodith.viewmodel.toLocalDateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The History tab: the Case's own entries, as a card or - once [pdfMode] is on - a PDF record
 * instead (feature/pdf-export). Controls (range, sharing mode, fields) sit above the preview,
 * since a long log's rows would otherwise crowd the filters below the fold (spec §13 design pass,
 * `docs/mockups/log-share-prototype.html`). The card is capped the same way the final card is
 * (`historyShareCardState`), and its own truncation note says when the range holds more than fits
 * - the PDF path (built by the caller from the same [uiState]) has no such cap.
 */
@Composable
internal fun HistoryShareTab(
    uiState: HistoryShareUiState,
    now: Long,
    displayName: String,
    graphicsLayer: GraphicsLayer,
    pdfMode: Boolean,
    onPdfModeChange: (Boolean) -> Unit,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    onFieldToggle: (HistoryRowField, Boolean) -> Unit,
    onSortOrderSelect: (ChronologicalOrder) -> Unit,
) {
    val voice = LocalVoice.current
    val use24Hour = LocalTimeFormat.current.is24Hour
    val zone = remember { TimeZone.currentSystemDefault() }
    val case = uiState.case ?: return
    val selection = uiState.selection

    DateRangeSection(case.createdAt, selection, now, zone, onDateFromPicked, onDateToPicked, voice)
    SharingModeSection(pdfMode, onPdfModeChange, selection.sortOrder, onSortOrderSelect, voice)
    FieldsSection(case, selection.fields, onFieldToggle, voice)

    if (!pdfMode) {
        val cardData =
            historyShareCardState(
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
}

/**
 * The two toggles that pick *how* the Case shares, kept apart from [FieldsSection]'s "Include"
 * group below since they're a different kind of choice (format and order, not which fields show).
 * The sort toggle reuses [com.secondmonday.hodith.ui.common.ToggleRow] rather than
 * [SegmentedChoiceRow] now that a second toggle sits beside it, for one consistent control shape.
 */
@Composable
private fun SharingModeSection(
    pdfMode: Boolean,
    onPdfModeChange: (Boolean) -> Unit,
    sortOrder: ChronologicalOrder,
    onSortOrderSelect: (ChronologicalOrder) -> Unit,
    voice: Voice,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow(
            label = voice.shareAsPdfActionLabel,
            checked = pdfMode,
            onCheckedChange = onPdfModeChange,
            modifier = Modifier.testTag(HISTORY_SHARE_PDF_TOGGLE_TAG),
        )
        ToggleRow(
            label = voice.shareHistorySortOldestLabel,
            checked = sortOrder == ChronologicalOrder.OLDEST_FIRST,
            onCheckedChange = { oldestFirst ->
                onSortOrderSelect(if (oldestFirst) ChronologicalOrder.OLDEST_FIRST else ChronologicalOrder.NEWEST_FIRST)
            },
            modifier = Modifier.testTag(HISTORY_SHARE_SORT_TOGGLE_TAG),
        )
    }
}

/** Test hook only - the PDF-mode toggle's own tag, since its label text ([Voice.shareAsPdfActionLabel]) is shared with the Insights tab's toggle. */
internal const val HISTORY_SHARE_PDF_TOGGLE_TAG = "history_share_pdf_toggle"

/** Test hook only - the sort toggle's own tag. */
internal const val HISTORY_SHARE_SORT_TOGGLE_TAG = "history_share_sort_toggle"

@Composable
private fun DateRangeSection(
    createdAt: Long,
    selection: HistoryShareSelection,
    now: Long,
    zone: TimeZone,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    voice: Voice,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    val isDefaultRange = isUnsetHistoryRange(selection.dateFrom, selection.dateTo, now, zone)

    OutlinedButton(onClick = { showRangePicker = true }, modifier = Modifier.fillMaxWidth()) {
        // Unset reads "All time", matching Case History's Range chip; the card above carries the resolved span.
        Text(
            "${voice.shareHistoryRangeLabel}: ${historyShareSelectorValue(
                createdAt,
                selection.dateFrom,
                selection.dateTo,
                now,
                zone,
                voice,
            )}",
        )
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
    selectedFields: Set<HistoryRowField>,
    onFieldToggle: (HistoryRowField, Boolean) -> Unit,
    voice: Voice,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(voice.shareSectionsPickerLabel, style = MaterialTheme.typography.labelLarge)
        availableHistoryRowFields(case).forEach { field ->
            ToggleRow(
                label = historyShareFieldLabel(field, voice),
                checked = field in selectedFields,
                onCheckedChange = { onFieldToggle(field, it) },
                modifier = Modifier.testTag(HISTORY_SHARE_FIELD_TOGGLE_TAG_PREFIX + field.name),
            )
        }
    }
}

/**
 * Prefix for each field-toggle row's `testTag` — clicking by tag rather than by label text, same
 * as Big Picture's own `BIG_PICTURE_DETAIL_TOGGLE_TAG_PREFIX` and the History tab's own
 * `HISTORY_FIELD_TOGGLE_TAG_PREFIX`, since the label/Switch semantics don't reliably merge into
 * one clickable node in every context.
 */
internal const val HISTORY_SHARE_FIELD_TOGGLE_TAG_PREFIX = "history_share_field_toggle_"

/** Notes/Tags always offered; Duration/Intensity only when the Case tracks them — same gating shape as [availableShareSections][com.secondmonday.hodith.viewmodel.availableShareSections]. */
private fun availableHistoryRowFields(case: CaseEntity): List<HistoryRowField> =
    buildList {
        add(HistoryRowField.NOTES)
        add(HistoryRowField.TAGS)
        if (case.tracksDuration) add(HistoryRowField.DURATION)
        if (case.intensityEnabled) add(HistoryRowField.INTENSITY)
    }

private fun historyShareFieldLabel(
    field: HistoryRowField,
    voice: Voice,
): String =
    when (field) {
        HistoryRowField.NOTES -> voice.shareHistoryFieldNotesLabel
        HistoryRowField.TAGS -> voice.shareHistoryFieldTagsLabel
        HistoryRowField.DURATION -> voice.insightsSectionLabelDuration
        HistoryRowField.INTENSITY -> voice.insightsSectionLabelIntensity
    }
