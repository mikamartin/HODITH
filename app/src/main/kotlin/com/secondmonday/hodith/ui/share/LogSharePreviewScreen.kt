package com.secondmonday.hodith.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.ui.common.DateRangePickerRow
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.LogShareSelection
import com.secondmonday.hodith.viewmodel.LogShareUiState
import com.secondmonday.hodith.viewmodel.LogShareViewModel
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.logShareCardState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private const val SHARE_MIME_TYPE = "image/png"

@Composable
fun LogSharePreviewRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogShareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collectLatest { uri ->
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = SHARE_MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    LogSharePreviewScreen(
        uiState = uiState,
        now = viewModel.nowMillis(),
        graphicsLayer = graphicsLayer,
        onBack = onBack,
        onFormatSelect = viewModel::setFormat,
        onSortOrderSelect = viewModel::setSortOrder,
        onDateFromPicked = viewModel::setDateFrom,
        onDateToPicked = viewModel::setDateTo,
        onFieldToggle = viewModel::setFieldSelected,
        onShareClick = { scope.launch { viewModel.share(graphicsLayer.toImageBitmap().asAndroidBitmap()) } },
        modifier = modifier,
    )
}

/**
 * Log Share's screen deliberately reorders [SharePreviewScreen]'s layout — controls first, preview
 * second — since a long log's rows would otherwise crowd the filters below the fold (spec §13
 * design pass, `docs/mockups/log-share-prototype.html`). The preview is capped the same way the
 * final card is (`logShareCardState`), with a match-count line so it's clear when the range holds
 * more than fits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSharePreviewScreen(
    uiState: LogShareUiState,
    now: Long,
    graphicsLayer: GraphicsLayer,
    onBack: () -> Unit,
    onFormatSelect: (ShareCardFormat) -> Unit,
    onSortOrderSelect: (ChronologicalOrder) -> Unit,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    onFieldToggle: (LogRowField, Boolean) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val use24Hour = LocalTimeFormat.current.is24Hour
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.shareChooserLogOption) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        val case = uiState.case
        if (uiState.isLoading || case == null) return@Scaffold

        val selection = uiState.selection

        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SortSection(selection.sortOrder, onSortOrderSelect, voice)
            DateRangeSection(selection, now, zone, onDateFromPicked, onDateToPicked, voice)
            FieldsSection(case, selection.fields, onFieldToggle, voice)

            SegmentedChoiceRow(
                options =
                    listOf(
                        ShareCardFormat.STORY to voice.shareFormatStoryLabel,
                        ShareCardFormat.SQUARE to voice.shareFormatSquareLabel,
                    ),
                selected = selection.format,
                onSelect = onFormatSelect,
            )

            val cardData =
                logShareCardState(
                    case = case,
                    displayName = case.name,
                    events = uiState.events,
                    format = selection.format,
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

            // The card's own truncation note (rendered inside ShareCardTemplate, spec §13) already
            // says when the range holds more than the cap -- no separate screen-level caption needed.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShareCardTemplate(
                    data = cardData,
                    voice = voice,
                    modifier =
                        Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                )
            }

            Button(onClick = onShareClick, modifier = Modifier.fillMaxWidth()) {
                Text(voice.shareLogButtonLabel)
            }
        }
    }
}

@Composable
private fun SortSection(
    sortOrder: ChronologicalOrder,
    onSortOrderSelect: (ChronologicalOrder) -> Unit,
    voice: Voice,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(voice.shareSectionsPickerLabel, style = MaterialTheme.typography.labelLarge)
        SegmentedChoiceRow(
            options =
                listOf(
                    ChronologicalOrder.NEWEST_FIRST to voice.shareLogSortNewestLabel,
                    ChronologicalOrder.OLDEST_FIRST to voice.shareLogSortOldestLabel,
                ),
            selected = sortOrder,
            onSelect = onSortOrderSelect,
        )
    }
}

@Composable
private fun DateRangeSection(
    selection: LogShareSelection,
    now: Long,
    zone: ZoneId,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    voice: Voice,
) {
    DateRangePickerRow(
        dateFrom = selection.dateFrom,
        dateTo = selection.dateTo,
        now = now,
        zone = zone,
        onDateFromChange = onDateFromPicked,
        onDateToChange = { picked -> if (picked != null) onDateToPicked(picked) },
        voice = voice,
    )
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

/** Notes/Tags always offered; Duration/Intensity only when the Case tracks them — same gating shape as [availableSections]. */
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
