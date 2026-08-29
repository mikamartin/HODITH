package com.secondmonday.hodith.ui.logsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.filterDigitInput
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.applyPickedDate
import com.secondmonday.hodith.viewmodel.applyPickedTime
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatEventTimeOfDay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private val INTENSITY_RANGE = 1..5
private val INTENSITY_CHOICE_SIZE = 48.dp
private const val DURATION_MINUTES_MAX_DIGITS = 5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogDetailSheet(
    isEditing: Boolean,
    durationMode: DurationMode,
    intensityEnabled: Boolean,
    initialDraft: LogDraft,
    tagSuggestions: List<TagEntity>,
    now: Long,
    onSave: (LogDraft) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val voice = LocalVoice.current
    val zone = ZoneId.systemDefault()
    var draft by remember { mutableStateOf(initialDraft) }
    var tagInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SheetHeader(
                title = if (isEditing) voice.logSheetEditEventTitle else voice.logSheetNewEventTitle,
                onDeleteClick = onDelete?.let { { showDeleteConfirm = true } },
                deleteDescription = voice.deleteEventConfirmAction,
            )

            TimeSection(
                occurredAt = draft.occurredAt,
                zone = zone,
                label = voice.logSheetTimeLabel,
                onDateClick = { showDatePicker = true },
                onTimeClick = { showTimePicker = true },
            )

            if (durationMode == DurationMode.START_STOP) {
                EndTimeSection(
                    endedAt = draft.endedAt,
                    zone = zone,
                    voice = voice,
                    onStopNowClick = { draft = draft.copy(endedAt = now) },
                    onBackToOngoingClick = { draft = draft.copy(endedAt = null) },
                    onDateClick = { showEndDatePicker = true },
                    onTimeClick = { showEndTimePicker = true },
                )
            }

            if (intensityEnabled) {
                IntensitySection(
                    label = voice.logSheetIntensityLabel,
                    selected = draft.intensity,
                    onSelect = { value -> draft = draft.copy(intensity = if (draft.intensity == value) null else value) },
                )
            }

            if (durationMode == DurationMode.MANUAL) {
                OutlinedTextField(
                    value = draft.durationMinutes,
                    onValueChange = { draft = draft.copy(durationMinutes = filterDigitInput(it, maxDigits = DURATION_MINUTES_MAX_DIGITS)) },
                    label = { Text(voice.logSheetDurationLabel) },
                    placeholder = { Text(voice.logSheetDurationHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = { Text(voice.logSheetNoteLabel) },
                placeholder = { Text(voice.logSheetNoteHint) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            TagsSection(
                label = voice.logSheetTagsLabel,
                selectedTags = draft.tags,
                suggestions = tagSuggestions.map { it.name },
                tagInput = tagInput,
                onTagInputChange = { tagInput = it },
                onAddTag = { name ->
                    tagToAdd(name, draft.tags)?.let { tag -> draft = draft.copy(tags = draft.tags + tag) }
                    tagInput = ""
                },
                onRemoveTag = { name -> draft = draft.copy(tags = draft.tags - name) },
                voice = voice,
            )

            val isStarting = durationMode == DurationMode.START_STOP && !isEditing && draft.endedAt == null
            Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isStarting) voice.logSheetStartButton else voice.logSheetSaveButton)
            }
        }
    }

    DateTimePickers(
        value = draft.occurredAt,
        zone = zone,
        now = now,
        voice = voice,
        showDatePicker = showDatePicker,
        showTimePicker = showTimePicker,
        onDismissDatePicker = { showDatePicker = false },
        onDismissTimePicker = { showTimePicker = false },
        onValueChange = { draft = draft.copy(occurredAt = it) },
    )

    draft.endedAt?.let { endedAt ->
        DateTimePickers(
            value = endedAt,
            zone = zone,
            now = now,
            voice = voice,
            showDatePicker = showEndDatePicker,
            showTimePicker = showEndTimePicker,
            onDismissDatePicker = { showEndDatePicker = false },
            onDismissTimePicker = { showEndTimePicker = false },
            onValueChange = { draft = draft.copy(endedAt = it) },
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        ConfirmDialog(
            title = voice.deleteEventConfirmTitle,
            body = voice.deleteEventConfirmBody,
            confirmLabel = voice.deleteEventConfirmAction,
            cancelLabel = voice.deleteEventCancelAction,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }
}

/**
 * Shared date+time picker pair for a single timestamp field, extracted so the start-time and
 * (`START_STOP`-only) end-time fields don't duplicate the same wiring — both just supply which
 * value to edit and how to write it back.
 */
@Composable
private fun DateTimePickers(
    value: Long,
    zone: ZoneId,
    now: Long,
    voice: Voice,
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    onDismissDatePicker: () -> Unit,
    onDismissTimePicker: () -> Unit,
    onValueChange: (Long) -> Unit,
) {
    if (showDatePicker) {
        LogDetailDatePickerDialog(
            occurredAt = value,
            zone = zone,
            now = now,
            voice = voice,
            onDismiss = onDismissDatePicker,
            onConfirm = { picked ->
                onValueChange(applyPickedDate(value, picked, zone).coerceAtMost(now))
                onDismissDatePicker()
            },
        )
    }

    if (showTimePicker) {
        LogDetailTimePickerDialog(
            occurredAt = value,
            zone = zone,
            voice = voice,
            onDismiss = onDismissTimePicker,
            onConfirm = { hour, minute ->
                onValueChange(applyPickedTime(value, hour, minute, zone).coerceAtMost(now))
                onDismissTimePicker()
            },
        )
    }
}

@Composable
private fun SheetHeader(
    title: String,
    onDeleteClick: (() -> Unit)?,
    deleteDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = deleteDescription)
            }
        }
    }
}

@Composable
private fun TimeSection(
    occurredAt: Long,
    zone: ZoneId,
    label: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedButton(onClick = onDateClick) { Text(formatEventDate(occurredAt, zone)) }
            OutlinedButton(onClick = onTimeClick) { Text(formatEventTimeOfDay(occurredAt, zone)) }
        }
    }
}

/**
 * `START_STOP`-only mirror of [TimeSection] (spec §6: "duration or Start, per durationMode").
 * Leaving [endedAt] null (the default for a fresh Start) IS the ongoing state — there's no
 * separate "Start" screen, just this section left alone. "Stop now" and the date/time buttons
 * both funnel into the same [onDateClick]/[onTimeClick]-editable value the sheet already has
 * pickers for via [draft.endedAt][com.secondmonday.hodith.viewmodel.LogDraft.endedAt].
 * "Back to ongoing" ([onBackToOngoingClick]) is the reverse of "Stop now" — clears the end
 * time so a too-hastily-stopped event (or one that stopped and restarted as the same
 * occurrence) returns to running.
 */
@Composable
private fun EndTimeSection(
    endedAt: Long?,
    zone: ZoneId,
    voice: Voice,
    onStopNowClick: () -> Unit,
    onBackToOngoingClick: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Column {
        Text(voice.logSheetEndLabel, style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (endedAt == null) {
                Text(voice.logSheetOngoingLabel, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onStopNowClick) { Text(voice.logSheetStopNowAction) }
            } else {
                OutlinedButton(onClick = onDateClick) { Text(formatEventDate(endedAt, zone)) }
                OutlinedButton(onClick = onTimeClick) { Text(formatEventTimeOfDay(endedAt, zone)) }
            }
        }
        if (endedAt != null) {
            TextButton(onClick = onBackToOngoingClick, modifier = Modifier.padding(top = 4.dp)) {
                Text(voice.logSheetBackToOngoingAction)
            }
        }
    }
}

@Composable
private fun IntensitySection(
    label: String,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp).selectableGroup(),
        ) {
            INTENSITY_RANGE.forEach { value ->
                IntensityChoice(value = value, selected = selected == value, onClick = { onSelect(value) })
            }
        }
    }
}

@Composable
private fun IntensityChoice(
    value: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier =
            Modifier
                .size(INTENSITY_CHOICE_SIZE)
                .background(background, CircleShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = value.toString(), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailDatePickerDialog(
    occurredAt: Long,
    zone: ZoneId,
    now: Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (pickedUtcMillis: Long) -> Unit,
) {
    val todayUtcMillis =
        Instant
            .ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    val initialSelectedDateMillis =
        Instant
            .ofEpochMilli(occurredAt)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    val datePickerState: DatePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
            selectableDates =
                remember(todayUtcMillis) {
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
                    }
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let(onConfirm)
                onDismiss()
            }) { Text(voice.logSheetPickerConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.logSheetPickerCancel) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * No future-time restriction here — M3's `TimePicker` has no "selectable time" API to restrict
 * against, unlike `DatePicker`'s `selectableDates`. The caller clamps the result to `now` after
 * [onConfirm] fires instead (same-day future times are the only case this matters for, since
 * [LogDetailDatePickerDialog] already rules out future dates).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailTimePickerDialog(
    occurredAt: Long,
    zone: ZoneId,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val current = Instant.ofEpochMilli(occurredAt).atZone(zone)
    val timePickerState: TimePickerState = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) { Text(voice.logSheetPickerConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.logSheetPickerCancel) }
        },
        text = { TimePicker(state = timePickerState) },
    )
}

@Composable
private fun TagsSection(
    label: String,
    selectedTags: List<String>,
    suggestions: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    voice: Voice,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        TagEditor(
            selectedTags = selectedTags,
            suggestions = suggestions,
            tagInput = tagInput,
            onTagInputChange = onTagInputChange,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            voice = voice,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(
    selectedTags: List<String>,
    suggestions: List<String>,
    tagInput: String,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    voice: Voice,
) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedTags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = voice.logSheetRemoveTagDescription,
                                modifier = Modifier.size(InputChipDefaults.AvatarSize),
                            )
                        },
                    )
                }
            }
        }

        val remainingSuggestions = filterTagSuggestions(suggestions, selectedTags, tagInput)
        if (remainingSuggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                remainingSuggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { onAddTag(suggestion) }, label = { Text(suggestion) })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = onTagInputChange,
                placeholder = { Text(voice.logSheetAddTagHint) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAddTag(tagInput) }) {
                Icon(Icons.Filled.Add, contentDescription = voice.logSheetAddTagHint)
            }
        }
    }
}
