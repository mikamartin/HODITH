package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.formatEventDate
import java.time.LocalDate
import java.time.ZoneId

/**
 * The shared From/To trigger-button pair behind both Log Share's own date range and the Case
 * Detail Log tab's Range filter — each button opens [RangeDatePickerDialog] for its own endpoint,
 * "From" capped at the current "To" (or [now] while "To" is itself unset) and "To" capped at
 * [now]. Either side reads "…" while unset — matches how a partially-unset range already renders
 * inline elsewhere (`logShareRangeAllTimeLabel`'s own per-side placeholder).
 */
@Composable
fun DateRangePickerRow(
    dateFrom: Long?,
    dateTo: Long?,
    now: Long,
    zone: ZoneId,
    onDateFromChange: (LocalDate?) -> Unit,
    onDateToChange: (LocalDate?) -> Unit,
    voice: Voice,
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showFromPicker = true }) {
            Text("${voice.shareLogDateFromLabel}: ${dateFrom?.let { formatEventDate(it, zone) } ?: "…"}")
        }
        OutlinedButton(onClick = { showToPicker = true }) {
            Text("${voice.shareLogDateToLabel}: ${dateTo?.let { formatEventDate(it, zone) } ?: "…"}")
        }
    }

    if (showFromPicker) {
        RangeDatePickerDialog(
            initialLocalMillis = dateFrom ?: dateTo ?: now,
            maxLocalMillis = dateTo ?: now,
            zone = zone,
            voice = voice,
            onDismiss = { showFromPicker = false },
            onConfirm = { picked ->
                onDateFromChange(picked)
                showFromPicker = false
            },
        )
    }
    if (showToPicker) {
        RangeDatePickerDialog(
            initialLocalMillis = dateTo ?: now,
            maxLocalMillis = now,
            zone = zone,
            voice = voice,
            onDismiss = { showToPicker = false },
            onConfirm = { picked ->
                onDateToChange(picked)
                showToPicker = false
            },
        )
    }
}
