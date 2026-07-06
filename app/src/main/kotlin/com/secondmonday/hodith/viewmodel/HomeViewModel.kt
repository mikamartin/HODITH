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
)

data class HomeUiState(
    val cases: List<HomeCaseRow> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        repository: HodithRepository,
        clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<HomeUiState> =
            repository
                .observeActiveCasesWithEvents()
                .map { casesWithEvents ->
                    HomeUiState(
                        cases = homeCaseRows(casesWithEvents, clock.nowMillis()),
                        isLoading = false,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = HomeUiState(),
                )
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
        )
    }
}
