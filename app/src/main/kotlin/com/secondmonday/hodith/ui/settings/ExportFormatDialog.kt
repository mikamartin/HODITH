package com.secondmonday.hodith.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.voice.Voice

/** Which file format the export dialog is currently set to produce. */
internal enum class ExportFormat { JSON, CSV }

/**
 * A fixed leading-slot width for [RadioButton], rather than relying on its own (theme-dependent)
 * measured width, so the gap to the label and the description's indent underneath it always agree
 * with each other. Wider than the drawn dot on purpose to keep the same visual gap [RadioButton]
 * has elsewhere in this app (e.g. `SingleCaseWidgetConfigureActivity`'s `CasePickerRow`).
 */
private val RADIO_SLOT_WIDTH = 32.dp
private val RADIO_LABEL_GAP = 12.dp

/**
 * First and only step of the "Export data" flow: choose JSON (the full, restorable backup) or CSV
 * (a flattened, spreadsheet-openable table, export-only per spec §16), each with a one-line note
 * since not every user already knows what either format is for. Replaces the former pair of
 * separate "Export data" / "Export as CSV" rows on the Data plank. A plain [AlertDialog]
 * composition, same idiom as this package's `DeleteDataOptionsDialog`.
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
                ExportFormatOption(
                    label = voice.settingsExportFormatJsonOption,
                    description = voice.settingsExportFormatJsonDescription,
                    selected = format == ExportFormat.JSON,
                    onSelect = { format = ExportFormat.JSON },
                )
                ExportFormatOption(
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

/**
 * [RadioButton] centers on the label line only (not the whole two-line block), since
 * [RadioButton]'s own touch-target box is taller than a single `bodyLarge` line — top-aligning it
 * against a two-line [Column] left the dot sitting visibly above the label. It sits in a fixed
 * [RADIO_SLOT_WIDTH] box rather than being measured inline, so its width doesn't dictate the gap
 * before the label; the description sits on its own line below, indented by the same
 * [RADIO_SLOT_WIDTH] + [RADIO_LABEL_GAP] so it lines up under the label rather than under the dot,
 * and fills the remaining dialog width rather than wrapping at its own intrinsic width.
 */
@Composable
private fun ExportFormatOption(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(RADIO_SLOT_WIDTH), contentAlignment = Alignment.Center) {
                RadioButton(selected = selected, onClick = null)
            }
            Text(label, modifier = Modifier.padding(start = RADIO_LABEL_GAP), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = RADIO_SLOT_WIDTH + RADIO_LABEL_GAP),
        )
    }
}
