package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Shared shape for a section with a label, a tappable info icon explaining it, and its content below. */
@Composable
fun SectionWithInfo(
    label: String,
    infoTitle: String,
    infoBody: String,
    infoDescription: String,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    content: @Composable () -> Unit,
) {
    Column {
        LabelWithInfo(label, infoTitle, infoBody, infoDescription, labelStyle)
        content()
    }
}

/** Shared shape for a label with a tappable info icon and a trailing control on the same row (e.g. a toggle). */
@Composable
fun RowWithInfo(
    label: String,
    infoTitle: String,
    infoBody: String,
    infoDescription: String,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
    trailingContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelWithInfo(label, infoTitle, infoBody, infoDescription, labelStyle)
        trailingContent()
    }
}

@Composable
private fun LabelWithInfo(
    label: String,
    infoTitle: String,
    infoBody: String,
    infoDescription: String,
    labelStyle: TextStyle,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(title = infoTitle, onDismiss = { showInfo = false }) { Text(infoBody) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = labelStyle)
        IconButton(onClick = { showInfo = true }) {
            Icon(Icons.Filled.Info, contentDescription = infoDescription, modifier = Modifier.size(18.dp))
        }
    }
}
