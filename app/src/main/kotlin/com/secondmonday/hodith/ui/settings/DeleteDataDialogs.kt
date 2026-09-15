package com.secondmonday.hodith.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.datePickerDateAtLocalStartOfDay
import com.secondmonday.hodith.viewmodel.formatMediumDate
import com.secondmonday.hodith.viewmodel.toDatePickerUtcMillis
import java.time.Instant
import java.time.ZoneId

/** Which data the "Delete Data" row's destructive flow targets. */
private enum class DeleteDataMode { ALL, LOGS_ONLY }

/**
 * Entry point for the whole "Delete Data" flow (spec §14): the options step
 * ([DeleteDataOptionsDialog]) then the existing destructive [ConfirmDialog], with copy branching
 * on the chosen mode. Owns all of the flow's own state internally — [visible] going from false to
 * true starts a fresh pass (mode back to [DeleteDataMode.ALL], cutoff date back to today) rather
 * than resuming a prior one, since `remember`'s slots for the body below are only entered while
 * `visible` is true.
 */
@Composable
internal fun DeleteDataFlow(
    visible: Boolean,
    nowMillis: () -> Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onDeleteAllData: () -> Unit,
    onDeleteEventsOlderThan: (cutoff: Long) -> Unit,
) {
    if (!visible) return

    var mode by remember { mutableStateOf(DeleteDataMode.ALL) }
    var cutoffDate by remember { mutableLongStateOf(nowMillis()) }
    var showConfirm by remember { mutableStateOf(false) }

    if (!showConfirm) {
        DeleteDataOptionsDialog(
            mode = mode,
            onModeSelect = { mode = it },
            cutoffDate = cutoffDate,
            onCutoffDateSelect = { cutoffDate = it },
            maxSelectableDate = nowMillis(),
            voice = voice,
            onDismiss = onDismiss,
            onProceed = { showConfirm = true },
        )
    } else {
        val logsDateLabel = formatMediumDate(Instant.ofEpochMilli(cutoffDate).atZone(ZoneId.systemDefault()).toLocalDate())
        ConfirmDialog(
            title = if (mode == DeleteDataMode.ALL) voice.settingsDeleteAllDataConfirmTitle else voice.settingsDeleteDataLogsConfirmTitle,
            body =
                if (mode == DeleteDataMode.ALL) {
                    voice.settingsDeleteAllDataConfirmBody
                } else {
                    voice.settingsDeleteDataLogsConfirmBody(logsDateLabel)
                },
            confirmLabel =
                if (mode == DeleteDataMode.ALL) voice.settingsDeleteAllDataConfirmAction else voice.settingsDeleteDataLogsConfirmAction,
            cancelLabel = voice.settingsDeleteAllDataCancelAction,
            onDismiss = onDismiss,
            onConfirm = {
                if (mode == DeleteDataMode.ALL) onDeleteAllData() else onDeleteEventsOlderThan(cutoffDate)
                onDismiss()
            },
        )
    }
}

/**
 * First step of the "Delete Data" flow (spec §14): choose all data vs. logs only, and — for logs
 * only — the cutoff date. A plain [AlertDialog] composition, the same idiom
 * `com.secondmonday.hodith.ui.common.ConfirmDialog` and `InfoDialog` both use — neither fits
 * as-is (`ConfirmDialog` takes a fixed body string with no content slot; `InfoDialog` has no
 * confirm/cancel pair), so this stays a one-off local to Settings, same as `HunchCreationSheet`'s
 * `WindowStartDatePickerDialog` is local to its own screen. Proceeding opens the existing
 * destructive `ConfirmDialog` as a second step; this dialog itself never deletes anything.
 */
@Composable
private fun DeleteDataOptionsDialog(
    mode: DeleteDataMode,
    onModeSelect: (DeleteDataMode) -> Unit,
    cutoffDate: Long,
    onCutoffDateSelect: (Long) -> Unit,
    maxSelectableDate: Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voice.settingsDeleteDataOptionsTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedChoiceRow(
                    options =
                        listOf(
                            DeleteDataMode.ALL to voice.settingsDeleteDataOptionAll,
                            DeleteDataMode.LOGS_ONLY to voice.settingsDeleteDataOptionLogsOnly,
                        ),
                    selected = mode,
                    onSelect = onModeSelect,
                )
                if (mode == DeleteDataMode.LOGS_ONLY) {
                    Text(voice.settingsDeleteDataDateLabel, style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(formatMediumDate(Instant.ofEpochMilli(cutoffDate).atZone(ZoneId.systemDefault()).toLocalDate()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onProceed) { Text(voice.settingsDeleteDataOptionsNextAction) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.settingsDeleteDataOptionsCancelAction) }
        },
    )

    if (showDatePicker) {
        DeleteDataCutoffDatePickerDialog(
            selectedDate = cutoffDate,
            maxDate = maxSelectableDate,
            voice = voice,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                onCutoffDateSelect(picked)
                showDatePicker = false
            },
        )
    }
}

/**
 * Local-date picker for the logs-only cutoff, capped at [maxDate] ("today," per spec — no future
 * cutoff). Mirrors [com.secondmonday.hodith.ui.casedetail.HunchCreationSheet]'s
 * `WindowStartDatePickerDialog` structure, with
 * [com.secondmonday.hodith.ui.logsheet.LogDetailSheet]'s `LogDetailDatePickerDialog` ceiling
 * predicate (`<=`) used for the max-date cap instead of a floor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteDataCutoffDatePickerDialog(
    selectedDate: Long,
    maxDate: Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (localMillis: Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val maxUtcMillis = toDatePickerUtcMillis(maxDate, zone)
    val datePickerState: DatePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = toDatePickerUtcMillis(selectedDate, zone),
            selectableDates =
                remember(maxUtcMillis) {
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxUtcMillis
                    }
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { utcMillis ->
                    onConfirm(minOf(datePickerDateAtLocalStartOfDay(utcMillis, zone), maxDate))
                }
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
