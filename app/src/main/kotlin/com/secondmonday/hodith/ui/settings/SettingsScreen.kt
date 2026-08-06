package com.secondmonday.hodith.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.SectionWithInfo
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.GlowCard
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.BackupEvent
import com.secondmonday.hodith.viewmodel.ImportFailureReason
import com.secondmonday.hodith.viewmodel.SettingsUiState
import com.secondmonday.hodith.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    fun showComingSoonSnackbar() {
        coroutineScope.launch { snackbarHostState.showSnackbar(voice.comingSoonPlaceholder, duration = SnackbarDuration.Short) }
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Plank(voice.settingsSupportSectionLabel) {
                ActionRow(voice.aboutScreenTitle, onClick = onOpenAbout)
                ActionRow(voice.settingsRateAppButton, onClick = { showComingSoonSnackbar() })
                ActionRow(voice.settingsContactUsButton, onClick = { showComingSoonSnackbar() })
            }

            Plank(voice.settingsAppearanceSectionLabel) {
                ThemeSection(theme = uiState.theme, voice = voice, onThemeSelect = onThemeSelect)
            }

            // No outer AreaHeader here: CheckInSection already carries its own label + info icon
            // (unlike Theme, there's no separate "area" beyond the one control), so an extra plank
            // title would just repeat "Check-ins" twice.
            Plank(title = null) {
                CheckInSection(
                    interval = uiState.checkInDefaultInterval,
                    voice = voice,
                    onCheckInDefaultIntervalSelect = onCheckInDefaultIntervalSelect,
                )
            }

            Plank(voice.settingsDataSectionLabel) {
                ActionRow(voice.settingsExportButton, onClick = onExportClick)
                ActionRow(voice.settingsImportButton, onClick = { showImportConfirm = true })
                ActionRow(voice.settingsDeleteAllDataButton, onClick = { showDeleteAllConfirm = true }, isDestructive = true)
            }

            if (uiState.developerModeUnlocked) {
                Plank(voice.settingsDeveloperModeSectionLabel) {
                    ActionRow(voice.settingsLoadDemoDataButton, onClick = onLoadDemoData)
                }
            }
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

    SectionWithInfo(
        label = voice.settingsThemeSectionLabel,
        infoTitle = voice.settingsThemeInfoTitle,
        infoBody = voice.settingsThemeInfoBody,
        infoDescription = voice.caseSectionInfoDescription,
    ) {
        SegmentedChoiceRow(options = options, selected = theme, onSelect = onThemeSelect)
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

/**
 * Area grouping for the Settings screen: a crisp white/thin-border card with a small-caps title.
 * Bright branches to [GlowCard] (Soft Glow mockup's `.plank`), same dispatch as
 * [com.secondmonday.hodith.ui.casedetail.InsightsTab]'s `InsightsCard`.
 */
@Composable
private fun Plank(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            GlowCard {
                title?.let { AreaHeader(it) }
                content()
            }
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    title?.let { AreaHeader(it) }
                    content()
                }
            }
    }
}

@Composable
private fun AreaHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT -> BrightActionRow(label, onClick, isDestructive)
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE ->
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    if (isDestructive) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
            ) { Text(label) }
    }
}

/**
 * Bright-only flat label + chevron row (Soft Glow mockup's `.arow`) — [GlowCard]'s tinted surface
 * already reads as chrome, so a filled button pill on top of it would double up; Plain/Intense
 * keep [FilledTonalButton] (tracked separately in PROGRESS.md's `FilledTonalButton` item).
 */
@Composable
private fun BrightActionRow(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean,
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = contentColor, style = MaterialTheme.typography.labelLarge)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (isDestructive) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Exercises [Plank]'s Bright branch and [BrightActionRow], including the destructive tint. */
@Composable
private fun SettingsBrightPlankPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
        LocalVoice provides voiceFor(AppTheme.BRIGHT),
    ) {
        val voice = LocalVoice.current
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Plank(voice.settingsSupportSectionLabel) {
                ActionRow(voice.aboutScreenTitle, onClick = {})
                ActionRow(voice.settingsRateAppButton, onClick = {})
                ActionRow(voice.settingsContactUsButton, onClick = {})
            }
            Plank(voice.settingsDataSectionLabel) {
                ActionRow(voice.settingsExportButton, onClick = {})
                ActionRow(voice.settingsDeleteAllDataButton, onClick = {}, isDestructive = true)
            }
        }
    }
}

@Preview(name = "Settings planks — Bright light", showBackground = true, widthDp = 360)
@Composable
private fun SettingsBrightPlankLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        SettingsBrightPlankPreviewContent()
    }
}

@Preview(name = "Settings planks — Bright dark", showBackground = true, widthDp = 360)
@Composable
private fun SettingsBrightPlankDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        SettingsBrightPlankPreviewContent()
    }
}
