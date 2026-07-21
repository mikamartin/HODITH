package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseWithEvents
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** Non-null only for a `START_STOP` case with an open event (spec §6). */
    val ongoingEvent: EventEntity? = null,
)

data class HomeUiState(
    val cases: List<HomeCaseRow> = emptyList(),
    val archivedCount: Int = 0,
    val isLoading: Boolean = true,
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
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<HomeUiState> =
            combine(
                repository.observeActiveCasesWithEvents(),
                repository.observeArchivedCasesWithEvents().map { it.size },
            ) { casesWithEvents, archivedCount ->
                HomeUiState(
                    cases = homeCaseRows(casesWithEvents, clock.nowMillis()),
                    archivedCount = archivedCount,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = HomeUiState(),
            )

        private val _logSheet = MutableStateFlow<HomeLogSheetState?>(null)
        val logSheet: StateFlow<HomeLogSheetState?> = _logSheet.asStateFlow()

        private val _quickLogUndo = Channel<QuickLogUndo>(Channel.BUFFERED)
        val quickLogUndo: Flow<QuickLogUndo> = _quickLogUndo.receiveAsFlow()

        /**
         * Routes a row's quick-log tap. An already-ongoing `START_STOP` case always stops
         * immediately, regardless of `logFlow` — by the time you're stopping, whatever detail
         * was worth capturing was already captured at Start (or can be added via edit
         * afterward), so there's no reason to force a sheet open just to stop (spec §6).
         * Otherwise routes per the Case's `logFlow` as before.
         */
        fun onQuickLogTap(row: HomeCaseRow) {
            val ongoing = row.ongoingEvent
            when {
                ongoing != null -> stopEvent(ongoing)
                row.logFlow == LogFlow.ONE_TAP -> quickLogOneTap(row)
                row.logFlow == LogFlow.DETAIL_SHEET -> openLogSheet(row)
            }
        }

        fun stopEvent(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(endedAt = clock.nowMillis())) }
        }

        fun dismissStalePrompt(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(staleNudgeDismissedAt = clock.nowMillis())) }
        }

        fun nowMillis(): Long = clock.nowMillis()

        private fun quickLogOneTap(row: HomeCaseRow) {
            viewModelScope.launch {
                val now = clock.nowMillis()
                val eventId =
                    repository.insertEvent(
                        EventEntity(
                            caseId = row.caseId,
                            occurredAt = now,
                            endedAt = null,
                            intensity = null,
                            note = null,
                            loggedAt = now,
                        ),
                    )
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
 * Pure mapping, split out from [HomeViewModel] so the today/this-week boundary math is
 * unit-testable on the JVM without a repository or Hilt.
 */
internal fun homeCaseRows(
    casesWithEvents: List<CaseWithEvents>,
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
    return casesWithEvents.map { (case, events) ->
        HomeCaseRow(
            caseId = case.id,
            icon = case.icon,
            name = case.name,
            todayCount = events.count { it.occurredAt >= startOfToday },
            weekCount = events.count { it.occurredAt >= startOfWeek },
            logFlow = case.logFlow,
            durationMode = case.durationMode,
            intensityEnabled = case.intensityEnabled,
            ongoingEvent = ongoingEventIn(case, events),
        )
    }
}
