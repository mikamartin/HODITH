package com.secondmonday.hodith.ui.settings.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.TagSummary
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.Plank
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.ManageTagsUiState
import com.secondmonday.hodith.viewmodel.ManageTagsViewModel
import com.secondmonday.hodith.viewmodel.PendingTagAction

@Composable
fun ManageTagsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageTagsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ManageTagsScreen(
        uiState = uiState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onRenameRequested = viewModel::onRenameRequested,
        onDeleteRequested = viewModel::onDeleteRequested,
        onConfirmPending = viewModel::onConfirmPending,
        onDismissPending = viewModel::onDismissPending,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsScreen(
    uiState: ManageTagsUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRenameRequested: (TagSummary, String) -> Unit,
    onDeleteRequested: (TagSummary) -> Unit,
    onConfirmPending: () -> Unit,
    onDismissPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var renaming by remember { mutableStateOf<TagSummary?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.manageTagsScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        if (uiState.isLoading) return@Scaffold

        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.showFilter) {
                TagFilterField(query = uiState.query, voice = voice, onQueryChange = onQueryChange)
            }
            Plank(title = null) {
                when {
                    uiState.tags.isEmpty() -> Text(voice.manageTagsEmptyState, style = MaterialTheme.typography.bodyMedium)
                    uiState.visibleTags.isEmpty() -> Text(voice.manageTagsNoMatches, style = MaterialTheme.typography.bodyMedium)
                    else ->
                        uiState.visibleTags.forEachIndexed { index, summary ->
                            if (index > 0) HorizontalDivider()
                            TagRow(
                                summary = summary,
                                voice = voice,
                                onEdit = { renaming = summary },
                                onDelete = { onDeleteRequested(summary) },
                            )
                        }
                }
            }
        }
    }

    renaming?.let { summary ->
        RenameTagDialog(
            summary = summary,
            voice = voice,
            onDismiss = { renaming = null },
            onSave = { newName ->
                renaming = null
                onRenameRequested(summary, newName)
            },
        )
    }

    uiState.pending?.let { action ->
        val (title, body, confirmLabel) =
            when (action) {
                is PendingTagAction.Rename ->
                    Triple(
                        voice.manageTagsRenameConfirmTitle,
                        voice.manageTagsRenameConfirmBody(action.tag.name, action.newName, action.eventCount),
                        voice.manageTagsRenameConfirmAction,
                    )
                is PendingTagAction.Merge ->
                    Triple(
                        voice.manageTagsMergeConfirmTitle,
                        voice.manageTagsMergeConfirmBody(
                            sourceName = action.tag.name,
                            targetName = action.target.name,
                            sourceEventCount = action.sourceEventCount,
                            overlapCount = action.overlapCount,
                        ),
                        voice.manageTagsMergeConfirmAction,
                    )
                is PendingTagAction.Delete ->
                    Triple(
                        voice.manageTagsDeleteConfirmTitle,
                        voice.manageTagsDeleteConfirmBody(action.tag.name, action.eventCount),
                        voice.manageTagsDeleteConfirmAction,
                    )
            }
        ConfirmDialog(
            title = title,
            body = body,
            confirmLabel = confirmLabel,
            cancelLabel = voice.manageTagsCancelAction,
            onDismiss = onDismissPending,
            onConfirm = onConfirmPending,
        )
    }
}

@Composable
private fun TagFilterField(
    query: String,
    voice: Voice,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(voice.manageTagsFilterPlaceholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = voice.manageTagsFilterClearDescription)
                }
            }
        },
    )
}

@Composable
private fun TagRow(
    summary: TagSummary,
    voice: Voice,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.tag.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                voice.manageTagsEventCount(summary.eventCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = voice.manageTagsEditDescription(summary.tag.name))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = voice.manageTagsDeleteDescription(summary.tag.name),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Collects the new name only. Whether it merges into another tag is decided by the ViewModel and
 * shown in the warning that follows, so this dialog stays a plain text field.
 */
@Composable
private fun RenameTagDialog(
    summary: TagSummary,
    voice: Voice,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(summary.tag.id) { mutableStateOf(summary.tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voice.manageTagsRenameDialogTitle) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(voice.manageTagsRenameFieldLabel) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(voice.manageTagsRenameSaveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.manageTagsCancelAction) }
        },
    )
}

@Composable
private fun ManageTagsPreview(
    theme: AppTheme,
    darkTheme: Boolean,
) {
    val tags =
        listOf(
            TagSummary(TagEntity(id = 1, name = "coffee"), eventCount = 3),
            TagSummary(TagEntity(id = 2, name = "focus"), eventCount = 1),
        )
    HodithTheme(theme = theme, darkTheme = darkTheme) {
        CompositionLocalProvider(LocalVoice provides voiceFor(theme)) {
            ManageTagsScreen(
                uiState = ManageTagsUiState(isLoading = false, tags = tags, visibleTags = tags),
                onBack = {},
                onQueryChange = {},
                onRenameRequested = { _, _ -> },
                onDeleteRequested = {},
                onConfirmPending = {},
                onDismissPending = {},
            )
        }
    }
}

@Preview(name = "Manage tags — Plain light", showBackground = true, widthDp = 360)
@Composable
private fun ManageTagsPlainLightPreview() {
    ManageTagsPreview(AppTheme.PLAIN, darkTheme = false)
}

@Preview(name = "Manage tags — Intense light", showBackground = true, widthDp = 360)
@Composable
private fun ManageTagsIntenseLightPreview() {
    ManageTagsPreview(AppTheme.INTENSE, darkTheme = false)
}

@Preview(name = "Manage tags — Bright light", showBackground = true, widthDp = 360)
@Composable
private fun ManageTagsBrightLightPreview() {
    ManageTagsPreview(AppTheme.BRIGHT, darkTheme = false)
}

@Preview(name = "Manage tags — Bright dark", showBackground = true, widthDp = 360)
@Composable
private fun ManageTagsBrightDarkPreview() {
    ManageTagsPreview(AppTheme.BRIGHT, darkTheme = true)
}
