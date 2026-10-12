// Throwaway Compose Preview spike for PROGRESS.md Story U1 (duration unification) — not wired to
// any ViewModel, domain code, or production screen. Local `remember` state only. Delete this file
// once the UX is validated and superseded by U2's real implementation (or rejected outright).
package com.secondmonday.hodith.ui.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import com.secondmonday.hodith.ui.common.RowWithInfo
import com.secondmonday.hodith.ui.common.themedSwitchColors
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.applyPickedDate
import com.secondmonday.hodith.viewmodel.applyPickedTime
import com.secondmonday.hodith.viewmodel.durationUnitFor
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatEventTimeOfDay
import com.secondmonday.hodith.viewmodel.toDatePickerUtcMillis
import java.time.Instant
import java.time.ZoneId

private val PROTOTYPE_DURATION_UNITS = listOf(DurationUnit.MINUTES, DurationUnit.HOURS, DurationUnit.DAYS)

private fun prototypeDurationUnitLabel(
    unit: DurationUnit,
    voice: Voice,
): String =
    when (unit) {
        DurationUnit.MINUTES -> voice.logSheetDurationUnitMinutes
        DurationUnit.HOURS -> voice.logSheetDurationUnitHours
        DurationUnit.DAYS -> voice.logSheetDurationUnitDays
    }

/** Mirrors the real `durationFieldsFor` (file-private in LogDetailViewModel.kt) for this mockup's own local state. */
private fun prototypeDurationFields(durationMillis: Long): Pair<String, DurationUnit> {
    val positiveMillis = durationMillis.coerceAtLeast(0L)
    val unit = durationUnitFor(positiveMillis)
    val amount = if (positiveMillis > 0L) (positiveMillis / unit.millis).toString() else "0"
    return amount to unit
}

/**
 * Picks the most natural unit for *displaying* a live elapsed duration while nothing's been
 * stated yet (distinct from [prototypeDurationFields], which only ever shows whole units for an
 * explicit end time): minutes under an hour, days once it's a whole or half day (e.g. "1.5"),
 * hours otherwise. A fresh event (elapsed 0) lands on "0" minutes.
 */
private fun prototypeElapsedDisplayFields(durationMillis: Long): Pair<String, DurationUnit> {
    val elapsed = durationMillis.coerceAtLeast(0L)
    val halfDayMillis = MILLIS_PER_DAY / 2
    return when {
        elapsed < MILLIS_PER_HOUR -> (elapsed / MILLIS_PER_MINUTE).toString() to DurationUnit.MINUTES
        elapsed >= MILLIS_PER_DAY && elapsed % halfDayMillis == 0L -> {
            val halfDays = elapsed / halfDayMillis
            val amount = if (halfDays % 2 == 0L) (halfDays / 2).toString() else "${halfDays / 2}.5"
            amount to DurationUnit.DAYS
        }
        elapsed % MILLIS_PER_HOUR == 0L -> (elapsed / MILLIS_PER_HOUR).toString() to DurationUnit.HOURS
        else -> (elapsed / MILLIS_PER_MINUTE).toString() to DurationUnit.MINUTES
    }
}

/** Exact visual copy of LogDetailSheet.kt's private DurationUnitSelector (not importable — file-private there). */
@Composable
private fun PrototypeDurationUnitSelector(
    selected: DurationUnit,
    onSelect: (DurationUnit) -> Unit,
    voice: Voice,
) {
    Row(
        modifier =
            Modifier
                .padding(end = 8.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PROTOTYPE_DURATION_UNITS.forEach { unit ->
            val isSelected = unit == selected
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .selectable(selected = isSelected, onClick = { onSelect(unit) }, role = Role.RadioButton)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = prototypeDurationUnitLabel(unit, voice).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Simplified stand-in for the real (unchanged) intensity picker, just to give the duration control realistic screen context. */
@Composable
private fun PrototypeIntensityRow(voice: Voice) {
    Column {
        Text(voice.logSheetIntensityLabel, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            (1..5).forEach { value ->
                val selected = value == 3
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(value.toString())
                }
            }
        }
    }
}

/** Simplified stand-in for the real (unchanged) tags section, just to give the duration control realistic screen context. */
@Composable
private fun PrototypeTagsRow(voice: Voice) {
    Column {
        Text(voice.logSheetTagsLabel, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf("Coffee", "Work").forEach { tag ->
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The merged Log/Edit Event control being validated: a start time, then a status row that's
 * either "Ongoing" + Stop or "Ended" + Start again (one flipping row, not a separate switch).
 * "Ongoing" is *derived* from [endedAt] vs [now] — null or still in the future both read as
 * ongoing, exactly like today's real `isStillRunning` — not a separately-toggled flag.
 *
 * The Duration field and the end-time date/time buttons are always visible under the status row,
 * pre-filled with the live elapsed time whenever nothing's stated yet (e.g. "0" for a just-created
 * event; the buttons themselves prompt "Choose date"/"Choose time" rather than showing today's
 * date, so they don't look like something was already picked) — no separate "current duration"
 * chip or a link to reveal them. Showing that default doesn't itself commit [endedAt] (only
 * actually editing the field or picking a date/time does), so a fresh event still reads Ongoing
 * rather than instantly Ended. Typing a number — or picking an end date/time directly — is how you
 * state an expected duration (the old MANUAL mode's whole point): it computes [endedAt], and if
 * that lands in the future the control stays Ongoing; if it's already passed, the control reads
 * Ended. Either way the buttons relabel to "Set to end" + the actual date/time. Start-time buttons
 * stay display-only in this mockup; only the end-time pickers are wired, since that's what the
 * duration/end-time sync question needs to exercise.
 */
@Composable
private fun UnifiedDurationControl(
    startedAt: Long,
    now: Long,
    initialEndedAt: Long?,
    zone: ZoneId,
    voice: Voice,
) {
    var endedAt by remember { mutableStateOf(initialEndedAt) }
    val initialFields =
        remember {
            initialEndedAt?.let { prototypeDurationFields(it - startedAt) } ?: prototypeElapsedDisplayFields(now - startedAt)
        }
    var durationAmount by remember { mutableStateOf(initialFields.first) }
    var durationUnit by remember { mutableStateOf(initialFields.second) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val use24Hour = LocalTimeFormat.current.is24Hour
    val isOngoing = endedAt?.let { it > now } ?: true

    fun applyEndedAt(at: Long?) {
        endedAt = at
        val fields = if (at == null) prototypeElapsedDisplayFields(now - startedAt) else prototypeDurationFields(at - startedAt)
        durationAmount = fields.first
        durationUnit = fields.second
    }

    fun stopNow() = applyEndedAt(now.coerceAtLeast(startedAt))

    fun startAgain() = applyEndedAt(null)

    fun recalcEndFromDuration(
        amount: String,
        unit: DurationUnit,
    ) {
        // Decimal, not integer-only — a pre-filled live display can read e.g. "1.5" days.
        endedAt = amount.toDoubleOrNull()?.let { startedAt + (it * unit.millis).toLong() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            // Placeholder label — not a Voice key; U2 names the real one once this shape is validated.
            Text("Started", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {}) { Text(formatEventDate(startedAt, zone)) }
                OutlinedButton(onClick = {}) { Text(formatEventTimeOfDay(startedAt, use24Hour, zone)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same text style as "Started" above, so the status word reads as a label, not a heading.
            // .removeSuffix(" at") drops Bright's "Wrapped up at" trailing preposition, written for the
            // old layout where it led straight into a time value — here it's a standalone status word.
            Text(
                (if (isOngoing) voice.logSheetOngoingLabel else voice.logSheetEndLabel).removeSuffix(" at"),
                style = MaterialTheme.typography.labelLarge,
            )
            // Filled, not outlined — this is the primary action, visually distinct from the outlined date/time selectors.
            Button(onClick = { if (isOngoing) stopNow() else startAgain() }) {
                // "Start again" (placeholder, not Voice yet) distinguishes reopening a past event from
                // the screen's own "Create"/"Save" button below.
                Text(if (isOngoing) voice.logSheetStopNowAction else "Start again")
            }
        }

        val currentEndedAt = endedAt
        // Always shown, alongside the Duration field below — not gated on a real stated end.
        // When nothing's been typed yet this reads "now" (what stopping right now would record),
        // matching the Duration field's own live-elapsed default; it doesn't commit anything
        // itself, only actually picking a date/time does (via applyEndedAt).
        val displayEndedAt = currentEndedAt ?: now
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Explains what the row below is for — "Set to end" once a real end exists (no time
            // repeated in the label, the buttons already show it); otherwise a note that it's
            // optional, with the buttons themselves prompting rather than showing today's date,
            // which would otherwise look like something was already chosen. Placeholder copy —
            // not Voice keys yet.
            Text(
                if (currentEndedAt != null) "Set to end" else "End time can be set manually",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showEndDatePicker = true }) {
                    Text(if (currentEndedAt != null) formatEventDate(displayEndedAt, zone) else "Choose date")
                }
                OutlinedButton(onClick = { showEndTimePicker = true }) {
                    Text(if (currentEndedAt != null) formatEventTimeOfDay(displayEndedAt, use24Hour, zone) else "Choose time")
                }
            }
            OutlinedTextField(
                value = durationAmount,
                onValueChange = { input ->
                    durationAmount = input
                    recalcEndFromDuration(input, durationUnit)
                },
                label = { Text(voice.logSheetDurationLabel) },
                // Decimal, not integer-only — the live-elapsed default can read e.g. "1.5" days.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                trailingIcon = {
                    PrototypeDurationUnitSelector(
                        selected = durationUnit,
                        onSelect = { unit ->
                            durationUnit = unit
                            recalcEndFromDuration(durationAmount, unit)
                        },
                        voice = voice,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showEndDatePicker) {
            PrototypeEndDatePickerDialog(
                endedAt = displayEndedAt,
                zone = zone,
                voice = voice,
                onDismiss = { showEndDatePicker = false },
                onConfirm = { picked -> applyEndedAt(picked) },
            )
        }
        if (showEndTimePicker) {
            PrototypeEndTimePickerDialog(
                endedAt = displayEndedAt,
                zone = zone,
                voice = voice,
                onDismiss = { showEndTimePicker = false },
                onConfirm = { picked -> applyEndedAt(picked) },
            )
        }
    }
}

/**
 * End-date picker — deliberately no "max = now" ceiling, unlike the real start/end pickers today:
 * typing a duration in [UnifiedDurationControl] can compute an end time in the future, so both
 * past and future dates need to be pickable here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrototypeEndDatePickerDialog(
    endedAt: Long,
    zone: ZoneId,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toDatePickerUtcMillis(endedAt, zone))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { picked -> onConfirm(applyPickedDate(endedAt, picked, zone)) }
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

/** End-time picker — mirrors LogDetailSheet.kt's private LogDetailTimePickerDialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrototypeEndTimePickerDialog(
    endedAt: Long,
    zone: ZoneId,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val current = Instant.ofEpochMilli(endedAt).atZone(zone)
    val timePickerState: TimePickerState =
        rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = LocalTimeFormat.current.is24Hour,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(applyPickedTime(endedAt, timePickerState.hour, timePickerState.minute, zone))
                onDismiss()
            }) { Text(voice.logSheetPickerConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.logSheetPickerCancel) }
        },
        text = { TimePicker(state = timePickerState) },
    )
}

/** 1. Case Edit — Duration becomes a single on/off switch, replacing the 3-way NONE/MANUAL/START_STOP picker. */
@Composable
private fun CaseDurationSwitchPreviewContent() {
    var tracksDuration by remember { mutableStateOf(true) }
    val voice = LocalVoice.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            // Label + info icon + switch on one row, matching the Track intensity row (CaseEditScreen.kt:338).
            RowWithInfo(
                voice.caseDurationModeLabel,
                voice.caseDurationModeInfoTitle,
                voice.caseDurationModeInfoBody,
                voice.caseSectionInfoDescription,
            ) {
                Switch(checked = tracksDuration, onCheckedChange = { tracksDuration = it }, colors = themedSwitchColors())
            }
        }
    }
}

/**
 * 2. Log Event — the whole bottom-sheet form (spec §6 order: time → duration/ongoing → intensity
 * → note → tags → Create), not just the duration control in isolation, so it reads in its real
 * screen context. Intensity/note/tags are simplified stand-ins — unchanged by this rework.
 *
 * [futureDurationOffsetMillis] lets a preview start the event with a stated expected duration
 * already set (now + offset) — the "duration in the future" scenario — instead of the plain
 * fresh-and-open-ended default.
 */
@Composable
private fun LogEventScreenPreviewContent(futureDurationOffsetMillis: Long? = null) {
    val voice = LocalVoice.current
    val now = remember { System.currentTimeMillis() }
    val initialEndedAt = futureDurationOffsetMillis?.let { now + it }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            UnifiedDurationControl(
                startedAt = now,
                now = now,
                initialEndedAt = initialEndedAt,
                zone = ZoneId.systemDefault(),
                voice = voice,
            )
            PrototypeIntensityRow(voice)
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text(voice.logSheetNoteLabel) },
                placeholder = { Text(voice.logSheetNoteHint) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PrototypeTagsRow(voice)
            // Fixed wording, not tied to ongoing/ended — "Start"/"Save" clashed with the control's
            // own Start again/Stop now actions above. Placeholder — not a Voice key yet.
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Create") }
        }
    }
}

/**
 * 3. Edit Event — the whole screen (duration control + intensity/note/tags stand-ins + Save),
 * reusing [UnifiedDurationControl] so both "already ended" and "still ongoing, no stated
 * duration" read in real screen context. [initialEndedAt] null is the latter; non-null (in the
 * past) the former.
 */
@Composable
private fun EditEventScreenPreviewContent(
    startedAt: Long,
    initialEndedAt: Long?,
) {
    val voice = LocalVoice.current
    val now = remember { System.currentTimeMillis() }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(voice.logSheetEditEventTitle, style = MaterialTheme.typography.titleLarge)
            UnifiedDurationControl(
                startedAt = startedAt,
                now = now,
                initialEndedAt = initialEndedAt,
                zone = ZoneId.systemDefault(),
                voice = voice,
            )
            PrototypeIntensityRow(voice)
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text(voice.logSheetNoteLabel) },
                placeholder = { Text(voice.logSheetNoteHint) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PrototypeTagsRow(voice)
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(voice.logSheetSaveButton) }
        }
    }
}

@Preview(name = "Case duration switch — Plain light", showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun CaseDurationSwitchPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            CaseDurationSwitchPreviewContent()
        }
    }
}

@Preview(name = "Log event screen (new, ongoing) — Plain light", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun LogEventScreenPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            LogEventScreenPreviewContent()
        }
    }
}

@Preview(name = "Log event screen (new, duration in the future) — Plain light", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun LogEventFutureDurationScreenPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            LogEventScreenPreviewContent(futureDurationOffsetMillis = 2 * MILLIS_PER_HOUR)
        }
    }
}

@Preview(name = "Edit event screen (ended) — Plain light", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun EditEventEndedScreenPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            val startedAt = System.currentTimeMillis() - 3 * MILLIS_PER_DAY
            EditEventScreenPreviewContent(startedAt = startedAt, initialEndedAt = startedAt + 90 * MILLIS_PER_MINUTE)
        }
    }
}

@Preview(
    name = "Edit event screen (ongoing, no manual duration) — Plain light",
    showBackground = true,
    widthDp = 360,
    heightDp = 780,
)
@Composable
private fun EditEventOngoingNoDurationScreenPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            EditEventScreenPreviewContent(startedAt = System.currentTimeMillis() - 2 * MILLIS_PER_HOUR, initialEndedAt = null)
        }
    }
}
