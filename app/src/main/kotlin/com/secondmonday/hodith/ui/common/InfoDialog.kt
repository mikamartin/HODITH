package com.secondmonday.hodith.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.secondmonday.hodith.ui.voice.LocalVoice

/**
 * Shared shape for a plain explanatory dialog (e.g. the Logging/Duration/Check-in info icons on
 * Case Edit): title, body, a single dismiss [TextButton]. Unlike [ConfirmDialog], there is no
 * confirm action — this dialog only explains, it never changes state.
 */
@Composable
fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    val voice = LocalVoice.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(voice.infoDialogDismissAction) }
        },
    )
}
