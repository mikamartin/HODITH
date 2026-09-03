package com.secondmonday.hodith.ui.logsheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDetailScreenUiState
import com.secondmonday.hodith.viewmodel.LogDetailScreenViewModel
import com.secondmonday.hodith.viewmodel.LogDraft

@Composable
fun LogDetailRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogDetailScreenViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onDone()
    }

    LogDetailScreen(
        uiState = uiState,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone,
        modifier = modifier,
    )
}

/**
 * Full-screen editor for an *existing* event — reached by tapping a row in Case Detail's Log
 * tab (or the stale-ongoing "edit end time" prompt). Deliberately mirrors `CaseEditScreen`'s
 * chrome (a `TopAppBar` with a back arrow and a trailing delete action) so editing an event and
 * editing a Case read the same way. Logging a *new* event stays [LogDetailSheet], a fast bottom
 * sheet (spec §6). Both share [LogDetailForm] for the fields themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    uiState: LogDetailScreenUiState,
    onSave: (LogDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val voice = LocalVoice.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        DeleteEventConfirmDialog(
            voice = voice,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        // Plain only: a form screen stays white (surface), matching CaseEditScreen and the
        // ModalBottomSheet's own default; Intense and Bright keep the tinted Scaffold default.
        containerColor =
            if (LocalCardDecorationStyle.current == CardDecorationStyle.PLAIN) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background
            },
        topBar = {
            TopAppBar(
                title = { Text(voice.logSheetEditEventTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
                actions = {
                    if (onDelete != null && !uiState.isLoading) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = voice.deleteEventConfirmAction)
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        val draft = uiState.initialDraft
        if (uiState.isLoading || draft == null) return@Scaffold

        LogDetailForm(
            isEditing = true,
            durationMode = uiState.durationMode,
            intensityEnabled = uiState.intensityEnabled,
            initialDraft = draft,
            tagSuggestions = uiState.tagSuggestions,
            now = uiState.now,
            onSave = onSave,
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
        )
    }
}

private val previewUiState =
    LogDetailScreenUiState(
        isLoading = false,
        durationMode = DurationMode.MANUAL,
        intensityEnabled = true,
        initialDraft =
            LogDraft(
                occurredAt = 0L,
                intensity = 3,
                durationAmount = "45",
                durationUnit = DurationUnit.MINUTES,
                note = "Longer than it felt at the time.",
                tags = listOf("morning"),
                endedAt = null,
                existingEndedAt = null,
            ),
        now = 0L,
    )

@Composable
private fun LogDetailScreenPreviewContent() {
    LogDetailScreen(uiState = previewUiState, onSave = {}, onBack = {}, onDelete = {})
}

@Preview(name = "LogDetailScreen — Plain light", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun LogDetailScreenPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) { LogDetailScreenPreviewContent() }
    }
}

@Preview(name = "LogDetailScreen — Plain dark", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun LogDetailScreenPlainDarkPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = true) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) { LogDetailScreenPreviewContent() }
    }
}

@Preview(name = "LogDetailScreen — Intense", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun LogDetailScreenIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.INTENSE,
            LocalVoice provides voiceFor(AppTheme.INTENSE),
        ) { LogDetailScreenPreviewContent() }
    }
}

@Preview(name = "LogDetailScreen — Bright", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun LogDetailScreenBrightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) { LogDetailScreenPreviewContent() }
    }
}
