package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
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
import com.secondmonday.hodith.data.LogSortOrder
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.VerdictResult
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.common.CenteredEmptyState
import com.secondmonday.hodith.ui.common.FabListBottomClearance
import com.secondmonday.hodith.ui.common.OngoingCountText
import com.secondmonday.hodith.ui.common.OngoingElapsedText
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.StopIconButton
import com.secondmonday.hodith.ui.common.rememberTickingNow
import com.secondmonday.hodith.ui.logsheet.LogDetailSheet
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.CaseDetailViewModel
import com.secondmonday.hodith.viewmodel.HunchHistoryEntry
import com.secondmonday.hodith.viewmodel.HunchTabState
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.formatExpectedFrequency
import com.secondmonday.hodith.viewmodel.formatRate
import com.secondmonday.hodith.viewmodel.hunchProgressFraction
import com.secondmonday.hodith.viewmodel.hunchTabState
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.ongoingEventsIn

private const val LOG_TAB = 0
private const val INSIGHTS_TAB = 1
private const val HUNCH_TAB = 2

@Composable
fun CaseDetailRoute(
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    onEditEvent: (caseId: Long, eventId: Long) -> Unit,
    onOpenTriggers: (Long) -> Unit,
    onOpenShare: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CaseDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEditCase = onEditCase,
        onEditEvent = onEditEvent,
        onOpenTriggers = onOpenTriggers,
        onOpenShare = onOpenShare,
        newEventDraft = viewModel::newEventDraft,
        onSaveEvent = viewModel::saveNewEvent,
        onStopEvent = viewModel::stopEvent,
        nowMillis = viewModel::nowMillis,
        onAddHunch = viewModel::addHunch,
        onResolveHunch = viewModel::resolveHunch,
        onLogSortOrderChange = viewModel::setLogSortOrder,
        onShowMoreLogEvents = viewModel::loadMoreLogEvents,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    uiState: CaseDetailUiState,
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    onEditEvent: (caseId: Long, eventId: Long) -> Unit,
    onOpenTriggers: (Long) -> Unit,
    onOpenShare: (Long) -> Unit,
    newEventDraft: () -> LogDraft,
    onSaveEvent: (LogDraft) -> Unit,
    onStopEvent: (EventEntity) -> Unit,
    nowMillis: () -> Long,
    onAddHunch: (HunchDirection, Int, ExpectedPer, VerdictMetric, ObservationWindow, Long?) -> Unit,
    onResolveHunch: (HunchEntity) -> Unit,
    onLogSortOrderChange: (LogSortOrder) -> Unit,
    onShowMoreLogEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val case = uiState.case
    val now by rememberTickingNow(clockNow = nowMillis)
    val ongoingEvents =
        case?.let { ongoingEventsIn(it, uiState.events.map { eventWithTags -> eventWithTags.event }) }.orEmpty()
    // Non-null while the new-event log sheet is open, holding the `now` captured when it opened.
    // Editing an existing event is a separate destination (onEditEvent), not this sheet.
    var newEventSheetNow by remember { mutableStateOf<Long?>(null) }
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
                        IconButton(onClick = { onOpenShare(case.id) }) {
                            Icon(Icons.Filled.Share, contentDescription = voice.shareOpenDescription)
                        }
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
                    onClick = { newEventSheetNow = now },
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
                        ongoingEvents = ongoingEvents,
                        uiState = uiState,
                        now = now,
                        voice = voice,
                        sortOrder = uiState.logSortOrder,
                        onSortOrderChange = onLogSortOrderChange,
                        onShowMore = onShowMoreLogEvents,
                        onStopEvent = onStopEvent,
                        onEditEvent = { event -> case?.let { onEditEvent(it.id, event.id) } },
                    )
                INSIGHTS_TAB ->
                    if (case != null) {
                        // Derived state — memoize so the many-pass aggregation recomputes only on a
                        // real input change, not on every unrelated recomposition of this screen.
                        val insightsState =
                            remember(case, uiState.events, now, frequencyGranularityOverride) {
                                insightsTabState(
                                    case,
                                    uiState.events,
                                    now,
                                    frequencyGranularityOverride = frequencyGranularityOverride,
                                )
                            }
                        InsightsTabContent(
                            state = insightsState,
                            case = case,
                            events = uiState.events,
                            now = now,
                            voice = voice,
                            frequencyGranularityOverride = frequencyGranularityOverride,
                            onFrequencyGranularityChange = { frequencyGranularityOverride = it },
                            onEditEvent = { event -> onEditEvent(case.id, event.id) },
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
                            onResolveHunch = onResolveHunch,
                        )
                    }
            }
        }
    }

    val sheetNow = newEventSheetNow
    if (case != null && sheetNow != null) {
        LogDetailSheet(
            durationMode = case.durationMode,
            intensityEnabled = case.intensityEnabled,
            initialDraft = newEventDraft(),
            tagSuggestions = uiState.tagSuggestions,
            now = sheetNow,
            onSave = { draft ->
                onSaveEvent(draft)
                newEventSheetNow = null
            },
            onDismiss = { newEventSheetNow = null },
        )
    }

    if (showHunchCreationSheet && case != null) {
        HunchCreationSheet(
            voice = voice,
            durationMode = case.durationMode,
            caseCreatedAt = case.createdAt,
            onDismiss = { showHunchCreationSheet = false },
            onSave = { direction, expectedCount, expectedPer, metric, observationWindow, windowStartDate ->
                onAddHunch(direction, expectedCount, expectedPer, metric, observationWindow, windowStartDate)
                showHunchCreationSheet = false
            },
        )
    }
}

@Composable
private fun LogTabContent(
    case: CaseEntity?,
    ongoingEvents: List<EventEntity>,
    uiState: CaseDetailUiState,
    now: Long,
    voice: Voice,
    sortOrder: LogSortOrder,
    onSortOrderChange: (LogSortOrder) -> Unit,
    onShowMore: () -> Unit,
    onStopEvent: (EventEntity) -> Unit,
    onEditEvent: (EventEntity) -> Unit,
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
        // Start/end sort is only meaningful when the Case tracks duration (spec §6) — a `NONE`
        // Case's `endedAt` is never shown, so "Ended" would order by an invisible field.
        if (case != null && case.durationMode.tracksDuration && uiState.events.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(voice.logSortLabel, style = MaterialTheme.typography.labelLarge)
                SegmentedChoiceRow(
                    options =
                        listOf(
                            LogSortOrder.BY_START to voice.logSortByStartLabel,
                            LogSortOrder.BY_END to voice.logSortByEndLabel,
                        ),
                    selected = sortOrder,
                    onSelect = onSortOrderChange,
                    modifier = Modifier,
                    stretchToFill = false,
                )
            }
        }
        if (case != null && ongoingEvents.isNotEmpty()) {
            // The header always reads as a count (spec §6) — even for one event — so it looks the
            // same regardless of how many run. Each open event's own elapsed time and its own Stop
            // button live on its log row below.
            OngoingCountText(
                count = ongoingEvents.size,
                voice = voice,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Unit
                uiState.events.isEmpty() -> {
                    CenteredEmptyState(voice.eventListEmptyState)
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = FabListBottomClearance),
                    ) {
                        items(uiState.logEvents, key = { it.event.id }) { eventWithTags ->
                            EventRow(
                                eventWithTags = eventWithTags,
                                caseName = case?.name.orEmpty(),
                                now = now,
                                voice = voice,
                                durationMode = case?.durationMode ?: DurationMode.NONE,
                                onClick = { onEditEvent(eventWithTags.event) },
                                onStopEvent = onStopEvent,
                            )
                        }
                        if (uiState.logHasMore) {
                            item(key = "log_show_more") {
                                TextButton(
                                    onClick = onShowMore,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                ) {
                                    Text(voice.logShowMoreAction)
                                }
                            }
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
    onResolveHunch: (HunchEntity) -> Unit,
) {
    val events = remember(uiState.events) { uiState.events.map { it.event } }
    // Derived state, not a cheap read — memoize so it recomputes only on a real input change, not
    // on every unrelated recomposition of CaseDetailScreen (spec §7). `now` stays a key: a
    // resolved-Hunch verdict is frozen at `resolvedAt`, but an active Hunch's window ends at `now`,
    // so the tick still has to flow through.
    val state =
        remember(case, uiState.activeHunch, events, uiState.hunchHistory, now) {
            hunchTabState(case, uiState.activeHunch, events, uiState.hunchHistory, now)
        }

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
                        eventCount = events.size,
                        voice = voice,
                        onAdd = onAddClick,
                    )
                } else {
                    HunchNoneCard(voice = voice, onAddClick = onAddClick)
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
        if (state.history.isNotEmpty()) {
            HunchHistoryCard(history = state.history, voice = voice)
        }
    }
}

/**
 * Shared shell for every Hunch-tab card — full-width [Card] with a padded, vertically-spaced
 * [Column]. [containerColor] defaults to the live-card surface; [HunchHistoryCard] overrides it
 * to sit on a visibly quieter tone.
 */
@Composable
private fun HunchCard(
    spacing: Dp = 8.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
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
        Text(
            voice.hunchTabNoneDataNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddClick) { Text(voice.hunchAddButtonLabel) }
    }
}

@Composable
private fun HunchNudgeCard(
    caseIcon: String,
    caseName: String,
    eventCount: Int,
    voice: Voice,
    onAdd: () -> Unit,
) {
    HunchCard {
        Text(voice.hunchNudgeTitle, style = MaterialTheme.typography.titleMedium)
        Text(voice.hunchNudgeBody(caseIcon, caseName, eventCount), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAdd) { Text(voice.hunchAddButtonLabel) }
    }
}

@Composable
private fun HunchEarlyCard(
    hunch: HunchEntity,
    result: VerdictResult,
    voice: Voice,
) {
    val daysActive = hunch.metric == VerdictMetric.DAYS_ACTIVE
    val observationCount = if (daysActive) result.activeDayCount else result.eventCount
    val progressUnit = if (daysActive) voice.hunchProgressUnitDaysActive else voice.hunchProgressUnitEvents
    HunchCard {
        Text(voice.hunchEarlyBadgeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            voice.hunchChipLabel(hunch.direction, formatExpectedFrequency(hunch.expectedCount, hunch.expectedPer, hunch.metric)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(voice.hunchEarlyHeadline, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { hunchProgressFraction(observationCount, result.windowDays) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            voice.hunchProgressLabel(observationCount, progressUnit, result.windowDays),
            style = MaterialTheme.typography.bodySmall,
        )
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
    val daysActive = hunch.metric == VerdictMetric.DAYS_ACTIVE
    val observedRateLabel = formatRate(result.observedRate, hunch.expectedPer, hunch.metric)

    HunchCard {
        Text(
            voice.hunchTierBadgeLabel(result.tier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            voice.hunchChipLabel(hunch.direction, formatExpectedFrequency(hunch.expectedCount, hunch.expectedPer, hunch.metric)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (daysActive) {
                voice.verdictHeadlineDaysActive(hunch.direction, band, observedRateLabel)
            } else {
                voice.verdictHeadline(hunch.direction, band, observedRateLabel)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (daysActive) {
                voice.verdictMetaDaysActive(result.tier, result.activeDayCount, result.windowDays)
            } else {
                voice.verdictMeta(result.tier, result.eventCount, result.windowDays)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { onResolve(hunch) }) { Text(voice.hunchResolveLabel) }
    }
}

/**
 * Sits on [MaterialTheme.colorScheme.surfaceVariant] rather than [HunchCard]'s default surface —
 * a quieter, visibly different tone from the live active-Hunch card above it, so a closed record
 * doesn't read as more of the same live content. Shown below the active Hunch card whenever one
 * exists, not only when [HunchTabState.NoActiveHunch] — the record of past Hunches never
 * disappears just because a new one is running.
 */
@Composable
private fun HunchHistoryCard(
    history: List<HunchHistoryEntry>,
    voice: Voice,
) {
    val heldUpCount = history.count { it.result.comparisonBand == ComparisonBand.ABOUT_RIGHT }
    HunchCard(spacing = 10.dp, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(voice.hunchHistoryHeader, style = MaterialTheme.typography.titleMedium)
        Text(voice.hunchHistorySummary(history.size, heldUpCount), style = MaterialTheme.typography.bodyMedium)
        history.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HunchHistoryRow(entry = entry, voice = voice)
        }
    }
}

/** One resolved Hunch, leading with its made→resolved stamp rather than burying the date. */
@Composable
private fun HunchHistoryRow(
    entry: HunchHistoryEntry,
    voice: Voice,
) {
    val hunch = entry.hunch
    val resolvedAt = hunch.resolvedAt ?: return
    val frequencyLabel = formatExpectedFrequency(hunch.expectedCount, hunch.expectedPer, hunch.metric)
    val observedRateLabel = formatRate(entry.result.observedRate, hunch.expectedPer, hunch.metric)
    // Guaranteed non-null: hunchTabState only surfaces history entries with a resolved band.
    val band = checkNotNull(entry.result.comparisonBand) { "History entry must carry a resolved comparison band" }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            voice.hunchHistoryRowStamp(formatEventDate(hunch.createdAt), formatEventDate(resolvedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(voice.hunchHistoryRowText(hunch.direction, frequencyLabel), style = MaterialTheme.typography.bodyMedium)
        Text(voice.hunchHistoryRowOutcome(band, observedRateLabel), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Plain wraps [EventRowContent] in a white plank card on the tinted screen background;
 * Intense and Bright keep today's flat row.
 */
@Composable
private fun EventRow(
    eventWithTags: EventWithTags,
    caseName: String,
    now: Long,
    voice: Voice,
    durationMode: DurationMode,
    onClick: () -> Unit,
    onStopEvent: (EventEntity) -> Unit,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.PLAIN ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                EventRowContent(
                    eventWithTags,
                    caseName,
                    now,
                    voice,
                    durationMode,
                    onStopEvent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        CardDecorationStyle.INTENSE, CardDecorationStyle.BRIGHT ->
            EventRowContent(
                eventWithTags,
                caseName,
                now,
                voice,
                durationMode,
                onStopEvent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
    }
}

@Composable
private fun EventRowContent(
    eventWithTags: EventWithTags,
    caseName: String,
    now: Long,
    voice: Voice,
    durationMode: DurationMode,
    onStopEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = eventWithTags.event
    val isOngoing = durationMode == DurationMode.START_STOP && event.endedAt == null

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatEventTime(event.occurredAt, now, LocalTimeFormat.current.is24Hour),
                style = MaterialTheme.typography.bodyLarge,
            )
            // A running event carries its own live elapsed time and Stop, so each of a Case's
            // open events is legible and stoppable on its own (spec §6).
            if (isOngoing) {
                OngoingElapsedText(startedAt = event.occurredAt, now = now, voice = voice)
            }
            val details =
                eventDetailSummary(
                    event,
                    eventWithTags.tags,
                    voice,
                    isOngoing = isOngoing,
                    tracksDuration = durationMode.tracksDuration,
                )
            if (details != null) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (isOngoing) {
            StopIconButton(caseName = caseName, voice = voice, onClick = { onStopEvent(event) })
        }
    }
}
