package com.secondmonday.hodith.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.secondmonday.hodith.ui.voice.Voice

/**
 * A plain `DatePickerDialog` + `DatePicker`, bound by a ceiling-only [SelectableDates] (no date
 * after [maxDateUtcMillis] is pickable) — shared by the Log sheet's own start/end date picker
 * (ceiling: "now") and Settings' delete-data cutoff date picker (ceiling: the chosen cutoff's own
 * max). Both read/write UTC-midnight millis, matching how Material3's `DatePicker` itself works;
 * callers own converting to/from their local timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaxDateBoundDatePickerDialog(
    initialDateUtcMillis: Long,
    maxDateUtcMillis: Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (pickedUtcMillis: Long) -> Unit,
) {
    val datePickerState: DatePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDateUtcMillis,
            selectableDates =
                remember(maxDateUtcMillis) {
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxDateUtcMillis
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
