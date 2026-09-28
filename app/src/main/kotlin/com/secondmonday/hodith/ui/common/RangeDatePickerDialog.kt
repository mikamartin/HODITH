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
import com.secondmonday.hodith.viewmodel.toDatePickerUtcMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A ceiling-capped local-date picker shared by any two-endpoint date-range control in the app (Log
 * Share's own date range, the Case Detail Log tab's Range filter) — only the [maxLocalMillis]
 * ceiling differs per endpoint ("To" caps at today, "From" caps at the current "To"). Mirrors
 * `DeleteDataCutoffDatePickerDialog`'s ceiling-predicate idiom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeDatePickerDialog(
    initialLocalMillis: Long,
    maxLocalMillis: Long,
    zone: ZoneId,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?) -> Unit,
) {
    val maxUtcMillis = toDatePickerUtcMillis(maxLocalMillis, zone)
    val datePickerState: DatePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = toDatePickerUtcMillis(initialLocalMillis, zone),
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
                val picked =
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                onConfirm(picked)
            }) { Text(voice.logSheetPickerConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.logSheetPickerCancel) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
