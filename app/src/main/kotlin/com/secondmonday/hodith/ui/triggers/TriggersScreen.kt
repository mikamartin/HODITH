package com.secondmonday.hodith.ui.triggers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.NumberStepper
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.filterDigitInput
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.TriggerRow
import com.secondmonday.hodith.viewmodel.TriggersUiState
import com.secondmonday.hodith.viewmodel.TriggersViewModel

@Composable
fun TriggersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TriggersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TriggersScreen(
        uiState = uiState,
        onBack = onBack,
        onCreateTrigger = viewModel::createTrigger,
        onSetEnabled = viewModel::setEnabled,
        onDeleteTrigger = viewModel::deleteTrigger,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(
    uiState: TriggersUiState,
    onBack: () -> Unit,
    onCreateTrigger: (kind: TriggerKind, threshold: Int, windowDays: Int?) -> Unit,
    onSetEnabled: (triggerId: Long, enabled: Boolean) -> Unit,
    onDeleteTrigger: (triggerId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var showCreateSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TriggerRow?>(null) }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = voice.triggersDeleteConfirmTitle,
            body = voice.triggersDeleteConfirmBody,
            confirmLabel = voice.triggersDeleteConfirmAction,
            cancelLabel = voice.triggersDeleteCancelAction,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteTrigger(target.id)
                deleteTarget = null
            },
        )
    }

    if (showCreateSheet) {
        TriggerCreationSheet(
            voice = voice,
            onDismiss = { showCreateSheet = false },
            onSave = { kind, threshold, windowDays ->
                onCreateTrigger(kind, threshold, windowDays)
                showCreateSheet = false
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.triggersScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.triggers.isNotEmpty()) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = voice.triggersFabDescription)
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> Unit
                uiState.triggers.isEmpty() -> {
                    TriggersEmptyState(
                        voice = voice,
                        onCreate = { showCreateSheet = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.triggers, key = { it.id }) { row ->
                            TriggerListItem(
                                row = row,
                                voice = voice,
                                onSetEnabled = { enabled -> onSetEnabled(row.id, enabled) },
                                onRequestDelete = { deleteTarget = row },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggersEmptyState(
    voice: Voice,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(voice.triggersEmptyTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(voice.triggersEmptyBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onCreate) { Text(voice.triggersEmptyCta) }
    }
}

@Composable
private fun TriggerListItem(
    row: TriggerRow,
    voice: Voice,
    onSetEnabled: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val summary = voice.triggerSummary(row.kind, row.threshold, row.windowDays)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .alpha(if (row.enabled) 1f else 0.55f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.titleSmall)
                Text(voice.triggerKindLabel(row.kind), style = MaterialTheme.typography.bodySmall)
                row.firedDaysAgo?.let { daysAgo ->
                    Text(
                        voice.triggerFiredAgo(daysAgo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(
                checked = row.enabled,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.semantics { contentDescription = voice.triggerToggleDescription(summary) },
            )
            IconButton(onClick = onRequestDelete) {
                Icon(Icons.Filled.Delete, contentDescription = voice.triggerDeleteDescription(summary))
            }
        }
    }
}

private enum class WindowPreset { SEVEN, THIRTY, CUSTOM }

private val THRESHOLD_RANGE = 1..999
private const val DEFAULT_AT_LEAST_THRESHOLD = 5
private const val DEFAULT_SILENT_THRESHOLD = 14
private const val DEFAULT_CUSTOM_WINDOW_DAYS = 14
private const val SEVEN_DAYS = 7
private const val THIRTY_DAYS = 30
private const val CUSTOM_WINDOW_MAX_DIGITS = 3

/** New-Trigger bottom sheet (spec §11/§14): kind, threshold, and — for [TriggerKind.AT_LEAST] — a rolling window. Same shape as [com.secondmonday.hodith.ui.casedetail.HunchCreationSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerCreationSheet(
    voice: Voice,
    onDismiss: () -> Unit,
    onSave: (kind: TriggerKind, threshold: Int, windowDays: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(TriggerKind.AT_LEAST) }
    var atLeastThreshold by remember { mutableIntStateOf(DEFAULT_AT_LEAST_THRESHOLD) }
    var silentThreshold by remember { mutableIntStateOf(DEFAULT_SILENT_THRESHOLD) }
    var windowPreset by remember { mutableStateOf(WindowPreset.SEVEN) }
    var customWindowText by remember { mutableStateOf(DEFAULT_CUSTOM_WINDOW_DAYS.toString()) }

    val windowDays =
        when (windowPreset) {
            WindowPreset.SEVEN -> SEVEN_DAYS
            WindowPreset.THIRTY -> THIRTY_DAYS
            WindowPreset.CUSTOM -> customWindowText.toIntOrNull()
        }
    val canSave = kind == TriggerKind.SILENT_FOR || windowDays != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(voice.triggersCreateTitle, style = MaterialTheme.typography.titleLarge)

            Column {
                Text(voice.triggersKindPickerLabel, style = MaterialTheme.typography.labelLarge)
                SegmentedChoiceRow(
                    options =
                        listOf(
                            TriggerKind.AT_LEAST to voice.triggerKindLabel(TriggerKind.AT_LEAST),
                            TriggerKind.SILENT_FOR to voice.triggerKindLabel(TriggerKind.SILENT_FOR),
                        ),
                    selected = kind,
                    onSelect = { kind = it },
                )
            }

            if (kind == TriggerKind.AT_LEAST) {
                Column {
                    Text(voice.triggersAtLeastLabel, style = MaterialTheme.typography.labelLarge)
                    NumberStepper(
                        value = atLeastThreshold,
                        range = THRESHOLD_RANGE,
                        suffix = voice.triggersAtLeastSuffix,
                        decreaseDescription = voice.triggersDecreaseCountDescription,
                        increaseDescription = voice.triggersIncreaseCountDescription,
                        onChange = { atLeastThreshold = it },
                    )
                }
                Column {
                    Text(voice.triggersWindowLabel, style = MaterialTheme.typography.labelLarge)
                    SegmentedChoiceRow(
                        options =
                            listOf(
                                WindowPreset.SEVEN to voice.triggersWindowSeven,
                                WindowPreset.THIRTY to voice.triggersWindowThirty,
                                WindowPreset.CUSTOM to voice.triggersWindowCustom,
                            ),
                        selected = windowPreset,
                        onSelect = { windowPreset = it },
                    )
                    if (windowPreset == WindowPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customWindowText,
                            onValueChange = { customWindowText = filterDigitInput(it, maxDigits = CUSTOM_WINDOW_MAX_DIGITS) },
                            label = { Text(voice.triggersWindowCustomHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            } else {
                Column {
                    Text(voice.triggersSilentLabel, style = MaterialTheme.typography.labelLarge)
                    NumberStepper(
                        value = silentThreshold,
                        range = THRESHOLD_RANGE,
                        suffix = voice.triggersSilentSuffix,
                        decreaseDescription = voice.triggersDecreaseCountDescription,
                        increaseDescription = voice.triggersIncreaseCountDescription,
                        onChange = { silentThreshold = it },
                    )
                }
            }

            Button(
                onClick = {
                    val threshold = if (kind == TriggerKind.AT_LEAST) atLeastThreshold else silentThreshold
                    onSave(kind, threshold, if (kind == TriggerKind.AT_LEAST) windowDays else null)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(voice.triggersSaveButton)
            }
        }
    }
}
