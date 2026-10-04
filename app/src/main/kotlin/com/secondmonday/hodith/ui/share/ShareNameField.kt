package com.secondmonday.hodith.ui.share

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.secondmonday.hodith.viewmodel.CASE_NAME_MAX_LENGTH

/**
 * The "Name on card" field shared by the Insight and Log share screens. It shows the name the card
 * will carry (the override, or the Case's own name) rather than a faded placeholder, so the field
 * reads as editable content. The text is held locally: the ViewModel stores a blank name as `null`,
 * which would otherwise refill the field with the Case name mid-edit.
 */
@Composable
internal fun ShareNameField(
    caseName: String,
    displayNameOverride: String?,
    label: String,
    onDisplayNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(displayNameOverride ?: caseName) }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.take(CASE_NAME_MAX_LENGTH)
            onDisplayNameChange(text)
        },
        label = { Text(label) },
        modifier = modifier,
    )
}
