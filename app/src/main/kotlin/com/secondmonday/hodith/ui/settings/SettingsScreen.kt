package com.secondmonday.hodith.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.SectionWithInfo
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.BackupEvent
import com.secondmonday.hodith.viewmodel.ImportFailureReason
import com.secondmonday.hodith.viewmodel.SettingsUiState
import com.secondmonday.hodith.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.Flow

private const val BACKUP_FILE_NAME = "hodith-backup.json"
private const val BACKUP_MIME_TYPE = "application/json"

@Composable
fun SettingsRoute(
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
            uri?.let(viewModel::exportData)
        }
    // "*/*" rather than the JSON mime type: many file providers report backup files as
    // application/octet-stream or text/plain, so a stricter filter would hide valid files.
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importData)
        }
    SettingsScreen(
        uiState = uiState,
        demoDataLoaded = viewModel.demoDataLoaded,
        backupEvents = viewModel.backupEvents,
        onThemeSelect = viewModel::onThemeSelect,
        onCheckInDefaultIntervalSelect = viewModel::onCheckInDefaultIntervalSelect,
        onLoadDemoData = viewModel::loadDemoData,
        onDeleteAllData = viewModel::deleteAllData,
        onExportClick = { exportLauncher.launch(BACKUP_FILE_NAME) },
        onImportConfirm = { importLauncher.launch(arrayOf("*/*")) },
        onOpenAbout = onOpenAbout,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    demoDataLoaded: Flow<Unit>,
    backupEvents: Flow<BackupEvent>,
    onThemeSelect: (AppTheme) -> Unit,
    onCheckInDefaultIntervalSelect: (CheckInDefaultInterval) -> Unit,
    onLoadDemoData: () -> Unit,
    onDeleteAllData: () -> Unit,
    onExportClick: () -> Unit,
    onImportConfirm: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        demoDataLoaded.collect {
            snackbarHostState.showSnackbar(voice.settingsDemoDataLoadedMessage, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(Unit) {
        backupEvents.collect { event ->
            val message =
                when (event) {
                    BackupEvent.ExportSuccess -> voice.settingsExportSuccessMessage
                    BackupEvent.ExportFailure -> voice.settingsExportFailureMessage
                    BackupEvent.ImportSuccess -> voice.settingsImportSuccessMessage
                    is BackupEvent.ImportFailure ->
                        when (event.reason) {
                            ImportFailureReason.INVALID -> voice.settingsImportFailureInvalidMessage
                            ImportFailureReason.UNSUPPORTED_VERSION -> voice.settingsImportFailureVersionMessage
                            ImportFailureReason.IO_ERROR -> voice.settingsImportFailureIoMessage
                        }
                }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
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

    if (showImportConfirm) {
        ConfirmDialog(
            title = voice.settingsImportConfirmTitle,
            body = voice.settingsImportConfirmBody,
            confirmLabel = voice.settingsImportConfirmAction,
            cancelLabel = voice.settingsImportCancelAction,
            onDismiss = { showImportConfirm = false },
            onConfirm = {
                showImportConfirm = false
                onImportConfirm()
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
            CheckInSection(
                interval = uiState.checkInDefaultInterval,
                voice = voice,
                onCheckInDefaultIntervalSelect = onCheckInDefaultIntervalSelect,
            )
            DemoDataSection(voice = voice, onLoadDemoData = onLoadDemoData, onDeleteAllData = { showDeleteAllConfirm = true })
            BackupSection(voice = voice, onExportClick = onExportClick, onImportClick = { showImportConfirm = true })
            AboutSection(voice = voice, onOpenAbout = onOpenAbout)
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
            AppTheme.PLAIN to voice.themeOptionPlain,
            AppTheme.INTENSE to voice.themeOptionIntense,
            AppTheme.BRIGHT to voice.themeOptionBright,
        )

    Column {
        Text(voice.settingsThemeSectionLabel, style = MaterialTheme.typography.labelLarge)
        SegmentedChoiceRow(options = options, selected = theme, onSelect = onThemeSelect)

        val previewVoice = voiceFor(theme)
        HodithTheme(theme = theme) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(voice.settingsPreviewLabel, style = MaterialTheme.typography.labelMedium)
                    Text(previewVoice.homeHeaderTitle, style = MaterialTheme.typography.headlineSmall)
                    Text(previewVoice.noCasesEmptyState, style = MaterialTheme.typography.bodyMedium)
                    Text(previewVoice.bigPictureEarlyDays, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {}, enabled = false) {
                        Text(previewVoice.caseSaveButton)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInSection(
    interval: CheckInDefaultInterval,
    voice: Voice,
    onCheckInDefaultIntervalSelect: (CheckInDefaultInterval) -> Unit,
) {
    val options =
        listOf(
            CheckInDefaultInterval.OFF to voice.checkInIntervalOptionOff,
            CheckInDefaultInterval.SEVEN to voice.checkInIntervalOptionSeven,
            CheckInDefaultInterval.FOURTEEN to voice.checkInIntervalOptionFourteen,
            CheckInDefaultInterval.THIRTY to voice.checkInIntervalOptionThirty,
        )

    SectionWithInfo(
        label = voice.settingsCheckInSectionLabel,
        infoTitle = voice.settingsCheckInInfoTitle,
        infoBody = voice.settingsCheckInInfoBody,
        infoDescription = voice.caseSectionInfoDescription,
        labelStyle = MaterialTheme.typography.labelLarge,
    ) {
        SegmentedChoiceRow(options = options, selected = interval, onSelect = onCheckInDefaultIntervalSelect)
    }
}

@Composable
private fun BackupSection(
    voice: Voice,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    Column {
        Text(voice.settingsBackupSectionLabel, style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportClick, modifier = Modifier.fillMaxWidth()) {
                Text(voice.settingsExportButton)
            }
            OutlinedButton(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
                Text(voice.settingsImportButton)
            }
        }
    }
}

@Composable
private fun AboutSection(
    voice: Voice,
    onOpenAbout: () -> Unit,
) {
    OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
        Text(voice.aboutScreenTitle)
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
