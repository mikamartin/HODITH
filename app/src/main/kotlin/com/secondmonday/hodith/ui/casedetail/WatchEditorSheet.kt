package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.FrequencyPicker
import com.secondmonday.hodith.ui.common.LabelledSection
import com.secondmonday.hodith.ui.common.NumberStepper
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.coerceExpectedPer
import com.secondmonday.hodith.ui.common.filterDigitInput
import com.secondmonday.hodith.ui.common.themedSwitchColors
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.THRESHOLD_RANGE

internal enum class WindowPreset { SHORT, LONG, CUSTOM }

private const val DEFAULT_OFTEN_THRESHOLD = 5
private const val DEFAULT_QUIET_THRESHOLD = 14
private const val DEFAULT_CUSTOM_WINDOW_DAYS = 45
private const val CUSTOM_WINDOW_MAX_DIGITS = 3
private const val DEFAULT_MIN_INTENSITY = 1

/**
 * The lookback window only means something relative to the rate it's checking (spec §8): a
 * 14/30-day window suits a weekly rate, but reading a "per 3 months" rate over just 14 days would
 * be judging it on a slice too short to mean anything. [SHORT]/[LONG] scale with [ExpectedPer] so
 * the two presets always stay sane for whichever rate the "At least" picker is set to.
 */
private fun windowDaysFor(
    per: ExpectedPer,
    preset: WindowPreset,
): Int? =
    when (preset) {
        WindowPreset.SHORT ->
            when (per) {
                ExpectedPer.DAY -> 7
                ExpectedPer.WEEK -> 14
                ExpectedPer.MONTH -> 60
                ExpectedPer.QUARTER -> 120
            }
        WindowPreset.LONG ->
            when (per) {
                ExpectedPer.DAY -> 14
                ExpectedPer.WEEK -> 30
                ExpectedPer.MONTH -> 90
                ExpectedPer.QUARTER -> 180
            }
        WindowPreset.CUSTOM -> null
    }

private fun windowPresetLabel(
    per: ExpectedPer,
    preset: WindowPreset,
): String =
    when (preset) {
        WindowPreset.SHORT ->
            when (per) {
                ExpectedPer.DAY -> "7 days"
                ExpectedPer.WEEK -> "14 days"
                ExpectedPer.MONTH -> "2mo"
                ExpectedPer.QUARTER -> "4mo"
            }
        WindowPreset.LONG ->
            when (per) {
                ExpectedPer.DAY -> "14 days"
                ExpectedPer.WEEK -> "30 days"
                ExpectedPer.MONTH -> "Quarter"
                ExpectedPer.QUARTER -> "6mo"
            }
        WindowPreset.CUSTOM -> error("Custom has no fixed label — read it from Voice.watchesWindowCustom instead")
    }

private fun windowPresetFor(
    days: Int?,
    per: ExpectedPer,
): WindowPreset =
    when (days) {
        null -> WindowPreset.SHORT
        windowDaysFor(per, WindowPreset.SHORT) -> WindowPreset.SHORT
        windowDaysFor(per, WindowPreset.LONG) -> WindowPreset.LONG
        else -> WindowPreset.CUSTOM
    }

/**
 * Create/edit sheet for a Watch (spec §11/§14) — [editing] null creates, non-null prefills
 * every field from it and adds a delete action. Extends the old `TriggerCreationSheet`'s kind +
 * threshold/window fields with `FrequencyPickers.kt`'s count+per picker, a 14/30-day lookback
 * preset plus custom (duration Cases only get the Measure picker, shown right after the kind
 * picker since it changes what "At least" can even offer), and an intensity-at-least toggle
 * (intensity Cases only) that only reveals its 1..5 picker once switched on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchEditorSheet(
    voice: Voice,
    durationMode: DurationMode,
    intensityEnabled: Boolean,
    editing: WatchEntity?,
    onDismiss: () -> Unit,
    onSave: (
        kind: WatchKind,
        threshold: Int,
        windowDays: Int?,
        expectedPer: ExpectedPer,
        metric: VerdictMetric,
        minIntensity: Int?,
    ) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(editing?.kind ?: WatchKind.OFTEN) }
    var oftenCount by remember {
        mutableIntStateOf(editing?.takeIf { it.kind == WatchKind.OFTEN }?.threshold ?: DEFAULT_OFTEN_THRESHOLD)
    }
    var quietThreshold by remember {
        mutableIntStateOf(editing?.takeIf { it.kind == WatchKind.QUIET }?.threshold ?: DEFAULT_QUIET_THRESHOLD)
    }
    var metric by remember { mutableStateOf(editing?.metric ?: VerdictMetric.OCCURRENCE_COUNT) }
    var expectedPer by remember { mutableStateOf(editing?.expectedPer ?: ExpectedPer.WEEK) }
    var minIntensity by remember { mutableStateOf(editing?.minIntensity) }
    val initialExpectedPer = editing?.expectedPer ?: ExpectedPer.WEEK
    val initialWindowDays = editing?.takeIf { it.kind == WatchKind.OFTEN }?.windowDays
    var windowPreset by remember { mutableStateOf(windowPresetFor(initialWindowDays, initialExpectedPer)) }
    var customWindowText by remember {
        mutableStateOf(
            (
                initialWindowDays?.takeIf { windowPresetFor(it, initialExpectedPer) == WindowPreset.CUSTOM }
                    ?: DEFAULT_CUSTOM_WINDOW_DAYS
            ).toString(),
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = voice.watchesDeleteConfirmTitle,
            body = voice.watchesDeleteConfirmBody,
            confirmLabel = voice.watchesDeleteConfirmAction,
            cancelLabel = voice.watchesDeleteCancelAction,
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
        if (windowPreset == WindowPreset.CUSTOM) customWindowText.toIntOrNull() else windowDaysFor(activePer, windowPreset)
    val canSave = kind == WatchKind.QUIET || (windowDays != null && windowDays > 0)

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
                    text = if (editing == null) voice.watchesCreateTitle else voice.watchesEditTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (editing != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription =
                                voice.watchDeleteDescription(
                                    voice.watchSummary(editing.kind, editing.threshold, editing.windowDays),
                                ),
                        )
                    }
                }
            }

            Column {
                Text(voice.watchesKindPickerLabel, style = MaterialTheme.typography.labelLarge)
                SegmentedChoiceRow(
                    options =
                        listOf(
                            WatchKind.OFTEN to voice.watchKindLabel(WatchKind.OFTEN),
                            WatchKind.QUIET to voice.watchKindLabel(WatchKind.QUIET),
                        ),
                    selected = kind,
                    onSelect = { kind = it },
                )
            }

            if (kind == WatchKind.OFTEN) {
                if (durationMode.tracksDuration) {
                    Column {
                        Text(voice.watchesMetricLabel, style = MaterialTheme.typography.labelLarge)
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
                LabelledSection(voice.watchesOftenLabel) {
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
                    Text(voice.watchesWindowLabel, style = MaterialTheme.typography.labelLarge)
                    SegmentedChoiceRow(
                        options =
                            listOf(
                                WindowPreset.SHORT to windowPresetLabel(activePer, WindowPreset.SHORT),
                                WindowPreset.LONG to windowPresetLabel(activePer, WindowPreset.LONG),
                                WindowPreset.CUSTOM to voice.watchesWindowCustom,
                            ),
                        selected = windowPreset,
                        onSelect = { windowPreset = it },
                    )
                    if (windowPreset == WindowPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customWindowText,
                            onValueChange = { customWindowText = filterDigitInput(it, maxDigits = CUSTOM_WINDOW_MAX_DIGITS) },
                            label = { Text(voice.watchesWindowCustomHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                if (intensityEnabled) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                voice.watchesIntensityLabel,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = minIntensity != null,
                                onCheckedChange = { checked -> minIntensity = if (checked) DEFAULT_MIN_INTENSITY else null },
                                colors = themedSwitchColors(),
                                modifier = Modifier.semantics { contentDescription = voice.watchesIntensityToggleDescription },
                            )
                        }
                        minIntensity?.let { current ->
                            IntensityAtLeastPicker(
                                selected = current,
                                onSelect = { minIntensity = it },
                                voice = voice,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            } else {
                Column {
                    Text(voice.watchesQuietLabel, style = MaterialTheme.typography.labelLarge)
                    NumberStepper(
                        value = quietThreshold,
                        range = THRESHOLD_RANGE,
                        suffix = voice.watchesQuietSuffix,
                        decreaseDescription = voice.watchesDecreaseCountDescription,
                        increaseDescription = voice.watchesIncreaseCountDescription,
                        onChange = { quietThreshold = it },
                    )
                }
            }

            Button(
                onClick = {
                    val threshold = if (kind == WatchKind.OFTEN) oftenCount else quietThreshold
                    onSave(
                        kind,
                        threshold,
                        if (kind == WatchKind.OFTEN) windowDays else null,
                        if (kind == WatchKind.OFTEN) activePer else ExpectedPer.WEEK,
                        activeMetric,
                        if (kind == WatchKind.OFTEN) activeMinIntensity else null,
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(voice.watchesSaveButton)
            }
        }
    }
}

/**
 * Tapping a level highlights it and every level above it (so 1..5 reads as "this or more"
 * without a "+" suffix cluttering each circle) — [selected] is the current minimum.
 */
@Composable
private fun IntensityAtLeastPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (level in 1..INTENSITY_MAX) {
            val highlighted = level >= selected
            Surface(
                shape = CircleShape,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clickable { onSelect(level) }
                        .semantics { contentDescription = voice.watchesIntensityOptionDescription(level) },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("$level", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
