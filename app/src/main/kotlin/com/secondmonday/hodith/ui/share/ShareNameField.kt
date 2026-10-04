package com.secondmonday.hodith.ui.share

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The "Name on card" field on the Share screen. Stateless: [ShareScreen] holds the typed text above the tabs,
 * so the name survives a switch between Summary, Insights and History.
 */
@Composable
internal fun ShareNameField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
    )
}

/**
 * The name a card carries: the typed name, or the Case's own name while the field is untouched or blank. A blank
 * field therefore shows empty but the card falls back, the same rule the share ViewModels used to apply.
 */
internal fun shareDisplayName(
    typed: String?,
    caseName: String,
): String = typed?.takeIf { it.isNotBlank() } ?: caseName
