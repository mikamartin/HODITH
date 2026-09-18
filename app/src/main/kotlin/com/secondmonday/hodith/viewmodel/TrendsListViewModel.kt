package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.TrendFinding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrendsListUiState(
    val caseIcon: String = "",
    val caseName: String = "",
    val findings: List<TrendFinding> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Spec §10 Trends section's full-list screen — re-derives [TrendsListUiState.findings] from [insightsTabState] the same way [CaseDetailScreen][com.secondmonday.hodith.ui.casedetail.CaseDetailScreen] does for the compact card, rather than sharing that screen's own ViewModel instance (no destination in this app does; each full-screen route re-queries the repository on its own, see [TriggersViewModel]). */
@HiltViewModel
class TrendsListViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        val uiState: StateFlow<TrendsListUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
                repository.observeMostRecentLoggedAtAcrossActiveCases(),
            ) { case, events, mostRecentActivityAcrossCasesAt ->
                if (case == null) {
                    TrendsListUiState(isLoading = false)
                } else {
                    val state =
                        insightsTabState(
                            case,
                            events,
                            clock.nowMillis(),
                            mostRecentActivityAcrossCasesAt = mostRecentActivityAcrossCasesAt,
                        )
                    val findings = (state as? InsightsTabState.Ready)?.stats?.trends.orEmpty()
                    TrendsListUiState(caseIcon = case.icon, caseName = case.name, findings = findings, isLoading = false)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TrendsListUiState(),
            )
    }
