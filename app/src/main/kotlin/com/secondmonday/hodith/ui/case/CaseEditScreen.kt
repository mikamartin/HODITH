package com.secondmonday.hodith.ui.case

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.RowWithInfo
import com.secondmonday.hodith.ui.common.SectionWithInfo
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.IconHalo
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.CaseEditUiState
import com.secondmonday.hodith.viewmodel.CaseEditViewModel
import com.secondmonday.hodith.viewmodel.isOneTapAllowed

private val ICON_CHOICE_SIZE = 48.dp

/** Matches [IconHalo]'s own default size, so the selected icon's halo isn't rescaled from its usual look. */
private val BRIGHT_ICON_CHOICE_VISUAL_SIZE = 34.dp

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
        onCheckInToggle = viewModel::onCheckInToggle,
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
    onCheckInToggle: (Boolean) -> Unit,
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
                            Icon(Icons.Filled.Delete, contentDescription = voice.archiveCaseDescription)
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
            onCheckInToggle = onCheckInToggle,
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
    onCheckInToggle: (Boolean) -> Unit,
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
        val fieldShape = caseEditTextFieldShape()

        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text(voice.caseNameLabel) },
            placeholder = { Text(voice.caseNameHint) },
            isError = uiState.showNameError || uiState.showDuplicateNameError,
            supportingText = {
                when {
                    uiState.showNameError -> Text(voice.caseNameRequiredError)
                    uiState.showDuplicateNameError -> Text(voice.caseNameDuplicateError)
                }
            },
            shape = fieldShape,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.description,
            onValueChange = onDescriptionChange,
            label = { Text(voice.caseDescriptionLabel) },
            placeholder = { Text(voice.caseDescriptionHint) },
            minLines = 2,
            shape = fieldShape,
            modifier = Modifier.fillMaxWidth(),
        )

        IconPickerSection(uiState = uiState, voice = voice, onIconSelect = onIconSelect)

        SectionWithInfo(voice.caseLogFlowLabel, voice.caseLogFlowInfoTitle, voice.caseLogFlowInfoBody, voice.caseSectionInfoDescription) {
            SegmentedChoiceRow(
                options = listOf(LogFlow.ONE_TAP to voice.caseLogFlowOneTap, LogFlow.DETAIL_SHEET to voice.caseLogFlowDetailSheet),
                selected = uiState.logFlow,
                onSelect = onLogFlowChange,
                enabled = { logFlow -> logFlow != LogFlow.ONE_TAP || isOneTapAllowed(uiState.durationMode, uiState.intensityEnabled) },
            )
        }

        SectionWithInfo(
            voice.caseDurationModeLabel,
            voice.caseDurationModeInfoTitle,
            voice.caseDurationModeInfoBody,
            voice.caseSectionInfoDescription,
        ) {
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

        RowWithInfo(voice.caseCheckInLabel, voice.caseCheckInInfoTitle, voice.caseCheckInInfoBody, voice.caseSectionInfoDescription) {
            Switch(checked = uiState.checkInsEnabled, onCheckedChange = onCheckInToggle, colors = caseEditSwitchColors())
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(voice.caseSaveButton)
        }
    }
}

@Composable
private fun IconPickerSection(
    uiState: CaseEditUiState,
    voice: Voice,
    onIconSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(!uiState.isEditing) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(voice.caseIconLabel, style = MaterialTheme.typography.labelLarge)
                if (!expanded && uiState.icon != null) {
                    Text(uiState.icon, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription =
                    if (expanded) voice.caseIconSectionCollapseDescription else voice.caseIconSectionExpandDescription,
            )
        }
        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp).selectableGroup(),
            ) {
                CASE_ICONS.forEach { icon ->
                    IconChoice(icon = icon, selected = icon == uiState.icon, onClick = { onIconSelect(icon) })
                }
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
}

@Composable
private fun IconChoice(
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT -> BrightIconChoice(icon = icon, selected = selected, onClick = onClick)
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> {
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
    }
}

/**
 * Bright-only icon choice (Soft Glow mockup's `.icon-choice`/`.icon-choice.on`): the selected icon
 * gets [IconHalo]'s tint-wash + glow ring, the rest a plain thin-bordered circle — both sized to
 * [IconHalo]'s own default, smaller than the 48dp touch target ([ICON_CHOICE_SIZE]) they sit inside,
 * same touch-target-larger-than-visual pattern as the ripple already clipped to a circle for
 * Plain/Intense's [IconChoice] branch above.
 */
@Composable
private fun BrightIconChoice(
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(ICON_CHOICE_SIZE)
                .clip(CircleShape)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            IconHalo(size = BRIGHT_ICON_CHOICE_VISUAL_SIZE, tint = MaterialTheme.colorScheme.primary) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .size(BRIGHT_ICON_CHOICE_VISUAL_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
            }
        }
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = caseEditSwitchColors())
    }
}

/** Bright-only pill switch colors matching the mockup's `.mswitch`/`.mswitch.on` (white thumb both states, tinted track when on). */
@Composable
private fun caseEditSwitchColors(): SwitchColors =
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                uncheckedBorderColor = Color.Transparent,
            )
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> SwitchDefaults.colors()
    }

/** Bright-only field shape matching the mockup's `.field .input` 16dp radius (Bright's `shapes.small`); Plain/Intense keep the M3 default. */
@Composable
private fun caseEditTextFieldShape() =
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT -> MaterialTheme.shapes.small
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> OutlinedTextFieldDefaults.shape
    }

private val previewUiState =
    CaseEditUiState(
        isEditing = true,
        isLoading = false,
        name = "Went for a run",
        description = "Any jog, walk, or run counted with intention.",
        icon = CASE_ICONS.first(),
        logFlow = LogFlow.DETAIL_SHEET,
        durationMode = DurationMode.START_STOP,
        intensityEnabled = true,
        checkInsEnabled = false,
        canArchive = true,
    )

/** Exercises the full Bright form: fields, a selected icon, both segmented rows, and both switch states (one on, one off). */
@Preview(name = "CaseEditScreen — Bright light", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun CaseEditScreenBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) {
            CaseEditScreen(
                uiState = previewUiState,
                onNameChange = {},
                onDescriptionChange = {},
                onIconSelect = {},
                onLogFlowChange = {},
                onDurationModeChange = {},
                onIntensityToggle = {},
                onCheckInToggle = {},
                onSave = {},
                onArchive = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "CaseEditScreen — Bright dark", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun CaseEditScreenBrightDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) {
            CaseEditScreen(
                uiState = previewUiState,
                onNameChange = {},
                onDescriptionChange = {},
                onIconSelect = {},
                onLogFlowChange = {},
                onDurationModeChange = {},
                onIntensityToggle = {},
                onCheckInToggle = {},
                onSave = {},
                onArchive = {},
                onBack = {},
            )
        }
    }
}
