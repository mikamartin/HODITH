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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.RowWithInfo
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.common.themedSwitchColors
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.IconHalo
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.CaseEditUiState
import com.secondmonday.hodith.viewmodel.CaseEditViewModel
import com.secondmonday.hodith.viewmodel.IntensityToggleNotice
import com.secondmonday.hodith.viewmodel.isOneTapAllowed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
        intensityToggleNotice = viewModel.intensityToggleNotice,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onIconSelect = viewModel::onIconSelect,
        onLogFlowChange = viewModel::onLogFlowChange,
        onTracksDurationChange = viewModel::onTracksDurationChange,
        onConfirmTurnOffDuration = viewModel::confirmTurnOffDuration,
        onDismissTurnOffDuration = viewModel::dismissTurnOffDuration,
        onConfirmTurnOnDuration = viewModel::confirmTurnOnDuration,
        onDismissTurnOnDuration = viewModel::dismissTurnOnDuration,
        onIntensityToggle = viewModel::onIntensityToggle,
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
    intensityToggleNotice: Flow<IntensityToggleNotice>,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIconSelect: (String) -> Unit,
    onLogFlowChange: (LogFlow) -> Unit,
    onTracksDurationChange: (Boolean) -> Unit,
    onConfirmTurnOffDuration: () -> Unit,
    onDismissTurnOffDuration: () -> Unit,
    onConfirmTurnOnDuration: () -> Unit,
    onDismissTurnOnDuration: () -> Unit,
    onIntensityToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Toggling never touches existing events' intensity values either direction (spec §6) — this
    // transient notice, fired once per flip rather than shown as a standing fixture, is the only
    // way the user learns what that means for data already logged on this Case. Long (longer than
    // every other snackbar in the app) since it carries two sentences; withDismissAction gives it
    // a close button so reading it isn't gated on waiting out the full duration.
    LaunchedEffect(Unit) {
        intensityToggleNotice.collect { notice ->
            snackbarHostState.showSnackbar(
                message =
                    if (notice.enabled) {
                        voice.enableIntensityNotice(notice.eventCount)
                    } else {
                        voice.disableIntensityNotice(notice.eventCount)
                    },
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        }
    }

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

    if (uiState.showTurnOffDurationConfirm) {
        ConfirmDialog(
            title = voice.turnOffDurationConfirmTitle,
            body = voice.turnOffDurationConfirmBody(uiState.runningEventCount),
            confirmLabel = voice.turnOffDurationConfirmAction,
            cancelLabel = voice.turnOffDurationCancelAction,
            onDismiss = onDismissTurnOffDuration,
            onConfirm = onConfirmTurnOffDuration,
        )
    }

    if (uiState.showTurnOnDurationConfirm) {
        ConfirmDialog(
            title = voice.turnOnDurationConfirmTitle,
            body = voice.turnOnDurationConfirmBody(uiState.runningEventCount),
            confirmLabel = voice.turnOnDurationConfirmAction,
            cancelLabel = voice.turnOnDurationCancelAction,
            onDismiss = onDismissTurnOnDuration,
            onConfirm = onConfirmTurnOnDuration,
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Plain only: a form/editor screen, not a browsing one, so it stays white (surface) like
        // LogDetailSheet's ModalBottomSheet (whose M3 default containerColor is surface, not
        // background) instead of the tinted background every Scaffold defaults to. Intense and
        // Bright keep that default — each theme's colors stay independent of Plain's changes.
        containerColor =
            if (LocalCardDecorationStyle.current == CardDecorationStyle.PLAIN) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditing) voice.editCaseTitle else voice.newCaseTitle,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
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
            onTracksDurationChange = onTracksDurationChange,
            onIntensityToggle = onIntensityToggle,
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
    onTracksDurationChange: (Boolean) -> Unit,
    onIntensityToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    // A failed save's error lands on a field above the Save button -- invisible if that button
    // only came into view by scrolling down a longer form. Scrolling back to top on any of
    // these turning true (not re-running just because save() was clicked again with the same
    // outcome) puts whichever error fired back on screen instead of a silent no-op save.
    LaunchedEffect(uiState.showNameError, uiState.showDuplicateNameError, uiState.showDuplicateArchivedNameError, uiState.showIconError) {
        if (uiState.showNameError || uiState.showDuplicateNameError || uiState.showDuplicateArchivedNameError || uiState.showIconError) {
            scrollState.animateScrollTo(0)
        }
    }

    Column(
        modifier =
            modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val fieldShape = caseEditTextFieldShape()

        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text(voice.caseNameLabel) },
            placeholder = { Text(voice.caseNameHint) },
            isError = uiState.showNameError || uiState.showDuplicateNameError || uiState.showDuplicateArchivedNameError,
            supportingText = {
                when {
                    uiState.showNameError -> Text(voice.caseNameRequiredError)
                    uiState.showDuplicateNameError -> Text(voice.caseNameDuplicateError)
                    uiState.showDuplicateArchivedNameError -> Text(voice.caseNameDuplicateArchivedError)
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

        RowWithInfo(
            voice.caseTracksDurationLabel,
            voice.caseTracksDurationInfoTitle,
            voice.caseTracksDurationInfoBody,
            voice.caseSectionInfoDescription,
        ) {
            Switch(checked = uiState.tracksDuration, onCheckedChange = onTracksDurationChange, colors = themedSwitchColors())
        }

        ToggleRow(
            label = voice.caseIntensityToggleLabel,
            checked = uiState.intensityEnabled,
            onCheckedChange = onIntensityToggle,
            labelStyle = MaterialTheme.typography.labelLarge,
            colors = themedSwitchColors(),
        )

        if (isOneTapAllowed(uiState.intensityEnabled)) {
            RowWithInfo(
                voice.caseOneTapToggleLabel,
                voice.caseOneTapInfoTitle,
                voice.caseOneTapInfoBody,
                voice.caseSectionInfoDescription,
            ) {
                Switch(
                    checked = uiState.logFlow == LogFlow.ONE_TAP,
                    onCheckedChange = { checked -> onLogFlowChange(if (checked) LogFlow.ONE_TAP else LogFlow.DETAIL_SHEET) },
                    colors = themedSwitchColors(),
                )
            }
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
                uiState.availableIcons.forEach { icon ->
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
            val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            Box(
                modifier =
                    Modifier
                        .size(ICON_CHOICE_SIZE)
                        .clip(CircleShape)
                        .background(background)
                        .border(2.dp, ringColor, CircleShape)
                        .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = icon, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/**
 * Bright-only icon choice: the selected icon
 * gets [IconHalo]'s tint-wash + glow ring plus a solid primary-color ring at the circle's edge, the
 * rest a plain thin-bordered circle — both sized to [IconHalo]'s own default, smaller than the 48dp
 * touch target ([ICON_CHOICE_SIZE]) they sit inside, same touch-target-larger-than-visual pattern as
 * the ripple already clipped to a circle for Plain/Intense's [IconChoice] branch above. The ring
 * (not the halo/tint alone) is what marks selection so it doesn't depend on background contrast
 * (spec §3 principle 6).
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
            IconHalo(
                size = BRIGHT_ICON_CHOICE_VISUAL_SIZE,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            ) {
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

/** Bright-only field shape: a 16dp radius (Bright's `shapes.small`); Plain/Intense keep the M3 default. */
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
        icon = CASE_ICONS_DEFAULT.first(),
        logFlow = LogFlow.DETAIL_SHEET,
        tracksDuration = true,
        intensityEnabled = true,
        canArchive = true,
    )

/** Exercises the full Bright form: fields, a selected icon, the logFlow segmented row, and both switch states (one on, one off). */
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
                intensityToggleNotice = emptyFlow(),
                onNameChange = {},
                onDescriptionChange = {},
                onIconSelect = {},
                onLogFlowChange = {},
                onTracksDurationChange = {},
                onConfirmTurnOffDuration = {},
                onDismissTurnOffDuration = {},
                onConfirmTurnOnDuration = {},
                onDismissTurnOnDuration = {},
                onIntensityToggle = {},
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
                intensityToggleNotice = emptyFlow(),
                onNameChange = {},
                onDescriptionChange = {},
                onIconSelect = {},
                onLogFlowChange = {},
                onTracksDurationChange = {},
                onConfirmTurnOffDuration = {},
                onDismissTurnOffDuration = {},
                onConfirmTurnOnDuration = {},
                onDismissTurnOnDuration = {},
                onIntensityToggle = {},
                onSave = {},
                onArchive = {},
                onBack = {},
            )
        }
    }
}

/** Selected vs. unselected side by side, so the ring is visible against each theme's own colors. */
@Composable
private fun IconChoicePreviewContent() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        IconChoice(icon = CASE_ICONS_DEFAULT.first(), selected = true, onClick = {})
        IconChoice(icon = CASE_ICONS_DEFAULT.first(), selected = false, onClick = {})
    }
}

@Preview(name = "IconChoice — Plain light", showBackground = true, widthDp = 160)
@Composable
private fun IconChoicePlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.PLAIN) { IconChoicePreviewContent() }
    }
}

@Preview(name = "IconChoice — Plain dark", showBackground = true, widthDp = 160)
@Composable
private fun IconChoicePlainDarkPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = true) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.PLAIN) { IconChoicePreviewContent() }
    }
}

@Preview(name = "IconChoice — Intense", showBackground = true, widthDp = 160)
@Composable
private fun IconChoiceIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.INTENSE) { IconChoicePreviewContent() }
    }
}

@Preview(name = "IconChoice — Bright", showBackground = true, widthDp = 160)
@Composable
private fun IconChoiceBrightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) { IconChoicePreviewContent() }
    }
}
