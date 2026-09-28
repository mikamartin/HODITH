package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
 *
 * [isRangeFiltered] is the caller's own "nothing filtered" definition, since it differs by
 * caller: the Log tab treats null/null as all time, while Log Share's [dateTo] is never null
 * (it defaults to today) so its "all time" is null-from/today-to instead. Passing it in, rather
 * than inferring all-time from [dateFrom]/[dateTo] here, is what lets the "All time" shortcut
 * below react correctly to both definitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterDialog(
    dateFrom: Long?,
    dateTo: Long?,
    isRangeFiltered: Boolean,
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
    // Captured from the state itself, not recomputed independently, so this can't drift from
    // whatever canonical form the state settles the initial millis into internally.
    val initialStartMillis = remember { rangeState.selectedStartDateMillis }
    val initialEndMillis = remember { rangeState.selectedEndDateMillis }
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    // Shown once there's an actual range to clear back to all time: either the dialog opened
    // already filtered, or the user has moved the calendar selection since opening it.
    val hasSelection =
        isRangeFiltered ||
            rangeState.selectedStartDateMillis != initialStartMillis ||
            rangeState.selectedEndDateMillis != initialEndMillis
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
            dateFormatter = dateFormatter,
            showModeToggle = false,
            // Default title/headline padding reserves a 64dp left margin (room for a close/save
            // icon in the full-screen variant, which this compact dialog doesn't render) and the
            // headline uses titleLarge text -- together too wide for a dialog-width row, so the
            // end date wraps once it needs a year suffix. Tighter padding and a smaller headline
            // style get both bounds on one line; the title row gets the same tighter start inset
            // so "Select dates" lines up above the headline, with room on the end for "All time".
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateRangePickerDefaults.DateRangePickerTitle(displayMode = rangeState.displayMode)
                    if (hasSelection) {
                        OutlinedButton(
                            onClick = { onConfirm(null, null) },
                            modifier = Modifier.testTag(DATE_RANGE_ALL_TIME_BUTTON_TAG),
                        ) {
                            Text(voice.shareLogRangeAllTimeLabel)
                        }
                    }
                }
            },
            headline = {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
                    DateRangePickerDefaults.DateRangePickerHeadline(
                        selectedStartDateMillis = rangeState.selectedStartDateMillis,
                        selectedEndDateMillis = rangeState.selectedEndDateMillis,
                        displayMode = rangeState.displayMode,
                        dateFormatter = dateFormatter,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            },
        )
    }
}

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal const val DATE_RANGE_ALL_TIME_BUTTON_TAG = "date_range_all_time_button"
