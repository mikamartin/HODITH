package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseWithEventsAndTags
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One case's icon/name as shown in the Big Picture grid and its filter chips (spec §9). */
data class CalendarCase(
    val id: Long,
    val icon: String,
    val name: String,
)

/**
 * One event as plotted on the Big Picture grid (spec §9). Intensity is not encoded. Duration is
 * encoded only for an event whose active span covers more than one calendar day: its icon then
 * appears on every covered day and the start day's icon is ringed. [endedAt] is null for a point
 * event and for a still-running one — [isOngoing] tells those apart, and a running event's span
 * runs to today.
 */
data class CalendarEvent(
    val id: Long,
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long? = null,
    val isOngoing: Boolean = false,
    val note: String? = null,
    val tags: List<String> = emptyList(),
)

data class BigPictureUiState(
    val cases: List<CalendarCase> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val earliestMonth: YearMonth? = null,
    val currentMonth: YearMonth? = null,
    val today: LocalDate? = null,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class BigPictureViewModel
    @Inject
    constructor(
        repository: HodithRepository,
        clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<BigPictureUiState> =
            repository
                .observeActiveCasesWithEventsAndTags()
                .map { casesWithEvents -> bigPictureUiState(casesWithEvents, clock.nowMillis()) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = BigPictureUiState(),
                )
    }

/**
 * Pure mapping, split out from [BigPictureViewModel] so it's unit-testable on the JVM without a
 * repository or Hilt. `earliestMonth` comes from the oldest active case's `createdAt` already
 * present in [casesWithEvents] — falls back to `currentMonth` when there are no cases at all.
 */
internal fun bigPictureUiState(
    casesWithEvents: List<CaseWithEventsAndTags>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): BigPictureUiState {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val currentMonth = YearMonth.from(today)
    val earliestMonth =
        casesWithEvents
            .minOfOrNull { it.case.createdAt }
            ?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()) }
            ?: currentMonth
    return BigPictureUiState(
        cases = casesWithEvents.map { CalendarCase(id = it.case.id, icon = it.case.icon, name = it.case.name) },
        events =
            casesWithEvents.flatMap { (case, events) ->
                events.map {
                    CalendarEvent(
                        id = it.event.id,
                        caseId = case.id,
                        occurredAt = it.event.occurredAt,
                        endedAt = it.event.endedAt,
                        isOngoing = case.durationMode == DurationMode.START_STOP && it.event.endedAt == null,
                        note = it.event.note,
                        tags = it.tags.map { tag -> tag.name },
                    )
                }
            },
        earliestMonth = earliestMonth,
        currentMonth = currentMonth,
        today = today,
        isLoading = false,
    )
}
