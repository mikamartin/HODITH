package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.secondmonday.hodith.ui.voice.LocalVoice

/**
 * Shared shape for a single-action dialog (e.g. the Logging/Duration/Check-in info icons on Case
 * Edit, or Big Picture's month picker / day / week detail dialogs): title, arbitrary content, a
 * dismiss [TextButton]. Unlike [ConfirmDialog], there is no confirm action — [leadingAction], when
 * present, shares the button row with dismiss rather than adding one (e.g. Big Picture's bulk
 * select/clear toggle, which would otherwise stack on top of content and read as an oversized gap
 * under the title).
 */
@Composable
fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String = LocalVoice.current.infoDialogDismissAction,
    leadingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = content,
        confirmButton = {
            if (leadingAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingAction()
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                }
            } else {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        },
    )
}
