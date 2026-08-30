package com.secondmonday.hodith.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.common.NotificationsDeniedBanner
import com.secondmonday.hodith.ui.common.OngoingCountText
import com.secondmonday.hodith.ui.common.OngoingElapsedText
import com.secondmonday.hodith.ui.common.StaleOngoingBanner
import com.secondmonday.hodith.ui.common.acronymHighlighted
import com.secondmonday.hodith.ui.common.rememberTickingNow
import com.secondmonday.hodith.ui.logsheet.LogDetailSheet
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.GlowCard
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.IconHalo
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.HomeLogSheetState
import com.secondmonday.hodith.viewmodel.HomeUiState
import com.secondmonday.hodith.viewmodel.HomeViewModel
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.QuickLogUndo
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.isStaleOngoing
import kotlinx.coroutines.flow.Flow

@Composable
fun HomeRoute(
    onNewCase: () -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenArchivedCases: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logSheet by viewModel.logSheet.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        logSheet = logSheet,
        quickLogUndo = viewModel.quickLogUndo,
        onNewCase = onNewCase,
        onOpenCase = onOpenCase,
        onOpenArchivedCases = onOpenArchivedCases,
        onQuickLogTap = viewModel::onQuickLogTap,
        onDismissLogSheet = viewModel::dismissLogSheet,
        onSaveLogSheetEvent = viewModel::saveLogSheetEvent,
        onUndoQuickLog = viewModel::undoQuickLog,
        onDismissStalePrompt = viewModel::dismissStalePrompt,
        nowMillis = viewModel::nowMillis,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    logSheet: HomeLogSheetState?,
    quickLogUndo: Flow<QuickLogUndo>,
    onNewCase: () -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenArchivedCases: () -> Unit,
    onQuickLogTap: (HomeCaseRow) -> Unit,
    onDismissLogSheet: () -> Unit,
    onSaveLogSheetEvent: (LogDraft) -> Unit,
    onUndoQuickLog: (Long) -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    nowMillis: () -> Long,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        quickLogUndo.collect { undo ->
            val result =
                snackbarHostState.showSnackbar(
                    message = voice.quickLogUndoMessage(undo.caseName),
                    actionLabel = voice.quickLogUndoAction,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) onUndoQuickLog(undo.eventId)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewCase) {
                Icon(Icons.Filled.Add, contentDescription = voice.newCaseFabDescription)
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            Text(
                text = acronymHighlighted(voice.homeHeaderTitle, MaterialTheme.colorScheme.primary),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (uiState.notificationPermissionRequested) {
                NotificationsDeniedBanner(
                    voice = voice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> Unit
                    uiState.cases.isEmpty() -> {
                        Text(
                            text = voice.noCasesEmptyState,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(uiState.cases, key = { _, row -> row.caseId }) { index, row ->
                                HomeCaseListItem(
                                    row = row,
                                    voice = voice,
                                    isEvenRow = index % 2 == 0,
                                    onClick = { onOpenCase(row.caseId) },
                                    onQuickLogTap = { onQuickLogTap(row) },
                                    onEditEndTime = { onOpenCase(row.caseId) },
                                    onDismissStalePrompt = onDismissStalePrompt,
                                    nowMillis = nowMillis,
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.archivedCount > 0) {
                Text(
                    text = voice.archivedCasesLink(uiState.archivedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenArchivedCases)
                            .padding(16.dp),
                )
            }
        }
    }

    if (logSheet != null) {
        LogDetailSheet(
            isEditing = false,
            durationMode = logSheet.durationMode,
            intensityEnabled = logSheet.intensityEnabled,
            initialDraft = logSheet.draft,
            tagSuggestions = logSheet.tagSuggestions,
            now = logSheet.draft.occurredAt,
            onSave = onSaveLogSheetEvent,
            onDismiss = onDismissLogSheet,
        )
    }
}

/**
 * Dispatches to the active theme's row treatment (mirrors `BigPictureGrid.kt`'s `DayCell`
 * dispatch pattern): [CardDecorationStyle.BRIGHT] gets [BrightHomeCaseListItem], [PLAIN][
 * CardDecorationStyle.PLAIN] wraps the shared [HomeCaseRowBody] in a white plank card on the
 * screen's tinted background (see docs/mockups/plain-theme-light-neutrals.html), and
 * [INTENSE][CardDecorationStyle.INTENSE] renders [HomeCaseRowBody] flat, unchanged.
 */
@Composable
private fun HomeCaseListItem(
    row: HomeCaseRow,
    voice: Voice,
    isEvenRow: Boolean,
    onClick: () -> Unit,
    onQuickLogTap: () -> Unit,
    onEditEndTime: () -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    nowMillis: () -> Long,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            BrightHomeCaseListItem(row, voice, isEvenRow, onClick, onQuickLogTap, onEditEndTime, onDismissStalePrompt, nowMillis)
        CardDecorationStyle.PLAIN ->
            PlainPlankHomeCaseListItem(row, voice, onClick, onQuickLogTap, onEditEndTime, onDismissStalePrompt, nowMillis)
        CardDecorationStyle.INTENSE ->
            HomeCaseRowBody(row, voice, onClick, onQuickLogTap, onEditEndTime, onDismissStalePrompt, nowMillis)
    }
}

/** Plain: the same row content as a white [Card] plank, margined off the tinted screen background. */
@Composable
private fun PlainPlankHomeCaseListItem(
    row: HomeCaseRow,
    voice: Voice,
    onClick: () -> Unit,
    onQuickLogTap: () -> Unit,
    onEditEndTime: () -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    nowMillis: () -> Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        HomeCaseRowBody(row, voice, onClick, onQuickLogTap, onEditEndTime, onDismissStalePrompt, nowMillis)
    }
}

/**
 * A Case row's trailing log button. It stays put whether or not an event runs (spec §6) — on a
 * `START_STOP` Case with something open it starts a second concurrent event; Stop lives on the
 * Case's own log rows, reached by tapping the row.
 */
@Composable
private fun HomeCaseLogButton(
    row: HomeCaseRow,
    voice: Voice,
    onClick: () -> Unit,
) {
    val description =
        if (row.durationMode == DurationMode.START_STOP) {
            voice.startActionDescription(row.name)
        } else {
            voice.quickLogButtonDescription(row.name)
        }
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.AddCircle, contentDescription = description)
    }
}

@Composable
private fun HomeCaseRowBody(
    row: HomeCaseRow,
    voice: Voice,
    onClick: () -> Unit,
    onQuickLogTap: () -> Unit,
    onEditEndTime: () -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    nowMillis: () -> Long,
) {
    val ongoing = row.ongoingEvent
    val now by rememberTickingNow(clockNow = nowMillis)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                when {
                    row.runningCount >= 2 -> OngoingCountText(count = row.runningCount, voice = voice)
                    ongoing != null -> OngoingElapsedText(startedAt = ongoing.occurredAt, now = now, voice = voice)
                    else ->
                        Text(
                            text = voice.homeCaseCounts(row.todayCount, row.weekCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            }
            HomeCaseLogButton(row = row, voice = voice, onClick = onQuickLogTap)
        }
        if (ongoing != null && isStaleOngoing(ongoing, now)) {
            StaleOngoingBanner(
                caseName = row.name,
                elapsed = formatElapsedDuration(ongoing.occurredAt, now),
                voice = voice,
                onEditEndTime = onEditEndTime,
                onStillGoing = { onDismissStalePrompt(ongoing) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/** Soft Glow mockup's `.hrow` — alternates [IconHalo] tint primary/secondary per row via [isEvenRow]. */
@Composable
private fun BrightHomeCaseListItem(
    row: HomeCaseRow,
    voice: Voice,
    isEvenRow: Boolean,
    onClick: () -> Unit,
    onQuickLogTap: () -> Unit,
    onEditEndTime: () -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    nowMillis: () -> Long,
) {
    val ongoing = row.ongoingEvent
    val now by rememberTickingNow(clockNow = nowMillis)
    val tint = if (isEvenRow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val nameStyle =
        MaterialTheme.typography.titleMedium.copy(
            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
            fontWeight = FontWeight.Bold,
        )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        GlowCard(tint = tint, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconHalo(tint = tint) { Text(text = row.icon, style = MaterialTheme.typography.headlineSmall) }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.name, style = nameStyle)
                    when {
                        row.runningCount >= 2 -> OngoingCountText(count = row.runningCount, voice = voice)
                        ongoing != null -> OngoingElapsedText(startedAt = ongoing.occurredAt, now = now, voice = voice)
                        else ->
                            Text(
                                text = voice.homeCaseCounts(row.todayCount, row.weekCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                }
                HomeCaseLogButton(row = row, voice = voice, onClick = onQuickLogTap)
            }
        }
        if (ongoing != null && isStaleOngoing(ongoing, now)) {
            StaleOngoingBanner(
                caseName = row.name,
                elapsed = formatElapsedDuration(ongoing.occurredAt, now),
                voice = voice,
                onEditEndTime = onEditEndTime,
                onStillGoing = { onDismissStalePrompt(ongoing) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val previewRows =
    listOf(
        HomeCaseRow(
            caseId = 1,
            icon = "🏃",
            name = "Went for a run",
            todayCount = 1,
            weekCount = 4,
            logFlow = LogFlow.ONE_TAP,
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
        ),
        HomeCaseRow(
            caseId = 2,
            icon = "☕",
            name = "Coffee before noon",
            todayCount = 0,
            weekCount = 6,
            logFlow = LogFlow.ONE_TAP,
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
        ),
        HomeCaseRow(
            caseId = 3,
            icon = "🔊",
            name = "Noisy neighbours",
            todayCount = 2,
            weekCount = 5,
            logFlow = LogFlow.ONE_TAP,
            durationMode = DurationMode.START_STOP,
            intensityEnabled = false,
            ongoingEvent =
                EventEntity(caseId = 3, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L),
            runningCount = 2,
        ),
    )

@Composable
private fun HomeBrightRowsPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
        LocalVoice provides voiceFor(AppTheme.BRIGHT),
    ) {
        Column {
            previewRows.forEachIndexed { index, row ->
                HomeCaseListItem(
                    row = row,
                    voice = LocalVoice.current,
                    isEvenRow = index % 2 == 0,
                    onClick = {},
                    onQuickLogTap = {},
                    onEditEndTime = {},
                    onDismissStalePrompt = {},
                    nowMillis = { 0L },
                )
            }
        }
    }
}

@Composable
private fun HomePlainRowsPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
        LocalVoice provides voiceFor(AppTheme.PLAIN),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                previewRows.forEachIndexed { index, row ->
                    HomeCaseListItem(
                        row = row,
                        voice = LocalVoice.current,
                        isEvenRow = index % 2 == 0,
                        onClick = {},
                        onQuickLogTap = {},
                        onEditEndTime = {},
                        onDismissStalePrompt = {},
                        nowMillis = { 0L },
                    )
                }
            }
        }
    }
}

@Preview(name = "Home rows — Plain light", showBackground = true, widthDp = 380)
@Composable
private fun HomePlainRowsLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        HomePlainRowsPreviewContent()
    }
}

@Preview(name = "Home rows — Bright light", showBackground = true, widthDp = 380)
@Composable
private fun HomeBrightRowsLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        HomeBrightRowsPreviewContent()
    }
}

@Preview(name = "Home rows — Bright dark", showBackground = true, widthDp = 380)
@Composable
private fun HomeBrightRowsDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        HomeBrightRowsPreviewContent()
    }
}
