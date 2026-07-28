package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseWithEvents
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchivedCaseRow(
    val caseId: Long,
    val icon: String,
    val name: String,
    val eventCount: Int,
)

data class ArchivedCasesUiState(
    val cases: List<ArchivedCaseRow> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class ArchivedCasesViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val widgetRefresher: WidgetRefresher,
    ) : ViewModel() {
        val uiState: StateFlow<ArchivedCasesUiState> =
            repository
                .observeArchivedCasesWithEvents()
                .map { casesWithEvents ->
                    ArchivedCasesUiState(cases = archivedCaseRows(casesWithEvents), isLoading = false)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = ArchivedCasesUiState(),
                )

        fun unarchive(caseId: Long) {
            viewModelScope.launch {
                val case = repository.getCase(caseId) ?: return@launch
                val sortOrder = repository.observeActiveCases().first().size
                repository.updateCase(case.copy(archived = false, sortOrder = sortOrder))
                widgetRefresher.refreshListWidget()
            }
        }

        fun deleteForever(caseId: Long) {
            viewModelScope.launch {
                repository.getCase(caseId)?.let { repository.deleteCase(it) }
                widgetRefresher.refreshListWidget()
            }
        }
    }

/**
 * Pure mapping, split out so it's unit-testable on the JVM without a repository or Hilt,
 * same pattern as [homeCaseRows].
 */
internal fun archivedCaseRows(casesWithEvents: List<CaseWithEvents>): List<ArchivedCaseRow> =
    casesWithEvents.map { (case, events) ->
        ArchivedCaseRow(caseId = case.id, icon = case.icon, name = case.name, eventCount = events.size)
    }
