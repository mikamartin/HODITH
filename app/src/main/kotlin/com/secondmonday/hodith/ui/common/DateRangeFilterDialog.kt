package com.secondmonday.hodith.ui.common

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.toDatePickerUtcMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The single "tap a start day, then an end day" range picker behind both Log Share's own date
 * range and the Case Detail Log tab's Range filter — replaces an earlier two-dialog From/To
 * pattern (one small single-date picker each) that let "To" land before "From" (nothing capped
 * its lower bound) and squeezed the two trigger controls into wrapped, illegible pills.
 * Material3's own [DateRangePicker] enforces start<=end itself: tapping a date earlier than the
 * current start restarts the range from there instead of producing an invalid one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterDialog(
    dateFrom: Long?,
    dateTo: Long?,
    now: Long,
    zone: ZoneId,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (from: LocalDate?, to: LocalDate?) -> Unit,
) {
    val maxUtcMillis = toDatePickerUtcMillis(now, zone)
    val rangeState =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = dateFrom?.let { toDatePickerUtcMillis(it, zone) },
            initialSelectedEndDateMillis = dateTo?.let { toDatePickerUtcMillis(it, zone) },
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
                onConfirm(
                    rangeState.selectedStartDateMillis?.toUtcLocalDate(),
                    rangeState.selectedEndDateMillis?.toUtcLocalDate(),
                )
            }) { Text(voice.logSheetPickerConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.logSheetPickerCancel) }
        },
    ) {
        DateRangePicker(
            state = rangeState,
            modifier = Modifier.weight(1f),
            showModeToggle = false,
        )
    }
}

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
