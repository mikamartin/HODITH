package com.secondmonday.hodith.ui.case

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CaseEditUiState
import com.secondmonday.hodith.viewmodel.CaseEditViewModel
import com.secondmonday.hodith.viewmodel.CheckInOption

private val ICON_CHOICE_SIZE = 48.dp

@Composable
fun CaseEditRoute(
    onDone: () -> Unit,
    onArchived: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    LaunchedEffect(uiState.isArchived) {
        if (uiState.isArchived) onArchived()
    }

    CaseEditScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onIconSelect = viewModel::onIconSelect,
        onLogFlowChange = viewModel::onLogFlowChange,
        onDurationModeChange = viewModel::onDurationModeChange,
        onIntensityToggle = viewModel::onIntensityToggle,
        onPinnedToggle = viewModel::onPinnedToggle,
        onCheckInOptionChange = viewModel::onCheckInOptionChange,
        onCheckInCustomDaysChange = viewModel::onCheckInCustomDaysChange,
        onSave = viewModel::save,
        onArchive = viewModel::archive,
        onBack = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseEditScreen(
    uiState: CaseEditUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIconSelect: (String) -> Unit,
    onLogFlowChange: (LogFlow) -> Unit,
    onDurationModeChange: (DurationMode) -> Unit,
    onIntensityToggle: (Boolean) -> Unit,
    onPinnedToggle: (Boolean) -> Unit,
    onCheckInOptionChange: (CheckInOption) -> Unit,
    onCheckInCustomDaysChange: (String) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var showArchiveConfirm by remember { mutableStateOf(false) }

    if (showArchiveConfirm) {
        ConfirmDialog(
            title = voice.archiveCaseConfirmTitle,
            body = voice.archiveCaseConfirmBody,
            confirmLabel = voice.archiveCaseConfirmAction,
            cancelLabel = voice.archiveCaseCancelAction,
            onDismiss = { showArchiveConfirm = false },
            onConfirm = {
                showArchiveConfirm = false
                onArchive()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) voice.editCaseTitle else voice.newCaseTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
                actions = {
                    if (uiState.canArchive) {
                        IconButton(onClick = { showArchiveConfirm = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = voice.archiveCaseDescription)
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        if (uiState.isLoading) return@Scaffold

        CaseEditForm(
            uiState = uiState,
            voice = voice,
            onNameChange = onNameChange,
            onDescriptionChange = onDescriptionChange,
            onIconSelect = onIconSelect,
            onLogFlowChange = onLogFlowChange,
            onDurationModeChange = onDurationModeChange,
            onIntensityToggle = onIntensityToggle,
            onPinnedToggle = onPinnedToggle,
            onCheckInOptionChange = onCheckInOptionChange,
            onCheckInCustomDaysChange = onCheckInCustomDaysChange,
            onSave = onSave,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaseEditForm(
    uiState: CaseEditUiState,
    voice: Voice,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIconSelect: (String) -> Unit,
    onLogFlowChange: (LogFlow) -> Unit,
    onDurationModeChange: (DurationMode) -> Unit,
    onIntensityToggle: (Boolean) -> Unit,
    onPinnedToggle: (Boolean) -> Unit,
    onCheckInOptionChange: (CheckInOption) -> Unit,
    onCheckInCustomDaysChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text(voice.caseNameLabel) },
            placeholder = { Text(voice.caseNameHint) },
            isError = uiState.showNameError,
            supportingText = { if (uiState.showNameError) Text(voice.caseNameRequiredError) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.description,
            onValueChange = onDescriptionChange,
            label = { Text(voice.caseDescriptionLabel) },
            placeholder = { Text(voice.caseDescriptionHint) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            Text(voice.caseIconLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp).selectableGroup(),
            ) {
                CASE_ICONS.forEach { icon ->
                    IconChoice(icon = icon, selected = icon == uiState.icon, onClick = { onIconSelect(icon) })
                }
            }
            if (uiState.showIconError) {
                Text(
                    voice.caseIconRequiredError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column {
            Text(voice.caseLogFlowLabel, style = MaterialTheme.typography.labelLarge)
            SegmentedChoiceRow(
                options = listOf(LogFlow.ONE_TAP to voice.caseLogFlowOneTap, LogFlow.DETAIL_SHEET to voice.caseLogFlowDetailSheet),
                selected = uiState.logFlow,
                onSelect = onLogFlowChange,
            )
        }

        Column {
            Text(voice.caseDurationModeLabel, style = MaterialTheme.typography.labelLarge)
            SegmentedChoiceRow(
                options =
                    listOf(
                        DurationMode.NONE to voice.caseDurationModeNone,
                        DurationMode.MANUAL to voice.caseDurationModeManual,
                        DurationMode.START_STOP to voice.caseDurationModeStartStop,
                    ),
                selected = uiState.durationMode,
                onSelect = onDurationModeChange,
            )
        }

        ToggleRow(label = voice.caseIntensityToggleLabel, checked = uiState.intensityEnabled, onCheckedChange = onIntensityToggle)
        ToggleRow(label = voice.casePinnedToggleLabel, checked = uiState.pinned, onCheckedChange = onPinnedToggle)

        Column(modifier = Modifier.selectableGroup()) {
            Text(voice.caseCheckInLabel, style = MaterialTheme.typography.labelLarge)
            CheckInOptionRow(voice.caseCheckInDefault, uiState.checkInOption == CheckInOption.DEFAULT) {
                onCheckInOptionChange(CheckInOption.DEFAULT)
            }
            CheckInOptionRow(voice.caseCheckInCustom, uiState.checkInOption == CheckInOption.CUSTOM) {
                onCheckInOptionChange(CheckInOption.CUSTOM)
            }
            if (uiState.checkInOption == CheckInOption.CUSTOM) {
                TextField(
                    value = uiState.checkInCustomDays,
                    onValueChange = onCheckInCustomDaysChange,
                    label = { Text(voice.caseCheckInCustomDaysHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(start = 40.dp),
                )
            }
            CheckInOptionRow(voice.caseCheckInOff, uiState.checkInOption == CheckInOption.OFF) {
                onCheckInOptionChange(CheckInOption.OFF)
            }
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(voice.caseSaveButton)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun IconChoice(
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier =
            Modifier
                .size(ICON_CHOICE_SIZE)
                .clip(CircleShape)
                .background(background)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckInOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}
