package com.secondmonday.hodith.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.secondmonday.hodith.ui.voice.LocalVoice

/**
 * Shared shape for a single-action dialog (e.g. the Logging/Duration/Check-in info icons on Case
 * Edit, or Big Picture's month picker / day / week detail dialogs): title, arbitrary content, a
 * single dismiss [TextButton]. Unlike [ConfirmDialog], there is no confirm action — this dialog
 * only shows something, it never changes state.
 */
@Composable
fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String = LocalVoice.current.infoDialogDismissAction,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = content,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
