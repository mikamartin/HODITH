package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val RADIO_SLOT_WIDTH = 32.dp
private val RADIO_LABEL_GAP = 12.dp

/**
 * A single-select `AlertDialog` option: a [RadioButton] in a fixed-width slot, a label beside it,
 * and a description indented to line up under the label rather than the dot. Shared by every
 * radio-style chooser dialog in the app (`ExportFormatDialog`'s JSON/CSV choice, the Case-Detail
 * header's Insight/Log Share chooser) — same idiom, previously duplicated per call site.
 */
@Composable
fun RadioOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (testTag != null) it.testTag(testTag) else it }
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
