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
                    testTag = SHARE_CHOOSER_OPTION_TAG_PREFIX + ShareChoice.INSIGHT.name,
                )
                RadioOptionRow(
                    label = voice.shareChooserLogOption,
                    description = voice.shareChooserLogDescription,
                    selected = choice == ShareChoice.LOG,
                    onSelect = { choice = ShareChoice.LOG },
                    testTag = SHARE_CHOOSER_OPTION_TAG_PREFIX + ShareChoice.LOG.name,
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

/**
 * [ShareChooserDialog]'s two [RadioOptionRow]s need to be findable by tag rather than by label
 * text: the Log option's own label text ("Log") collides with the still-present Case Detail Log
 * tab underneath the dialog, the same class of ambiguity Big Picture/Log tab/Log Share's own field
 * toggles hit and solved the same way.
 */
internal const val SHARE_CHOOSER_OPTION_TAG_PREFIX = "share_chooser_option_"
