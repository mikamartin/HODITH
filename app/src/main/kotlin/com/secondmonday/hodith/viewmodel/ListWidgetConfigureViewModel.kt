package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.HodithRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ListWidgetConfigureUiState {
    data object Loading : ListWidgetConfigureUiState

    /** At least one Case is already pinned — the widget already has something to show, so the
     * picker is skipped entirely rather than re-asked on every widget add. */
    data object AlreadyConfigured : ListWidgetConfigureUiState

    data class Picker(
        val cases: List<CaseEntity>,
        val selectedCaseIds: Set<Long>,
    ) : ListWidgetConfigureUiState
}

/**
 * Backs [com.secondmonday.hodith.widget.ListWidgetConfigureActivity], the widget's mandatory
 * `android:configure` step (spec §15). `pinned` is a Case-level flag, not per-widget-instance
 * data — every List widget instance shows the same pinned set — so this only ever needs to ask
 * once, the first time nothing is pinned yet.
 */
@HiltViewModel
class ListWidgetConfigureViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<ListWidgetConfigureUiState>(ListWidgetConfigureUiState.Loading)
        val uiState: StateFlow<ListWidgetConfigureUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val cases = repository.observeActiveCases().first()
                _uiState.value =
                    if (cases.any { it.pinned }) {
                        ListWidgetConfigureUiState.AlreadyConfigured
                    } else {
                        ListWidgetConfigureUiState.Picker(cases = cases, selectedCaseIds = emptySet())
                    }
            }
        }

        fun toggle(caseId: Long) {
            val current = _uiState.value
            if (current !is ListWidgetConfigureUiState.Picker) return
            val selected =
                if (caseId in current.selectedCaseIds) {
                    current.selectedCaseIds - caseId
                } else {
                    current.selectedCaseIds + caseId
                }
            _uiState.value = current.copy(selectedCaseIds = selected)
        }

        fun confirmSelection(onDone: () -> Unit) {
            val current = _uiState.value
            if (current !is ListWidgetConfigureUiState.Picker) {
                onDone()
                return
            }
            viewModelScope.launch {
                current.selectedCaseIds.forEach { caseId ->
                    val case = current.cases.find { it.id == caseId } ?: return@forEach
                    repository.updateCase(case.copy(pinned = true))
                }
                onDone()
            }
        }
    }
