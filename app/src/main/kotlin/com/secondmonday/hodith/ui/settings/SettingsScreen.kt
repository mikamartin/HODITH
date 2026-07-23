package com.secondmonday.hodith.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.SettingsUiState
import com.secondmonday.hodith.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.Flow

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        demoDataLoaded = viewModel.demoDataLoaded,
        onThemeSelect = viewModel::onThemeSelect,
        onLoadDemoData = viewModel::loadDemoData,
        onDeleteAllData = viewModel::deleteAllData,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    demoDataLoaded: Flow<Unit>,
    onThemeSelect: (AppTheme) -> Unit,
    onLoadDemoData: () -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        demoDataLoaded.collect {
            snackbarHostState.showSnackbar(voice.settingsDemoDataLoadedMessage, duration = SnackbarDuration.Short)
        }
    }

    if (showDeleteAllConfirm) {
        ConfirmDialog(
            title = voice.settingsDeleteAllDataConfirmTitle,
            body = voice.settingsDeleteAllDataConfirmBody,
            confirmLabel = voice.settingsDeleteAllDataConfirmAction,
            cancelLabel = voice.settingsDeleteAllDataCancelAction,
            onDismiss = { showDeleteAllConfirm = false },
            onConfirm = {
                showDeleteAllConfirm = false
                onDeleteAllData()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        if (uiState.isLoading) return@Scaffold

        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ThemeSection(theme = uiState.theme, voice = voice, onThemeSelect = onThemeSelect)
            DemoDataSection(voice = voice, onLoadDemoData = onLoadDemoData, onDeleteAllData = { showDeleteAllConfirm = true })
        }
    }
}

@Composable
private fun ThemeSection(
    theme: AppTheme,
    voice: Voice,
    onThemeSelect: (AppTheme) -> Unit,
) {
    val options =
        listOf(
            AppTheme.SERIOUS to voice.themeOptionSerious,
            AppTheme.GOTH to voice.themeOptionGoth,
            AppTheme.QUIRKY to voice.themeOptionQuirky,
        )

    Column {
        Text(voice.settingsThemeSectionLabel, style = MaterialTheme.typography.labelLarge)
        SegmentedChoiceRow(options = options, selected = theme, onSelect = onThemeSelect)

        val previewVoice = voiceFor(theme)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(voice.settingsPreviewLabel, style = MaterialTheme.typography.labelMedium)
                Text(previewVoice.noCasesEmptyState, style = MaterialTheme.typography.bodyMedium)
                Text(previewVoice.bigPictureEarlyDays, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DemoDataSection(
    voice: Voice,
    onLoadDemoData: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    Column {
        Text(voice.settingsDemoDataSectionLabel, style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoadDemoData, modifier = Modifier.fillMaxWidth()) {
                Text(voice.settingsLoadDemoDataButton)
            }
            TextButton(onClick = onDeleteAllData, modifier = Modifier.fillMaxWidth()) {
                Text(voice.settingsDeleteAllDataButton, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
