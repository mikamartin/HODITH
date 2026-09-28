package com.secondmonday.hodith.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.common.RadioOptionRow
import com.secondmonday.hodith.ui.voice.Voice

/** Which file format the export dialog is currently set to produce. */
internal enum class ExportFormat { JSON, CSV }

/**
 * First and only step of the "Export data" flow: choose JSON (the full, restorable backup) or CSV
 * (a flattened, spreadsheet-openable table, export-only per spec §16), each with a one-line note
 * since not every user already knows what either format is for. Replaces the former pair of
 * separate "Export data" / "Export as CSV" rows on the Data plank. A plain [AlertDialog]
 * composition ([RadioOptionRow] rows), same idiom as this package's `DeleteDataOptionsDialog` and
 * the Case-Detail header's Insight/Log Share chooser (`ShareChooserDialog`).
 */
@Composable
internal fun ExportFormatDialog(
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat) -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.JSON) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voice.settingsExportFormatDialogTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioOptionRow(
                    label = voice.settingsExportFormatJsonOption,
                    description = voice.settingsExportFormatJsonDescription,
                    selected = format == ExportFormat.JSON,
                    onSelect = { format = ExportFormat.JSON },
                )
                RadioOptionRow(
                    label = voice.settingsExportFormatCsvOption,
                    description = voice.settingsExportFormatCsvDescription,
                    selected = format == ExportFormat.CSV,
                    onSelect = { format = ExportFormat.CSV },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(format) }) { Text(voice.settingsExportFormatConfirmAction) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.settingsExportFormatCancelAction) }
        },
    )
}
