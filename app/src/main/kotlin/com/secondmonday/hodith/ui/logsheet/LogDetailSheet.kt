package com.secondmonday.hodith.ui.logsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.MaxDateBoundDatePickerDialog
import com.secondmonday.hodith.ui.common.filterDigitInput
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.TimeEditRejection
import com.secondmonday.hodith.viewmodel.applyEndedAt
import com.secondmonday.hodith.viewmodel.applyPickedDate
import com.secondmonday.hodith.viewmodel.applyPickedTime
import com.secondmonday.hodith.viewmodel.draftFrom
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatEventTimeOfDay
import com.secondmonday.hodith.viewmodel.startedAgain
import com.secondmonday.hodith.viewmodel.stoppedNow
import com.secondmonday.hodith.viewmodel.toDatePickerUtcMillis
import com.secondmonday.hodith.viewmodel.validateEndEdit
import com.secondmonday.hodith.viewmodel.validateStartEdit
import com.secondmonday.hodith.viewmodel.withDurationInput
import java.time.Instant
import java.time.ZoneId

private val INTENSITY_RANGE = 1..5
private val INTENSITY_CHOICE_SIZE = 48.dp
private const val DURATION_FIELD_MAX_DIGITS = 4

/**
 * The bottom-sheet container for logging a *new* event — quick-log from Home, retro-log from
 * the Case Detail FAB, and the widget trampoline (spec §6, "≤5 seconds to complete"). Editing
 * an existing event is [LogDetailScreen] instead, a full screen matching `CaseEditScreen` so
 * the two edit flows read the same way. Both wrap the shared [LogDetailForm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailSheet(
    tracksDuration: Boolean,
    intensityEnabled: Boolean,
    initialDraft: LogDraft,
    tagSuggestions: List<TagEntity>,
    now: Long,
    onSave: (LogDraft) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        LogDetailForm(
            isEditing = false,
            tracksDuration = tracksDuration,
            intensityEnabled = intensityEnabled,
            initialDraft = initialDraft,
            tagSuggestions = tagSuggestions,
            now = now,
            onSave = onSave,
            // The sheet's drag handle provides the top gap; keep the wider bottom inset it had.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(voice.logSheetNewEventTitle, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * The event form itself — every field from spec §6 (time, the unified duration/ongoing control
 * when [tracksDuration], intensity, note, tags) plus the primary save button and the date/time
 * picker dialogs — with no container chrome of its own. [LogDetailSheet] wraps it in a
 * `ModalBottomSheet` (new events); [LogDetailScreen] wraps it in a `Scaffold` + `TopAppBar`
 * (editing an existing event). All transient edit state (the working [LogDraft], the tag input,
 * which picker is open) lives here so both wrappers stay thin; the wrapper supplies the
 * [header] slot (a sheet title, or nothing when a `TopAppBar` already carries it) and reacts to
 * [onSave].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogDetailForm(
    isEditing: Boolean,
    tracksDuration: Boolean,
    intensityEnabled: Boolean,
    initialDraft: LogDraft,
    tagSuggestions: List<TagEntity>,
    now: Long,
    onSave: (LogDraft) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val voice = LocalVoice.current
    val zone = ZoneId.systemDefault()
    // A new event on an intensity-enabled case starts pre-rated (spec §6) rather than letting Save
    // through with no rating at all. Editing an existing event never gets this default — its
    // stored intensity (including a legitimate null from before the case tracked this) is shown
    // exactly as-is, so opening an old event for an unrelated edit can't silently fabricate a rating.
    var draft by remember {
        mutableStateOf(
            if (!isEditing && intensityEnabled) initialDraft.copy(intensity = INTENSITY_RANGE.first) else initialDraft,
        )
    }
    var tagInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    // Set by DateTimePickers' onConfirm when a picked value is rejected (applied to the field
    // regardless, per spec — no revert); cleared on that field's own next successful edit, or on
    // a successful edit of the *other* field, since that can resolve the reason the rejected pick
    // was invalid. Save stays disabled below while either is non-null.
    var startNotice by remember { mutableStateOf<TimeEditRejection?>(null) }
    var endNotice by remember { mutableStateOf<TimeEditRejection?>(null) }
    val canSave = startNotice == null && endNotice == null

    Column(
        modifier =
            modifier
                .padding(contentPadding)
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // fill = false normally: the field area wraps its own content, so a short form still
        // sizes the ModalBottomSheet compactly instead of stretching it to full height. Once the
        // IME opens, fill = true instead — the field area is forced to occupy the whole
        // (IME-shrunk) space, docking Save to the bottom of what's visible above the keyboard
        // rather than leaving it floating right after a short field list with dead space below.
        val imeVisible = WindowInsets.isImeVisible
        Column(
            modifier = Modifier.weight(1f, fill = imeVisible).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            header()

            TimeSection(
                occurredAt = draft.occurredAt,
                zone = zone,
                // "Started" once there's an end to pair it with (spec §6 Story U2); a Case that
                // doesn't track duration has no start/end pair at all, so it keeps the plain "When".
                label = if (tracksDuration) voice.logSheetStartedLabel else voice.logSheetTimeLabel,
                notice = startNotice?.let { noticeText(it, voice) },
                onDateClick = { showDatePicker = true },
                onTimeClick = { showTimePicker = true },
            )

            if (tracksDuration) {
                UnifiedDurationSection(
                    draft = draft,
                    now = now,
                    zone = zone,
                    voice = voice,
                    notice = endNotice?.let { noticeText(it, voice) },
                    onStopClick = {
                        draft = draft.stoppedNow(now)
                        startNotice = null
                        endNotice = null
                    },
                    onStartAgainClick = {
                        draft = draft.startedAgain()
                        startNotice = null
                        endNotice = null
                    },
                    onDaysChange = { input ->
                        draft =
                            draft.withDurationInput(days = filterDigitInput(input, maxDigits = DURATION_FIELD_MAX_DIGITS))
                    },
                    onHoursChange = { input ->
                        draft =
                            draft.withDurationInput(hours = filterDigitInput(input, maxDigits = DURATION_FIELD_MAX_DIGITS))
                    },
                    onMinutesChange = { input ->
                        draft =
                            draft.withDurationInput(minutes = filterDigitInput(input, maxDigits = DURATION_FIELD_MAX_DIGITS))
                    },
                    onEndDateClick = { showEndDatePicker = true },
                    onEndTimeClick = { showEndTimePicker = true },
                )
            }

            if (intensityEnabled) {
                IntensitySection(
                    label = voice.logSheetIntensityLabel,
                    selected = draft.intensity,
                    onSelect = { value -> draft = draft.copy(intensity = value) },
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
        }

        Button(onClick = { onSave(draft) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
            Text(if (isEditing) voice.logSheetSaveButton else voice.logSheetCreateAction)
        }
    }

    DateTimePickers(
        value = draft.occurredAt,
        zone = zone,
        maxDateUtcMillis = toDatePickerUtcMillis(now, zone),
        voice = voice,
        showDatePicker = showDatePicker,
        showTimePicker = showTimePicker,
        onDismissDatePicker = { showDatePicker = false },
        onDismissTimePicker = { showTimePicker = false },
        onValueChange = { draft = draft.copy(occurredAt = it) },
        validate = { candidate -> validateStartEdit(candidate, draft.endedAt, now) },
        onResult = { rejection ->
            startNotice = rejection
            if (rejection == null) endNotice = null
        },
    )

    // Always mounted, not gated on a stated endedAt — the end-time buttons are visible even while
    // open-ended (spec §6 Story U2), defaulting the picker to "now" until something's actually set.
    DateTimePickers(
        value = draft.endedAt ?: now,
        zone = zone,
        // No ceiling here, unlike the start picker above — a future pick is how a planned/expected
        // duration gets stated.
        maxDateUtcMillis = null,
        voice = voice,
        showDatePicker = showEndDatePicker,
        showTimePicker = showEndTimePicker,
        onDismissDatePicker = { showEndDatePicker = false },
        onDismissTimePicker = { showEndTimePicker = false },
        onValueChange = { applied -> draft = draft.applyEndedAt(applied) },
        validate = { candidate -> validateEndEdit(candidate, draft.occurredAt) },
        onResult = { rejection ->
            endNotice = rejection
            if (rejection == null) startNotice = null
        },
    )
}

/** Shared delete-event confirm — the wrapper owns the show/hide state and the affordance that triggers it. */
@Composable
fun DeleteEventConfirmDialog(
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        title = voice.deleteEventConfirmTitle,
        body = voice.deleteEventConfirmBody,
        confirmLabel = voice.deleteEventConfirmAction,
        cancelLabel = voice.deleteEventCancelAction,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * Shared date+time picker pair for a single timestamp field — the start-time and end-time fields
 * don't duplicate the same wiring, just supply which value to edit, how to write it back, and
 * (via [maxDateUtcMillis]) whether a ceiling applies.
 */
@Composable
private fun DateTimePickers(
    value: Long,
    zone: ZoneId,
    maxDateUtcMillis: Long?,
    voice: Voice,
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    onDismissDatePicker: () -> Unit,
    onDismissTimePicker: () -> Unit,
    onValueChange: (Long) -> Unit,
    validate: (candidate: Long) -> TimeEditRejection?,
    onResult: (TimeEditRejection?) -> Unit,
) {
    if (showDatePicker) {
        LogDetailDatePickerDialog(
            occurredAt = value,
            zone = zone,
            maxDateUtcMillis = maxDateUtcMillis,
            voice = voice,
            onDismiss = onDismissDatePicker,
            onConfirm = { picked ->
                val applied = applyPickedDate(value, picked, zone)
                onValueChange(applied)
                onResult(validate(applied))
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
                val applied = applyPickedTime(value, hour, minute, zone)
                onValueChange(applied)
                onResult(validate(applied))
                onDismissTimePicker()
            },
        )
    }
}

private fun noticeText(
    rejection: TimeEditRejection,
    voice: Voice,
): String =
    when (rejection) {
        TimeEditRejection.FUTURE -> voice.logSheetFutureTimeNotice
        TimeEditRejection.AFTER_END -> voice.logSheetStartAfterEndNotice
        TimeEditRejection.BEFORE_START -> voice.logSheetEndBeforeStartNotice
    }

@Composable
private fun TimeSection(
    occurredAt: Long,
    zone: ZoneId,
    label: String,
    notice: String?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    val use24Hour = LocalTimeFormat.current.is24Hour
    // Rectangular, matching the Duration fields' own shape token (OutlinedTextField's default) —
    // M3's button default is a fully-rounded pill, which reads as a different control family
    // sitting right next to the rectangular Duration fields otherwise.
    val fieldShape = MaterialTheme.shapes.extraSmall
    // These read as fields, not actions, so their content uses the fields' own regular-weight
    // text (matching the Duration fields' bodyMedium) rather than every button's bold labelLarge.
    val fieldTextStyle = MaterialTheme.typography.bodyMedium
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedButton(onClick = onDateClick, shape = fieldShape) {
                Text(formatEventDate(occurredAt, zone), style = fieldTextStyle)
            }
            OutlinedButton(onClick = onTimeClick, shape = fieldShape) {
                Text(formatEventTimeOfDay(occurredAt, use24Hour, zone), style = fieldTextStyle)
            }
        }
        if (notice != null) {
            Text(notice, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * The merged Log/Edit Event duration control (spec §6 Story U2): a status row that's either "Ongoing" + Stop or
 * "Ended" + Start again (one flipping row, not a separate switch) — "Ongoing" is *derived* from
 * [LogDraft.endedAt] vs [now] (null or still in the future both read as ongoing, exactly like
 * [com.secondmonday.hodith.domain.isStillRunning]), not a separately-toggled flag. While ongoing,
 * a plain note under the status row names how long it's actually been running, independent of
 * anything stated below.
 *
 * The end-time date/time buttons and the Duration fields (Days/Hours/Minutes) are always visible,
 * but stay empty/unset until the user actually does something: typing a value, picking an end
 * date/time, or tapping Stop (which commits the real elapsed duration). Nothing is ever
 * auto-filled with a live-elapsed guess. Typing a duration or picking an end date/time directly is
 * how a planned/expected one gets stated: it computes [LogDraft.endedAt], and if that lands in the
 * future the control stays Ongoing; if it's already passed, the control reads Ended. The end-time
 * label covers the two states worth naming (nothing stated / stated-and-future) — once Ended, the
 * status row's own "Ended" word already says so, so no separate "Ended at" label repeats it. The
 * Duration fields' own label reads "Planned duration" while Ongoing vs plain "Duration" once
 * Ended. Tapping "Start again" clears both back to empty.
 */
@Composable
private fun UnifiedDurationSection(
    draft: LogDraft,
    now: Long,
    zone: ZoneId,
    voice: Voice,
    notice: String?,
    onStopClick: () -> Unit,
    onStartAgainClick: () -> Unit,
    onDaysChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onEndDateClick: () -> Unit,
    onEndTimeClick: () -> Unit,
) {
    val use24Hour = LocalTimeFormat.current.is24Hour
    val endedAt = draft.endedAt
    val isOngoing = endedAt?.let { it > now } ?: true
    val displayEndedAt = endedAt ?: now
    // Shared by the end date/time buttons and the Duration fields below, so every field in this
    // control reads as one rectangular family rather than mixing M3's pill-shaped button default
    // with the text fields' own more-rectangular shape.
    val fieldShape = MaterialTheme.shapes.extraSmall
    // These read as fields, not actions, so their content uses the fields' own regular-weight
    // text (matching the Duration fields' bodyMedium) rather than every button's bold labelLarge.
    val fieldTextStyle = MaterialTheme.typography.bodyMedium

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same text style the start-time label uses, so this reads as a label, not a heading.
            Text(
                if (isOngoing) voice.logSheetOngoingLabel else voice.logSheetEndedStatusLabel,
                style = MaterialTheme.typography.labelLarge,
            )
            // Stop is the primary, more consequential action (filled); Start again just reopens a
            // past event, so it stays outlined rather than matching Stop's visual weight — but
            // still a real button (default rounded shape), not one of the rectangular fields below.
            if (isOngoing) {
                Button(onClick = onStopClick) { Text(voice.logSheetStopNowAction) }
            } else {
                OutlinedButton(onClick = onStartAgainClick) { Text(voice.logSheetStartAgainAction) }
            }
        }

        if (isOngoing) {
            // How long it's actually been running so far — independent of whatever's (or isn't)
            // typed in the Duration fields below, which may state a different planned length.
            // Styled as a muted caption (matching OngoingIndicator's own elapsed text) so it reads
            // as a secondary status note, not another line of equal-weight body text.
            Text(
                voice.logSheetOngoingForNotice(formatElapsedDuration(draft.occurredAt, now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Two end-time states worth a label: nothing stated yet, or a stated end still ahead
            // of now (still Ongoing, so naming it "set to end" means something). Once Ended, the
            // status row above already reads "Ended" — a third "Ended at" label here would just
            // repeat it right above the same date/time buttons, so that state shows no label at all.
            val endTimeLabel =
                when {
                    endedAt == null -> voice.logSheetEndTimeUnsetLabel
                    isOngoing -> voice.logSheetEndTimeFutureLabel
                    else -> null
                }
            if (endTimeLabel != null) {
                Text(endTimeLabel, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEndDateClick, shape = fieldShape) {
                    Text(
                        if (endedAt != null) formatEventDate(displayEndedAt, zone) else voice.logSheetChooseDateAction,
                        style = fieldTextStyle,
                    )
                }
                OutlinedButton(onClick = onEndTimeClick, shape = fieldShape) {
                    Text(
                        if (endedAt != null) formatEventTimeOfDay(displayEndedAt, use24Hour, zone) else voice.logSheetChooseTimeAction,
                        style = fieldTextStyle,
                    )
                }
            }
            if (notice != null) {
                Text(notice, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            // "Planned duration" while Ongoing (whatever's typed is a projection, not a fact yet);
            // plain "Duration" once Ended.
            Text(
                if (isOngoing) voice.logSheetPlannedDurationLabel else voice.logSheetDurationLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                // A bit more breathing room than the column's own 8dp rhythm, so the Duration
                // label reads as this row's heading rather than just another evenly-spaced line.
                modifier = Modifier.padding(top = 4.dp),
            ) {
                DurationFieldInput(
                    value = draft.daysInput,
                    label = voice.logSheetDurationDaysLabel,
                    onValueChange = onDaysChange,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
                DurationFieldInput(
                    value = draft.hoursInput,
                    label = voice.logSheetDurationHoursLabel,
                    onValueChange = onHoursChange,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
                DurationFieldInput(
                    value = draft.minutesInput,
                    label = voice.logSheetDurationMinutesLabel,
                    onValueChange = onMinutesChange,
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A compact outlined field for the Days/Hours/Minutes row — the plain [OutlinedTextField]
 * constructor's content padding makes three of them side by side read noticeably bigger than a
 * single short number needs, so this trims padding via the decoration-box API instead. [shape]
 * matches the end date/time buttons' own shape, so the whole row reads as one rectangular family.
 */
@Composable
private fun DurationFieldInput(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = shape,
                    )
                },
            )
        },
    )
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
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier =
            Modifier
                .size(INTENSITY_CHOICE_SIZE)
                .background(background, CircleShape)
                .border(2.dp, ringColor, CircleShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = value.toString(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LogDetailDatePickerDialog(
    occurredAt: Long,
    zone: ZoneId,
    maxDateUtcMillis: Long?,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (pickedUtcMillis: Long) -> Unit,
) {
    if (maxDateUtcMillis != null) {
        MaxDateBoundDatePickerDialog(
            initialDateUtcMillis = toDatePickerUtcMillis(occurredAt, zone),
            maxDateUtcMillis = maxDateUtcMillis,
            voice = voice,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    } else {
        UnboundedDatePickerDialog(
            initialDateUtcMillis = toDatePickerUtcMillis(occurredAt, zone),
            voice = voice,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/**
 * A plain `DatePickerDialog` + `DatePicker` with no ceiling at all — the end-time date picker
 * (spec §6 Story U2): a future pick is how a planned/expected duration gets stated, and a past
 * pick (even one before the start time, which [validateEndEdit] then rejects) is how a real
 * historical end gets corrected. Unlike [MaxDateBoundDatePickerDialog], which every other date
 * picker in the sheet still uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnboundedDatePickerDialog(
    initialDateUtcMillis: Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (pickedUtcMillis: Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateUtcMillis)
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
 * against, unlike `DatePicker`'s `selectableDates`. The caller validates the result after
 * [onConfirm] fires instead, applying it regardless and surfacing a warning if it's invalid
 * (same-day future times are the only case this matters for the start field, since
 * [LogDetailDatePickerDialog] already rules out future dates there; the end field has no such
 * restriction at all).
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
    val timePickerState: TimePickerState =
        rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = LocalTimeFormat.current.is24Hour,
        )
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

/** Selected vs. unselected side by side, so the ring is visible against each theme's own colors. */
@Composable
private fun IntensityChoicePreviewContent() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        IntensityChoice(value = 3, selected = true, onClick = {})
        IntensityChoice(value = 3, selected = false, onClick = {})
    }
}

@Preview(name = "IntensityChoice — Plain light", showBackground = true, widthDp = 160)
@Composable
private fun IntensityChoicePlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) { IntensityChoicePreviewContent() }
}

@Preview(name = "IntensityChoice — Plain dark", showBackground = true, widthDp = 160)
@Composable
private fun IntensityChoicePlainDarkPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = true) { IntensityChoicePreviewContent() }
}

@Preview(name = "IntensityChoice — Intense", showBackground = true, widthDp = 160)
@Composable
private fun IntensityChoiceIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) { IntensityChoicePreviewContent() }
}

@Preview(name = "IntensityChoice — Bright", showBackground = true, widthDp = 160)
@Composable
private fun IntensityChoiceBrightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) { IntensityChoicePreviewContent() }
}

/**
 * Exercises [UnifiedDurationSection] across its three end-time states (open-ended, planned/future,
 * Ended) — the same validation shape `DurationSectionPreviewContent` gave the old single-field
 * control.
 */
@Composable
private fun UnifiedDurationSectionPreviewContent(
    startedAt: Long,
    initialEndedAt: Long?,
) {
    val now = remember { System.currentTimeMillis() }
    var draft by remember { mutableStateOf(draftFrom(event = null, now = startedAt).applyEndedAt(initialEndedAt)) }
    Column(modifier = Modifier.padding(16.dp)) {
        UnifiedDurationSection(
            draft = draft,
            now = now,
            zone = ZoneId.systemDefault(),
            voice = LocalVoice.current,
            notice = null,
            onStopClick = { draft = draft.stoppedNow(now) },
            onStartAgainClick = { draft = draft.startedAgain() },
            onDaysChange = { input -> draft = draft.withDurationInput(days = input) },
            onHoursChange = { input -> draft = draft.withDurationInput(hours = input) },
            onMinutesChange = { input -> draft = draft.withDurationInput(minutes = input) },
            onEndDateClick = {},
            onEndTimeClick = {},
        )
    }
}

@Preview(name = "UnifiedDurationSection — open-ended", showBackground = true, widthDp = 380)
@Composable
private fun UnifiedDurationSectionOpenEndedPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalVoice provides voiceFor(AppTheme.PLAIN)) {
            UnifiedDurationSectionPreviewContent(startedAt = System.currentTimeMillis() - 2 * 60 * 60 * 1000L, initialEndedAt = null)
        }
    }
}

@Preview(name = "UnifiedDurationSection — planned (future)", showBackground = true, widthDp = 380)
@Composable
private fun UnifiedDurationSectionPlannedPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalVoice provides voiceFor(AppTheme.PLAIN)) {
            val startedAt = System.currentTimeMillis()
            UnifiedDurationSectionPreviewContent(startedAt = startedAt, initialEndedAt = startedAt + 2 * 60 * 60 * 1000L)
        }
    }
}

@Preview(name = "UnifiedDurationSection — ended", showBackground = true, widthDp = 380)
@Composable
private fun UnifiedDurationSectionEndedPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalVoice provides voiceFor(AppTheme.PLAIN)) {
            val startedAt = System.currentTimeMillis() - 3 * 60 * 60 * 1000L
            UnifiedDurationSectionPreviewContent(startedAt = startedAt, initialEndedAt = startedAt + 90 * 60 * 1000L)
        }
    }
}
