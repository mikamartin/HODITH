package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseWithEvents
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

/** One event as plotted on the Big Picture grid — icon-only per day, no intensity/duration encoding (spec §9). */
data class CalendarEvent(
    val caseId: Long,
    val occurredAt: Long,
    val note: String? = null,
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
                .observeActiveCasesWithEvents()
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
    casesWithEvents: List<CaseWithEvents>,
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
                events.map { CalendarEvent(caseId = case.id, occurredAt = it.occurredAt, note = it.note) }
            },
        earliestMonth = earliestMonth,
        currentMonth = currentMonth,
        today = today,
        isLoading = false,
    )
}
