package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared +/- stepper for a bounded Int with a trailing unit suffix (Hunch's expected count, Trigger's threshold). */
@Composable
fun NumberStepper(
    value: Int,
    range: IntRange,
    suffix: String,
    decreaseDescription: String,
    increaseDescription: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { onChange((value - 1).coerceIn(range)) },
            modifier = Modifier.semantics { contentDescription = decreaseDescription },
        ) {
            Text("−", style = MaterialTheme.typography.headlineSmall)
        }
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        IconButton(
            onClick = { onChange((value + 1).coerceIn(range)) },
            modifier = Modifier.semantics { contentDescription = increaseDescription },
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
        Text(suffix, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}
