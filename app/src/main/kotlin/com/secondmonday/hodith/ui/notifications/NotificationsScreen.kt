package com.secondmonday.hodith.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.common.FabListBottomClearance
import com.secondmonday.hodith.ui.common.NumberStepper
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.filterDigitInput
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.NotificationRow
import com.secondmonday.hodith.viewmodel.NotificationsUiState
import com.secondmonday.hodith.viewmodel.NotificationsViewModel

@Composable
fun NotificationsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NotificationsScreen(
        uiState = uiState,
        onBack = onBack,
        onCreateNotification = viewModel::createNotification,
        onSetEnabled = viewModel::setEnabled,
        onDeleteNotification = viewModel::deleteNotification,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onBack: () -> Unit,
    onCreateNotification: (kind: NotificationKind, threshold: Int, windowDays: Int?) -> Unit,
    onSetEnabled: (notificationId: Long, enabled: Boolean) -> Unit,
    onDeleteNotification: (notificationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var showCreateSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<NotificationRow?>(null) }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = voice.notificationsDeleteConfirmTitle,
            body = voice.notificationsDeleteConfirmBody,
            confirmLabel = voice.notificationsDeleteConfirmAction,
            cancelLabel = voice.notificationsDeleteCancelAction,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteNotification(target.id)
                deleteTarget = null
            },
        )
    }

    if (showCreateSheet) {
        NotificationCreationSheet(
            voice = voice,
            onDismiss = { showCreateSheet = false },
            onSave = { kind, threshold, windowDays ->
                onCreateNotification(kind, threshold, windowDays)
                showCreateSheet = false
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.notificationsScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.notifications.isNotEmpty()) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = voice.notificationsFabDescription)
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> Unit
                uiState.notifications.isEmpty() -> {
                    NotificationsEmptyState(
                        voice = voice,
                        onCreate = { showCreateSheet = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = FabListBottomClearance),
                    ) {
                        items(uiState.notifications, key = { it.id }) { row ->
                            NotificationListItem(
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
private fun NotificationsEmptyState(
    voice: Voice,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(voice.notificationsEmptyTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(voice.notificationsEmptyBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onCreate) { Text(voice.notificationsEmptyCta) }
    }
}

@Composable
private fun NotificationListItem(
    row: NotificationRow,
    voice: Voice,
    onSetEnabled: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val summary = voice.notificationSummary(row.kind, row.threshold, row.windowDays)
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
                Text(voice.notificationKindLabel(row.kind), style = MaterialTheme.typography.bodySmall)
                row.firedDaysAgo?.let { daysAgo ->
                    Text(
                        voice.notificationFiredAgo(daysAgo),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(
                checked = row.enabled,
                onCheckedChange = onSetEnabled,
                modifier = Modifier.semantics { contentDescription = voice.notificationToggleDescription(summary) },
            )
            IconButton(onClick = onRequestDelete) {
                Icon(Icons.Filled.Delete, contentDescription = voice.notificationDeleteDescription(summary))
            }
        }
    }
}

private enum class WindowPreset { SEVEN, THIRTY, CUSTOM }

internal val THRESHOLD_RANGE = 1..999
private const val DEFAULT_OFTEN_THRESHOLD = 5
private const val DEFAULT_QUIET_THRESHOLD = 14
private const val DEFAULT_CUSTOM_WINDOW_DAYS = 14
private const val SEVEN_DAYS = 7
private const val THIRTY_DAYS = 30
private const val CUSTOM_WINDOW_MAX_DIGITS = 3

/** New-Notification bottom sheet (spec §11/§14): kind, threshold, and — for [NotificationKind.OFTEN] — a rolling window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCreationSheet(
    voice: Voice,
    onDismiss: () -> Unit,
    onSave: (kind: NotificationKind, threshold: Int, windowDays: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf(NotificationKind.OFTEN) }
    var oftenThreshold by remember { mutableIntStateOf(DEFAULT_OFTEN_THRESHOLD) }
    var quietThreshold by remember { mutableIntStateOf(DEFAULT_QUIET_THRESHOLD) }
    var windowPreset by remember { mutableStateOf(WindowPreset.SEVEN) }
    var customWindowText by remember { mutableStateOf(DEFAULT_CUSTOM_WINDOW_DAYS.toString()) }

    val windowDays =
        when (windowPreset) {
            WindowPreset.SEVEN -> SEVEN_DAYS
            WindowPreset.THIRTY -> THIRTY_DAYS
            WindowPreset.CUSTOM -> customWindowText.toIntOrNull()
        }
    val canSave = kind == NotificationKind.QUIET || (windowDays != null && windowDays > 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(voice.notificationsCreateTitle, style = MaterialTheme.typography.titleLarge)

            Column {
                Text(voice.notificationsKindPickerLabel, style = MaterialTheme.typography.labelLarge)
                SegmentedChoiceRow(
                    options =
                        listOf(
                            NotificationKind.OFTEN to voice.notificationKindLabel(NotificationKind.OFTEN),
                            NotificationKind.QUIET to voice.notificationKindLabel(NotificationKind.QUIET),
                        ),
                    selected = kind,
                    onSelect = { kind = it },
                )
            }

            if (kind == NotificationKind.OFTEN) {
                Column {
                    Text(voice.notificationsOftenLabel, style = MaterialTheme.typography.labelLarge)
                    NumberStepper(
                        value = oftenThreshold,
                        range = THRESHOLD_RANGE,
                        suffix = voice.notificationsOftenSuffix,
                        decreaseDescription = voice.notificationsDecreaseCountDescription,
                        increaseDescription = voice.notificationsIncreaseCountDescription,
                        onChange = { oftenThreshold = it },
                    )
                }
                Column {
                    Text(voice.notificationsWindowLabel, style = MaterialTheme.typography.labelLarge)
                    SegmentedChoiceRow(
                        options =
                            listOf(
                                WindowPreset.SEVEN to voice.notificationsWindowSeven,
                                WindowPreset.THIRTY to voice.notificationsWindowThirty,
                                WindowPreset.CUSTOM to voice.notificationsWindowCustom,
                            ),
                        selected = windowPreset,
                        onSelect = { windowPreset = it },
                    )
                    if (windowPreset == WindowPreset.CUSTOM) {
                        OutlinedTextField(
                            value = customWindowText,
                            onValueChange = { customWindowText = filterDigitInput(it, maxDigits = CUSTOM_WINDOW_MAX_DIGITS) },
                            label = { Text(voice.notificationsWindowCustomHint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            } else {
                Column {
                    Text(voice.notificationsQuietLabel, style = MaterialTheme.typography.labelLarge)
                    NumberStepper(
                        value = quietThreshold,
                        range = THRESHOLD_RANGE,
                        suffix = voice.notificationsQuietSuffix,
                        decreaseDescription = voice.notificationsDecreaseCountDescription,
                        increaseDescription = voice.notificationsIncreaseCountDescription,
                        onChange = { quietThreshold = it },
                    )
                }
            }

            Button(
                onClick = {
                    val threshold = if (kind == NotificationKind.OFTEN) oftenThreshold else quietThreshold
                    onSave(kind, threshold, if (kind == NotificationKind.OFTEN) windowDays else null)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(voice.notificationsSaveButton)
            }
        }
    }
}
