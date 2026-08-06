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

    data class Picker(
        val cases: List<CaseEntity>,
        val selectedCaseIds: Set<Long>,
    ) : ListWidgetConfigureUiState
}

/**
 * Backs [com.secondmonday.hodith.widget.ListWidgetConfigureActivity], the widget's mandatory
 * `android:configure` step (spec §15). Each List widget instance is bound to its own selected
 * Cases via the widget's own Glance state (per-instance, not a Case flag) — mirroring
 * [SingleCaseWidgetConfigureViewModel] — so the picker always shows and this ViewModel only
 * tracks the in-progress selection; the Activity owns writing the confirmed set into the widget's
 * own Glance state, since that's Context-bound infrastructure this ViewModel would otherwise need
 * to fake in tests.
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
                _uiState.value = ListWidgetConfigureUiState.Picker(cases = cases, selectedCaseIds = emptySet())
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

        fun confirmSelection(onDone: (Set<Long>) -> Unit) {
            val current = _uiState.value
            if (current !is ListWidgetConfigureUiState.Picker) return
            onDone(current.selectedCaseIds)
        }
    }
