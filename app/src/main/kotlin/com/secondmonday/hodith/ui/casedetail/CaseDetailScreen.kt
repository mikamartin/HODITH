package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.VerdictResult
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.common.OngoingElapsedText
import com.secondmonday.hodith.ui.common.StaleOngoingBanner
import com.secondmonday.hodith.ui.common.StopIconButton
import com.secondmonday.hodith.ui.common.rememberTickingNow
import com.secondmonday.hodith.ui.logsheet.LogDetailSheet
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.CaseDetailViewModel
import com.secondmonday.hodith.viewmodel.HunchHistoryEntry
import com.secondmonday.hodith.viewmodel.HunchTabState
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.draftFrom
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.formatExpectedFrequency
import com.secondmonday.hodith.viewmodel.formatRate
import com.secondmonday.hodith.viewmodel.hunchProgressFraction
import com.secondmonday.hodith.viewmodel.hunchTabState
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.isStaleOngoing
import com.secondmonday.hodith.viewmodel.monthsAgo
import com.secondmonday.hodith.viewmodel.ongoingEventIn

private const val LOG_TAB = 0
private const val INSIGHTS_TAB = 1
private const val HUNCH_TAB = 2

@Composable
fun CaseDetailRoute(
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    onOpenTriggers: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CaseDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEditCase = onEditCase,
        onOpenTriggers = onOpenTriggers,
        onDeleteEvent = viewModel::deleteEvent,
        newEventDraft = viewModel::newEventDraft,
        onSaveEvent = viewModel::saveEvent,
        onStopEvent = viewModel::stopEvent,
        onDismissStalePrompt = viewModel::dismissStalePrompt,
        nowMillis = viewModel::nowMillis,
        onAddHunch = viewModel::addHunch,
        onResolveHunch = viewModel::resolveHunch,
        onDismissHunchNudge = viewModel::dismissHunchNudge,
        modifier = modifier,
    )
}

private data class EditRequest(
    val event: EventEntity?,
    val originalTags: List<TagEntity>,
    val now: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    uiState: CaseDetailUiState,
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    onOpenTriggers: (Long) -> Unit,
    onDeleteEvent: (EventEntity) -> Unit,
    newEventDraft: () -> LogDraft,
    onSaveEvent: (LogDraft, EventEntity?, List<TagEntity>) -> Unit,
    onStopEvent: (EventEntity) -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    nowMillis: () -> Long,
    onAddHunch: (HunchDirection, Int, ExpectedPer) -> Unit,
    onResolveHunch: (HunchEntity) -> Unit,
    onDismissHunchNudge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val case = uiState.case
    val now by rememberTickingNow(clockNow = nowMillis)
    val ongoing = case?.let { ongoingEventIn(it, uiState.events.map { eventWithTags -> eventWithTags.event }) }
    var editRequest by remember { mutableStateOf<EditRequest?>(null) }
    var selectedTab by remember { mutableIntStateOf(LOG_TAB) }
    var showHunchCreationSheet by remember { mutableStateOf(false) }
    var frequencyGranularityOverride by remember { mutableStateOf<FrequencyGranularity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(case?.let { "${it.icon} ${it.name}" }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
                actions = {
                    if (case != null) {
                        IconButton(onClick = { onOpenTriggers(case.id) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = voice.triggersOpenDescription)
                        }
                        IconButton(onClick = { onEditCase(case.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = voice.caseDetailEditDescription)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedTab == LOG_TAB) {
                FloatingActionButton(
                    onClick = { editRequest = EditRequest(event = null, originalTags = emptyList(), now = now) },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = voice.retroLogEntryDescription)
                }
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == LOG_TAB, onClick = { selectedTab = LOG_TAB }, text = { Text(voice.caseDetailLogTabLabel) })
                Tab(
                    selected = selectedTab == INSIGHTS_TAB,
                    onClick = { selectedTab = INSIGHTS_TAB },
                    text = { Text(voice.caseDetailInsightsTabLabel) },
                )
                Tab(
                    selected = selectedTab == HUNCH_TAB,
                    onClick = { selectedTab = HUNCH_TAB },
                    text = { Text(voice.caseDetailHunchTabLabel) },
                )
            }
            when (selectedTab) {
                LOG_TAB ->
                    LogTabContent(
                        case = case,
                        ongoing = ongoing,
                        uiState = uiState,
                        now = now,
                        voice = voice,
                        onStopEvent = onStopEvent,
                        onDismissStalePrompt = onDismissStalePrompt,
                        onEventClick = { eventWithTags ->
                            editRequest = EditRequest(event = eventWithTags.event, originalTags = eventWithTags.tags, now = now)
                        },
                    )
                INSIGHTS_TAB ->
                    if (case != null) {
                        InsightsTabContent(
                            state =
                                insightsTabState(
                                    case,
                                    uiState.events,
                                    now,
                                    frequencyGranularityOverride = frequencyGranularityOverride,
                                ),
                            voice = voice,
                            frequencyGranularityOverride = frequencyGranularityOverride,
                            onFrequencyGranularityChange = { frequencyGranularityOverride = it },
                        )
                    }
                HUNCH_TAB ->
                    if (case != null) {
                        HunchTabContent(
                            case = case,
                            uiState = uiState,
                            now = now,
                            voice = voice,
                            onAddClick = { showHunchCreationSheet = true },
                            onDismissNudge = onDismissHunchNudge,
                            onResolveHunch = onResolveHunch,
                        )
                    }
            }
        }
    }

    val request = editRequest
    if (case != null && request != null) {
        LogDetailSheet(
            isEditing = request.event != null,
            durationMode = case.durationMode,
            intensityEnabled = case.intensityEnabled,
            initialDraft = request.event?.let { draftFrom(it, now = 0L, tags = request.originalTags) } ?: newEventDraft(),
            tagSuggestions = uiState.tagSuggestions,
            now = request.now,
            onSave = { draft ->
                onSaveEvent(draft, request.event, request.originalTags)
                editRequest = null
            },
            onDismiss = { editRequest = null },
            onDelete =
                request.event?.let { event ->
                    {
                        onDeleteEvent(event)
                        editRequest = null
                    }
                },
        )
    }

    if (showHunchCreationSheet) {
        HunchCreationSheet(
            voice = voice,
            onDismiss = { showHunchCreationSheet = false },
            onSave = { direction, expectedCount, expectedPer ->
                onAddHunch(direction, expectedCount, expectedPer)
                showHunchCreationSheet = false
            },
        )
    }
}

@Composable
private fun LogTabContent(
    case: CaseEntity?,
    ongoing: EventEntity?,
    uiState: CaseDetailUiState,
    now: Long,
    voice: Voice,
    onStopEvent: (EventEntity) -> Unit,
    onDismissStalePrompt: (EventEntity) -> Unit,
    onEventClick: (EventWithTags) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (case != null && uiState.events.isNotEmpty()) {
            Text(
                text =
                    voice.logSummaryLine(
                        eventCount = uiState.events.size,
                        observedDays = observationSpanDays(uiState.events.map { it.event }, case.createdAt, now),
                    ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (case != null && ongoing != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OngoingElapsedText(startedAt = ongoing.occurredAt, now = now, voice = voice, modifier = Modifier.weight(1f))
                StopIconButton(caseName = case.name, voice = voice, onClick = { onStopEvent(ongoing) })
            }
            if (isStaleOngoing(ongoing, now)) {
                StaleOngoingBanner(
                    caseName = case.name,
                    elapsed = formatElapsedDuration(ongoing.occurredAt, now),
                    voice = voice,
                    onEditEndTime = {
                        val originalTags =
                            uiState.events
                                .find { it.event.id == ongoing.id }
                                ?.tags
                                .orEmpty()
                        onEventClick(EventWithTags(ongoing, originalTags))
                    },
                    onStillGoing = { onDismissStalePrompt(ongoing) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Unit
                uiState.events.isEmpty() -> {
                    Text(text = voice.eventListEmptyState, modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.events, key = { it.event.id }) { eventWithTags ->
                            EventRow(
                                eventWithTags = eventWithTags,
                                now = now,
                                voice = voice,
                                durationMode = case?.durationMode ?: DurationMode.NONE,
                                onClick = { onEventClick(eventWithTags) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HunchTabContent(
    case: CaseEntity,
    uiState: CaseDetailUiState,
    now: Long,
    voice: Voice,
    onAddClick: () -> Unit,
    onDismissNudge: () -> Unit,
    onResolveHunch: (HunchEntity) -> Unit,
) {
    val events = uiState.events.map { it.event }
    val state = hunchTabState(case, uiState.activeHunch, events, uiState.hunchHistory, now)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            is HunchTabState.NoActiveHunch -> {
                if (state.showNudge) {
                    HunchNudgeCard(
                        caseIcon = case.icon,
                        caseName = case.name,
                        voice = voice,
                        onAdd = onAddClick,
                        onDismiss = onDismissNudge,
                    )
                } else {
                    HunchNoneCard(voice = voice, onAddClick = onAddClick)
                }
                if (state.history.isNotEmpty()) {
                    HunchHistoryCard(history = state.history, now = now, voice = voice)
                }
            }
            is HunchTabState.EarlyDays -> HunchEarlyCard(hunch = state.hunch, result = state.result, voice = voice)
            is HunchTabState.Verdict ->
                HunchVerdictCard(
                    hunch = state.hunch,
                    result = state.result,
                    voice = voice,
                    onResolve = onResolveHunch,
                )
        }
    }
}

/** Shared shell for every Hunch-tab card — full-width [Card] with a padded, vertically-spaced [Column]. */
@Composable
private fun HunchCard(
    spacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
private fun HunchNoneCard(
    voice: Voice,
    onAddClick: () -> Unit,
) {
    HunchCard {
        Text(voice.hunchTabNoneTitle, style = MaterialTheme.typography.titleMedium)
        Text(voice.hunchTabNoneBody, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAddClick) { Text(voice.hunchAddButtonLabel) }
    }
}

@Composable
private fun HunchNudgeCard(
    caseIcon: String,
    caseName: String,
    voice: Voice,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    HunchCard {
        Text(voice.hunchNudgeTitle, style = MaterialTheme.typography.titleMedium)
        Text(voice.hunchNudgeBody(caseIcon, caseName), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text(voice.hunchAddButtonLabel) }
            TextButton(onClick = onDismiss) { Text(voice.hunchNudgeDismissAction) }
        }
    }
}

@Composable
private fun HunchEarlyCard(
    hunch: HunchEntity,
    result: VerdictResult,
    voice: Voice,
) {
    HunchCard {
        Text(voice.hunchEarlyBadgeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            voice.hunchChipLabel(hunch.direction, formatExpectedFrequency(hunch.expectedCount, hunch.expectedPer)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(voice.hunchEarlyHeadline, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { hunchProgressFraction(result.eventCount, result.windowDays) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(voice.hunchProgressLabel(result.eventCount, result.windowDays), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HunchVerdictCard(
    hunch: HunchEntity,
    result: VerdictResult,
    voice: Voice,
    onResolve: (HunchEntity) -> Unit,
) {
    // Guaranteed non-null: hunchTabState only produces a Verdict once comparisonBand exists.
    val band = checkNotNull(result.comparisonBand) { "Verdict state must carry a resolved comparison band" }
    val observedRateLabel = formatRate(result.observedRate, hunch.expectedPer)

    HunchCard {
        Text(
            voice.hunchTierBadgeLabel(result.tier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            voice.hunchChipLabel(hunch.direction, formatExpectedFrequency(hunch.expectedCount, hunch.expectedPer)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(voice.verdictHeadline(hunch.direction, band, observedRateLabel), style = MaterialTheme.typography.titleMedium)
        Text(voice.verdictMeta(result.tier, result.eventCount, result.windowDays), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onResolve(hunch) }) { Text(voice.hunchResolveLabel) }
    }
}

@Composable
private fun HunchHistoryCard(
    history: List<HunchHistoryEntry>,
    now: Long,
    voice: Voice,
) {
    val heldUpCount = history.count { it.result.comparisonBand == ComparisonBand.ABOUT_RIGHT }
    HunchCard(spacing = 12.dp) {
        Text(voice.hunchHistoryHeader, style = MaterialTheme.typography.titleMedium)
        Text(voice.hunchHistorySummary(history.size, heldUpCount), style = MaterialTheme.typography.bodyMedium)
        history.forEach { entry -> HunchHistoryRow(entry = entry, now = now, voice = voice) }
    }
}

@Composable
private fun HunchHistoryRow(
    entry: HunchHistoryEntry,
    now: Long,
    voice: Voice,
) {
    val hunch = entry.hunch
    val resolvedAt = hunch.resolvedAt ?: return
    val frequencyLabel = formatExpectedFrequency(hunch.expectedCount, hunch.expectedPer)
    val observedRateLabel = formatRate(entry.result.observedRate, hunch.expectedPer)
    // Guaranteed non-null: hunchTabState only surfaces history entries with a resolved band.
    val band = checkNotNull(entry.result.comparisonBand) { "History entry must carry a resolved comparison band" }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                voice.hunchHistoryRowText(hunch.direction, frequencyLabel),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(voice.hunchHistoryRowWhen(monthsAgo(resolvedAt, now)), style = MaterialTheme.typography.bodySmall)
        }
        Text(voice.hunchHistoryRowOutcome(band, observedRateLabel), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventRow(
    eventWithTags: EventWithTags,
    now: Long,
    voice: Voice,
    durationMode: DurationMode,
    onClick: () -> Unit,
) {
    val event = eventWithTags.event
    val isOngoing = durationMode == DurationMode.START_STOP && event.endedAt == null

    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = formatEventTime(event.occurredAt, now), style = MaterialTheme.typography.bodyLarge)
        val details = eventDetailSummary(event, eventWithTags.tags, voice, isOngoing = isOngoing)
        if (details != null) {
            Text(text = details, style = MaterialTheme.typography.bodySmall)
        }
    }
}
