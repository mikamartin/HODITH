package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.FrequencyPicker
import com.secondmonday.hodith.ui.common.LabelledSection
import com.secondmonday.hodith.ui.common.NumberStepper
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.coerceExpectedPer
import com.secondmonday.hodith.ui.common.filterDigitInput
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.THRESHOLD_RANGE

internal enum class WindowPreset { SEVEN, THIRTY, NINETY, CUSTOM }

private const val DEFAULT_OFTEN_THRESHOLD = 5
private const val DEFAULT_QUIET_THRESHOLD = 14
private const val DEFAULT_CUSTOM_WINDOW_DAYS = 14
private const val SEVEN_DAYS = 7
private const val THIRTY_DAYS = 30
private const val NINETY_DAYS = 90
private const val CUSTOM_WINDOW_MAX_DIGITS = 3

private fun windowPresetFor(days: Int?): WindowPreset =
    when (days) {
        SEVEN_DAYS -> WindowPreset.SEVEN
        THIRTY_DAYS -> WindowPreset.THIRTY
        NINETY_DAYS -> WindowPreset.NINETY
        null -> WindowPreset.SEVEN
        else -> WindowPreset.CUSTOM
    }

/**
 * Create/edit sheet for a Notification (spec §11/§14) — [editing] null creates, non-null prefills
 * every field from it and adds a delete action. Extends the old `TriggerCreationSheet`'s kind +
 * threshold/window fields with `FrequencyPickers.kt`'s count+per picker, a 90-day lookback preset,
 * a metric picker (duration Cases only), and an intensity-at-least picker (intensity Cases only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationEditorSheet(
    voice: Voice,
    durationMode: DurationMode,
    intensityEnabled: Boolean,
    editing: NotificationEntity?,
    onDismiss: () -> Unit,
    onSave: (
        kind: NotificationKind,
        threshold: Int,
        windowDays: Int?,
        expectedPer: ExpectedPer,
        metric: VerdictMetric,
        minIntensity: Int?,
    ) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(editing?.kind ?: NotificationKind.OFTEN) }
    var oftenCount by remember {
        mutableIntStateOf(editing?.takeIf { it.kind == NotificationKind.OFTEN }?.threshold ?: DEFAULT_OFTEN_THRESHOLD)
    }
    var quietThreshold by remember {
        mutableIntStateOf(editing?.takeIf { it.kind == NotificationKind.QUIET }?.threshold ?: DEFAULT_QUIET_THRESHOLD)
    }
    var metric by remember { mutableStateOf(editing?.metric ?: VerdictMetric.OCCURRENCE_COUNT) }
    var expectedPer by remember { mutableStateOf(editing?.expectedPer ?: ExpectedPer.WEEK) }
    var minIntensity by remember { mutableStateOf(editing?.minIntensity) }
    val initialWindowDays = editing?.takeIf { it.kind == NotificationKind.OFTEN }?.windowDays
    var windowPreset by remember { mutableStateOf(windowPresetFor(initialWindowDays)) }
    var customWindowText by remember {
        mutableStateOf(
            (initialWindowDays?.takeIf { windowPresetFor(it) == WindowPreset.CUSTOM } ?: DEFAULT_CUSTOM_WINDOW_DAYS).toString(),
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = voice.notificationsDeleteConfirmTitle,
            body = voice.notificationsDeleteConfirmBody,
            confirmLabel = voice.notificationsDeleteConfirmAction,
            cancelLabel = voice.notificationsDeleteCancelAction,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    // A hidden picker's stale state (e.g. a DAYS_ACTIVE metric prefilled from before the Case
    // stopped tracking duration) never reaches onSave -- these are what's actually rendered/saved.
    val activeMetric = if (durationMode.tracksDuration) metric else VerdictMetric.OCCURRENCE_COUNT
    val activePer = coerceExpectedPer(activeMetric, expectedPer)
    val activeMinIntensity = if (intensityEnabled) minIntensity else null

    val windowDays =
        when (windowPreset) {
            WindowPreset.SEVEN -> SEVEN_DAYS
            WindowPreset.THIRTY -> THIRTY_DAYS
            WindowPreset.NINETY -> NINETY_DAYS
            WindowPreset.CUSTOM -> customWindowText.toIntOrNull()
        }
    val canSave = kind == NotificationKind.QUIET || (windowDays != null && windowDays > 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (editing == null) voice.notificationsCreateTitle else voice.notificationsEditTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (editing != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription =
                                voice.notificationDeleteDescription(
                                    voice.notificationSummary(editing.kind, editing.threshold, editing.windowDays),
                                ),
                        )
                    }
                }
            }

            Column {
                Text(voice.notificationsKindPickerLabel, style = MaterialTheme.typography.labelLarge)
                SegmentedChoiceRow(
                    options =
                        listOf(
                            NotificationKind.OFTEN to voice.notificationKindLabel(NotificationKind.OFTEN),
                            NotificationKind.QUIET to voice.notificationKindLabel(NotificationKind.QUIET),
                        ),
                    selected = kind,
                    onSelect = { kind = it },
                )
            }

            if (kind == NotificationKind.OFTEN) {
                LabelledSection(voice.notificationsOftenLabel) {
                    FrequencyPicker(
                        count = oftenCount,
                        onCountChange = { oftenCount = it },
                        per = activePer,
                        onPerChange = { expectedPer = it },
                        metric = activeMetric,
                        voice = voice,
                    )
                }
                Column {
                    Text(voice.notificationsWindowLabel, style = MaterialTheme.typography.labelLarge)
                    SegmentedChoiceRow(
                        options =
                            listOf(
                                WindowPreset.SEVEN to voice.notificationsWindowSeven,
                                WindowPreset.THIRTY to voice.notificationsWindowThirty,
                                WindowPreset.NINETY to voice.notificationsWindowNinety,
                                WindowPreset.CUSTOM to voice.notificationsWindowCustom,
                            ),
                        selected = windowPreset,
                        onSelect = { windowPreset = it },
                    )
                    if (windowPreset == WindowPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customWindowText,
                            onValueChange = { customWindowText = filterDigitInput(it, maxDigits = CUSTOM_WINDOW_MAX_DIGITS) },
                            label = { Text(voice.notificationsWindowCustomHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                if (durationMode.tracksDuration) {
                    Column {
                        Text(voice.notificationsMetricLabel, style = MaterialTheme.typography.labelLarge)
                        SegmentedChoiceRow(
                            options =
                                listOf(
                                    VerdictMetric.OCCURRENCE_COUNT to voice.metricOccurrenceLabel,
                                    VerdictMetric.DAYS_ACTIVE to voice.metricDaysActiveLabel,
                                ),
                            selected = metric,
                            onSelect = { metric = it },
                        )
                    }
                }
                if (intensityEnabled) {
                    Column {
                        Text(voice.notificationsIntensityLabel, style = MaterialTheme.typography.labelLarge)
                        SegmentedChoiceRow(
                            options =
                                (listOf<Int?>(null) + (1..INTENSITY_MAX)).map { level ->
                                    level to voice.notificationsIntensityOption(level)
                                },
                            selected = minIntensity,
                            onSelect = { minIntensity = it },
                        )
                    }
                }
            } else {
                Column {
                    Text(voice.notificationsQuietLabel, style = MaterialTheme.typography.labelLarge)
                    NumberStepper(
                        value = quietThreshold,
                        range = THRESHOLD_RANGE,
                        suffix = voice.notificationsQuietSuffix,
                        decreaseDescription = voice.notificationsDecreaseCountDescription,
                        increaseDescription = voice.notificationsIncreaseCountDescription,
                        onChange = { quietThreshold = it },
                    )
                }
            }

            Button(
                onClick = {
                    val threshold = if (kind == NotificationKind.OFTEN) oftenCount else quietThreshold
                    onSave(
                        kind,
                        threshold,
                        if (kind == NotificationKind.OFTEN) windowDays else null,
                        if (kind == NotificationKind.OFTEN) activePer else ExpectedPer.WEEK,
                        activeMetric,
                        if (kind == NotificationKind.OFTEN) activeMinIntensity else null,
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(voice.notificationsSaveButton)
            }
        }
    }
}
