package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.LogSortOrder
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.common.CenteredEmptyState
import com.secondmonday.hodith.ui.common.DateRangeFilterDialog
import com.secondmonday.hodith.ui.common.FabListBottomClearance
import com.secondmonday.hodith.ui.common.FilterTriggerChip
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.common.OngoingCountText
import com.secondmonday.hodith.ui.common.OngoingElapsedText
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.StopIconButton
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.common.rememberTickingNow
import com.secondmonday.hodith.ui.logsheet.LogDetailSheet
import com.secondmonday.hodith.ui.share.ShareChoice
import com.secondmonday.hodith.ui.share.ShareChooserDialog
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.CaseDetailViewModel
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatDateRangeBound
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.ongoingEventsIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val LOG_TAB = 0
private const val INSIGHTS_TAB = 1

// Deliberately not colorScheme.outlineVariant: that hue shifts per theme (blue on Plain, warm on
// Bright), which read as inconsistent. A single fixed neutral gray reads the same everywhere.
private val CaseDescriptionBorderColor = Color(0x66828282)

@Composable
fun CaseDetailRoute(
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    onEditEvent: (caseId: Long, eventId: Long) -> Unit,
    onOpenTriggers: (Long) -> Unit,
    onOpenShare: (Long) -> Unit,
    onOpenLogShare: (Long) -> Unit,
    onOpenTrends: (Long) -> Unit,
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
        onOpenLogShare = onOpenLogShare,
        onOpenTrends = onOpenTrends,
        newEventDraft = viewModel::newEventDraft,
        onSaveEvent = viewModel::saveNewEvent,
        onStopEvent = viewModel::stopEvent,
        nowMillis = viewModel::nowMillis,
        onLogSortOrderChange = viewModel::setLogSortOrder,
        onLogDateFromChange = viewModel::setLogDateFrom,
        onLogDateToChange = viewModel::setLogDateTo,
        onLogFieldVisibleChange = viewModel::setLogFieldVisible,
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
    onOpenLogShare: (Long) -> Unit,
    onOpenTrends: (Long) -> Unit,
    newEventDraft: () -> LogDraft,
    onSaveEvent: (LogDraft) -> Unit,
    onStopEvent: (EventEntity) -> Unit,
    nowMillis: () -> Long,
    onLogSortOrderChange: (LogSortOrder) -> Unit,
    onLogDateFromChange: (LocalDate?) -> Unit,
    onLogDateToChange: (LocalDate?) -> Unit,
    onLogFieldVisibleChange: (LogRowField, Boolean) -> Unit,
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
    var showShareChooser by remember { mutableStateOf(false) }
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
                        IconButton(onClick = { showShareChooser = true }) {
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
            if (case?.description != null) {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, CaseDescriptionBorderColor),
                    ) {
                        Text(
                            text = case.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == LOG_TAB, onClick = { selectedTab = LOG_TAB }, text = { Text(voice.caseDetailLogTabLabel) })
                Tab(
                    selected = selectedTab == INSIGHTS_TAB,
                    onClick = { selectedTab = INSIGHTS_TAB },
                    text = { Text(voice.caseDetailInsightsTabLabel) },
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
                        dateFrom = uiState.logDateFrom,
                        dateTo = uiState.logDateTo,
                        onDateFromChange = onLogDateFromChange,
                        onDateToChange = onLogDateToChange,
                        visibleFields = uiState.logVisibleFields,
                        onFieldVisibleChange = onLogFieldVisibleChange,
                        onShowMore = onShowMoreLogEvents,
                        onStopEvent = onStopEvent,
                        onEditEvent = { event -> case?.let { onEditEvent(it.id, event.id) } },
                    )
                INSIGHTS_TAB ->
                    if (case != null) {
                        // Every computation inside insightsTabState that reads "now" only cares about
                        // the calendar day (CalendarMath.kt's daysBetween), so keying on the day rather
                        // than the raw, minute-ticking `now` (Ticker.kt) skips identical recomputation
                        // on every tick -- worth doing now that Story C T4's tag-outcome detector makes
                        // that recomputation a real (permutation-test) cost, not just a cheap no-op.
                        val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
                        // Derived state — memoize so the many-pass aggregation recomputes only on a
                        // real input change, not on every unrelated recomposition of this screen.
                        val insightsState =
                            remember(case, uiState.events, today, frequencyGranularityOverride, uiState.mostRecentActivityAcrossCasesAt) {
                                insightsTabState(
                                    case,
                                    uiState.events,
                                    now,
                                    frequencyGranularityOverride = frequencyGranularityOverride,
                                    mostRecentActivityAcrossCasesAt = uiState.mostRecentActivityAcrossCasesAt,
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
                            onOpenTrends = { onOpenTrends(case.id) },
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

    if (showShareChooser && case != null) {
        ShareChooserDialog(
            voice = voice,
            onDismiss = { showShareChooser = false },
            onConfirm = { choice ->
                showShareChooser = false
                when (choice) {
                    ShareChoice.INSIGHT -> onOpenShare(case.id)
                    ShareChoice.LOG -> onOpenLogShare(case.id)
                }
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
    dateFrom: Long?,
    dateTo: Long?,
    onDateFromChange: (LocalDate?) -> Unit,
    onDateToChange: (LocalDate?) -> Unit,
    visibleFields: Set<LogRowField>,
    onFieldVisibleChange: (LogRowField, Boolean) -> Unit,
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
        if (case != null && uiState.events.isNotEmpty()) {
            LogFilterRow(
                case = case,
                now = now,
                voice = voice,
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
                dateFrom = dateFrom,
                dateTo = dateTo,
                onDateFromChange = onDateFromChange,
                onDateToChange = onDateToChange,
                visibleFields = visibleFields,
                onFieldVisibleChange = onFieldVisibleChange,
            )
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
                uiState.logEvents.isEmpty() -> {
                    CenteredEmptyState(voice.shareLogEmptyRangeMessage)
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
                                visibleFields = visibleFields,
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

/**
 * The Log tab's filter-chip row (Sort, Range, plus the pinned field-visibility Edit icon) and the
 * dialogs its chips/icon open — split out from [LogTabContent] the same way Big Picture's own
 * `FilterSummaryRow` (chip row + dialogs) sits apart from the grid it filters. A narrowed Range
 * collapses to [Voice.shareLogRangeSelectedLabel] in the chip itself (the actual bounds, once
 * formatted, don't fit the chip's own width) with the real dates spelled out underneath instead.
 */
@Composable
private fun LogFilterRow(
    case: CaseEntity,
    now: Long,
    voice: Voice,
    sortOrder: LogSortOrder,
    onSortOrderChange: (LogSortOrder) -> Unit,
    dateFrom: Long?,
    dateTo: Long?,
    onDateFromChange: (LocalDate?) -> Unit,
    onDateToChange: (LocalDate?) -> Unit,
    visibleFields: Set<LogRowField>,
    onFieldVisibleChange: (LogRowField, Boolean) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var showSortDialog by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var showFieldsDialog by remember { mutableStateOf(false) }
    val isRangeFiltered = dateFrom != null || dateTo != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Start/end sort is only meaningful when the Case tracks duration (spec §6) — a
        // `NONE` Case's `endedAt` is never shown, so "Ended" would order by an invisible field.
        if (case.durationMode.tracksDuration) {
            FilterTriggerChip(
                label = voice.logSortLabel,
                count = if (sortOrder == LogSortOrder.BY_START) voice.logSortByStartLabel else voice.logSortByEndLabel,
                onClick = { showSortDialog = true },
                isFiltered = sortOrder != LogSortOrder.BY_START,
            )
        }
        FilterTriggerChip(
            label = voice.shareLogRangeLabel,
            count = if (isRangeFiltered) voice.shareLogRangeSelectedLabel else voice.shareLogRangeAllTimeLabel,
            onClick = { showRangePicker = true },
            isFiltered = isRangeFiltered,
        )
        Spacer(modifier = Modifier.weight(1f))
        // Edit which fields each row shows (spec §6) — pinned right, same placement as
        // Big Picture's own detail-edit icon.
        IconButton(onClick = { showFieldsDialog = true }) {
            Icon(Icons.Filled.Edit, contentDescription = voice.logDetailEditDescription)
        }
    }
    if (isRangeFiltered) {
        Text(
            text =
                voice.shareLogRangeNote(
                    dateFrom?.let { formatDateRangeBound(it, now, zone) },
                    dateTo?.let { formatDateRangeBound(it, now, zone) },
                ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }

    if (showSortDialog) {
        InfoDialog(title = voice.logSortLabel, onDismiss = { showSortDialog = false }) {
            SegmentedChoiceRow(
                options =
                    listOf(
                        LogSortOrder.BY_START to voice.logSortByStartLabel,
                        LogSortOrder.BY_END to voice.logSortByEndLabel,
                    ),
                selected = sortOrder,
                onSelect = {
                    onSortOrderChange(it)
                    showSortDialog = false
                },
            )
        }
    }
    if (showRangePicker) {
        DateRangeFilterDialog(
            dateFrom = dateFrom,
            dateTo = dateTo,
            isRangeFiltered = isRangeFiltered,
            now = now,
            zone = zone,
            voice = voice,
            onDismiss = { showRangePicker = false },
            onConfirm = { from, to ->
                onDateFromChange(from)
                onDateToChange(to)
                showRangePicker = false
            },
        )
    }
    if (showFieldsDialog) {
        InfoDialog(title = voice.logDetailDialogTitle, onDismiss = { showFieldsDialog = false }) {
            Column {
                availableLogRowFields(case).forEach { field ->
                    ToggleRow(
                        label = logRowFieldLabel(field, voice),
                        checked = field in visibleFields,
                        onCheckedChange = { onFieldVisibleChange(field, it) },
                        modifier =
                            Modifier
                                .testTag(LOG_DETAIL_FIELD_TOGGLE_TAG_PREFIX + field.name)
                                .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Prefix for each field-toggle row's `testTag` in the Log tab's field-visibility dialog — clicking
 * by tag rather than by label text, same as Big Picture's own `BIG_PICTURE_DETAIL_TOGGLE_TAG_PREFIX`,
 * since the label/Switch semantics don't reliably merge into one clickable node in every context.
 */
internal const val LOG_DETAIL_FIELD_TOGGLE_TAG_PREFIX = "log_detail_field_toggle_"

/** Notes/Tags always offered; Duration/Intensity only when the Case tracks them — the Log tab's own gating, matching Log Share's `availableLogRowFields`. */
private fun availableLogRowFields(case: CaseEntity): List<LogRowField> =
    buildList {
        add(LogRowField.NOTES)
        add(LogRowField.TAGS)
        if (case.durationMode.tracksDuration) add(LogRowField.DURATION)
        if (case.intensityEnabled) add(LogRowField.INTENSITY)
    }

private fun logRowFieldLabel(
    field: LogRowField,
    voice: Voice,
): String =
    when (field) {
        LogRowField.NOTES -> voice.shareLogFieldNotesLabel
        LogRowField.TAGS -> voice.shareLogFieldTagsLabel
        LogRowField.DURATION -> voice.insightsSectionLabelDuration
        LogRowField.INTENSITY -> voice.insightsSectionLabelIntensity
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
    visibleFields: Set<LogRowField>,
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
                    visibleFields,
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
                visibleFields,
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
    visibleFields: Set<LogRowField>,
    onStopEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = eventWithTags.event
    val isOngoing = durationMode == DurationMode.START_STOP && event.endedAt == null

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatEventTime(event.occurredAt, now, LocalTimeFormat.current.is24Hour, zone = event.loggedZone()),
                style = MaterialTheme.typography.bodyLarge,
            )
            // A running event carries its own live elapsed time and Stop, so each of a Case's
            // open events is legible and stoppable on its own (spec §6).
            if (isOngoing) {
                OngoingElapsedText(startedAt = event.occurredAt, now = now, voice = voice)
            }
            val details =
                eventDetailSummary(
                    occurredAt = event.occurredAt,
                    endedAt = event.endedAt,
                    intensity = event.intensity,
                    note = event.note.takeIf { LogRowField.NOTES in visibleFields },
                    tagNames = if (LogRowField.TAGS in visibleFields) eventWithTags.tags.map { it.name } else emptyList(),
                    voice = voice,
                    isOngoing = isOngoing,
                    tracksDuration = durationMode.tracksDuration && LogRowField.DURATION in visibleFields,
                    showIntensity = LogRowField.INTENSITY in visibleFields,
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
