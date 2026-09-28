package com.secondmonday.hodith.ui.share

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

/** Which of Case Detail's two share flows the entry-point chooser is currently pointed at. */
enum class ShareChoice { INSIGHT, LOG }

/**
 * The single Share icon's entry point (Case Detail header, spec §13): a choice between the
 * existing Insight Share and Log Share, not two separate icons — decided in the Log Share design
 * pass (`docs/mockups/log-share-prototype.html`). A plain [AlertDialog] composition, same idiom as
 * `ExportFormatDialog.kt` ([RadioOptionRow] rows, Cancel/Confirm).
 */
@Composable
internal fun ShareChooserDialog(
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (ShareChoice) -> Unit,
) {
    var choice by remember { mutableStateOf(ShareChoice.INSIGHT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voice.shareOpenDescription) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioOptionRow(
                    label = voice.shareChooserInsightOption,
                    description = voice.shareChooserInsightDescription,
                    selected = choice == ShareChoice.INSIGHT,
                    onSelect = { choice = ShareChoice.INSIGHT },
                )
                RadioOptionRow(
                    label = voice.shareChooserLogOption,
                    description = voice.shareChooserLogDescription,
                    selected = choice == ShareChoice.LOG,
                    onSelect = { choice = ShareChoice.LOG },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }) { Text(voice.shareOpenDescription) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.shareChooserCancelAction) }
        },
    )
}
