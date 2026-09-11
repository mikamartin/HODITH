package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CaseEventSpan
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.quickLogEvent
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.activeSpanEnd
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HomeCaseRow(
    val caseId: Long,
    val icon: String,
    val name: String,
    val todayCount: Int,
    val weekCount: Int,
    val logFlow: LogFlow,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    /**
     * The earliest-started open event for a `START_STOP` case, or null. Drives the single
     * elapsed-time display; past one running event the row shows [runningCount] instead.
     */
    val ongoingEvent: EventEntity? = null,
    /** How many events are currently running on this Case (0 unless `START_STOP`). */
    val runningCount: Int = 0,
)

data class HomeUiState(
    val cases: List<HomeCaseRow> = emptyList(),
    val archivedCount: Int = 0,
    val isLoading: Boolean = true,
    /** Once true, Home shows a banner if the system notification permission is off (spec §11/§14). */
    val notificationPermissionRequested: Boolean = false,
)

/**
 * State for the shared [com.secondmonday.hodith.ui.logsheet.LogDetailSheet] when opened from a
 * Home row whose `logFlow` is `DETAIL_SHEET` (spec §14 — the sheet is reachable from Home, not
 * only Case Detail). Tag suggestions are fetched once when the sheet opens rather than observed
 * continuously — the sheet is a short-lived interaction, so a live-updating tag list isn't worth
 * the extra combined flow across every case on Home.
 */
data class HomeLogSheetState(
    val caseId: Long,
    val caseName: String,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val tagSuggestions: List<TagEntity>,
    val draft: LogDraft,
)

/** One-shot signal to show an Undo snackbar for a just-inserted one-tap event (spec §6). */
data class QuickLogUndo(
    val eventId: Long,
    val caseName: String,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        // Rows come from [homeCaseRowsFlow]'s three lean projections (shared with the widgets), not
        // the events-per-Case `@Relation` graph. The mapping runs on `Dispatchers.Default` so a
        // rapid-logging burst never touches the main thread; `clock::nowMillis` is read per emission,
        // so day-boundary rollover lands on the next emission or resubscribe.
        val uiState: StateFlow<HomeUiState> =
            combine(
                homeCaseRowsFlow(repository, clock::nowMillis),
                repository.observeArchivedCaseCount(),
                settingsRepository.observeHasRequestedNotificationPermission(),
            ) { cases, archivedCount, notificationPermissionRequested ->
                HomeUiState(
                    cases = cases,
                    archivedCount = archivedCount,
                    isLoading = false,
                    notificationPermissionRequested = notificationPermissionRequested,
                )
            }.flowOn(Dispatchers.Default)
                .conflate()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = HomeUiState(),
                )

        private val _logSheet = MutableStateFlow<HomeLogSheetState?>(null)
        val logSheet: StateFlow<HomeLogSheetState?> = _logSheet.asStateFlow()

        private val _quickLogUndo = Channel<QuickLogUndo>(Channel.BUFFERED)
        val quickLogUndo: Flow<QuickLogUndo> = _quickLogUndo.receiveAsFlow()

        /**
         * Routes a row's log-button tap per the Case's `logFlow`. The button stays a log/start
         * affordance even while an event runs on a `START_STOP` Case (spec §6) — tapping it then
         * starts a second concurrent event; stopping happens on the Case's own log rows, reached
         * by tapping the row.
         */
        fun onQuickLogTap(row: HomeCaseRow) {
            when (row.logFlow) {
                LogFlow.ONE_TAP -> quickLogOneTap(row)
                LogFlow.DETAIL_SHEET -> openLogSheet(row)
            }
        }

        fun nowMillis(): Long = clock.nowMillis()

        private fun quickLogOneTap(row: HomeCaseRow) {
            viewModelScope.launch {
                val eventId = repository.insertEvent(quickLogEvent(caseId = row.caseId, now = clock.nowMillis()))
                _quickLogUndo.send(QuickLogUndo(eventId = eventId, caseName = row.name))
            }
        }

        private fun openLogSheet(row: HomeCaseRow) {
            viewModelScope.launch {
                val tagSuggestions = repository.observeTagsForCase(row.caseId).first()
                _logSheet.value =
                    HomeLogSheetState(
                        caseId = row.caseId,
                        caseName = row.name,
                        durationMode = row.durationMode,
                        intensityEnabled = row.intensityEnabled,
                        tagSuggestions = tagSuggestions,
                        draft = draftFrom(event = null, now = clock.nowMillis()),
                    )
            }
        }

        fun dismissLogSheet() {
            _logSheet.value = null
        }

        fun saveLogSheetEvent(draft: LogDraft) {
            val sheet = _logSheet.value ?: return
            _logSheet.value = null
            viewModelScope.launch {
                val plan =
                    planSaveEvent(
                        caseId = sheet.caseId,
                        draft = draft,
                        existingEvent = null,
                        originalTags = emptyList(),
                        durationMode = sheet.durationMode,
                        now = clock.nowMillis(),
                    )
                val eventId = repository.insertEvent(plan.entity)
                plan.tagDiff.toAdd.forEach { repository.addTagToEvent(eventId, it) }
            }
        }

        fun undoQuickLog(eventId: Long) {
            viewModelScope.launch { repository.deleteEventById(eventId) }
        }
    }

/**
 * Every active Case's Home row, from three lean projections — the flat Case list for
 * identity/config, `EventDao.observeActiveCaseEventSpans` for the today/this-week counts, and
 * `observeOpenEvents` for the ongoing indicator — never the events-per-Case `@Relation` graph.
 * Shared by [HomeViewModel] and both Glance widgets (each filters the result to its own Cases).
 * Room re-emits all three on any `events` write, but each payload is small. [now] is read per
 * emission ([HomeViewModel] passes `clock::nowMillis`; a widget passes its once-captured value).
 */
internal fun homeCaseRowsFlow(
    repository: HodithRepository,
    now: () -> Long,
): Flow<List<HomeCaseRow>> =
    combine(
        repository.observeActiveCases(),
        repository.observeActiveCaseEventSpans(),
        repository.observeOpenEvents(),
    ) { cases, eventSpans, openEvents ->
        homeCaseRows(cases, eventSpans, openEvents, now())
    }

/**
 * Pure mapping, split out so the today/this-week boundary math is unit-testable on the JVM
 * without a repository or Hilt. [eventSpans] and [openEvents] span *every* active Case; both are
 * grouped by `caseId` here, and spans/opens for a Case not in [cases] are ignored (so a widget
 * can pass the full set and let [cases] narrow the output).
 */
internal fun homeCaseRows(
    cases: List<CaseEntity>,
    eventSpans: List<CaseEventSpan>,
    openEvents: List<EventEntity>,
    nowMillis: Long,
): List<HomeCaseRow> {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val startOfWeek =
        today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    val spansByCase = eventSpans.groupBy { it.caseId }
    val openEventsByCase = openEvents.groupBy { it.caseId }
    return cases.map { case ->
        val spans = spansByCase[case.id].orEmpty()
        val open = ongoingEventsIn(case, openEventsByCase[case.id].orEmpty())
        // Spec §9 active span: an event counts toward a window if its span reaches into it,
        // counted once whatever its length — so Home agrees with the calendar heatmap.
        // activeSpanEnd handles the NONE-collapses-to-a-point and running-runs-to-now cases.
        HomeCaseRow(
            caseId = case.id,
            icon = case.icon,
            name = case.name,
            todayCount = spans.count { activeSpanEnd(it.occurredAt, it.endedAt, it.durationMode, nowMillis) >= startOfToday },
            weekCount = spans.count { activeSpanEnd(it.occurredAt, it.endedAt, it.durationMode, nowMillis) >= startOfWeek },
            logFlow = case.logFlow,
            durationMode = case.durationMode,
            intensityEnabled = case.intensityEnabled,
            ongoingEvent = open.firstOrNull(),
            runningCount = open.size,
        )
    }
}
