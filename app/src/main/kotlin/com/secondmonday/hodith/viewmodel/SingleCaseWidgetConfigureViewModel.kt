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

sealed interface SingleCaseWidgetConfigureUiState {
    data object Loading : SingleCaseWidgetConfigureUiState

    data class Picker(
        val cases: List<CaseEntity>,
        val selectedCaseId: Long?,
    ) : SingleCaseWidgetConfigureUiState
}

/**
 * Backs [com.secondmonday.hodith.widget.SingleCaseWidgetConfigureActivity], the Single-case
 * widget's mandatory `android:configure` step (spec §15). Unlike [ListWidgetConfigureViewModel],
 * which skips straight past the picker once anything is pinned (a Case-level flag shared by every
 * List widget instance), each Single-case widget instance is bound to its own Case — there's
 * nothing to skip, so the picker always shows. This ViewModel only tracks the in-progress
 * selection; the Activity owns writing the confirmed Case id into the widget's own Glance state,
 * since that's Context-bound infrastructure this ViewModel would otherwise need to fake in tests.
 */
@HiltViewModel
class SingleCaseWidgetConfigureViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SingleCaseWidgetConfigureUiState>(SingleCaseWidgetConfigureUiState.Loading)
        val uiState: StateFlow<SingleCaseWidgetConfigureUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val cases = repository.observeActiveCases().first()
                _uiState.value = SingleCaseWidgetConfigureUiState.Picker(cases = cases, selectedCaseId = null)
            }
        }

        fun select(caseId: Long) {
            val current = _uiState.value
            if (current !is SingleCaseWidgetConfigureUiState.Picker) return
            _uiState.value = current.copy(selectedCaseId = caseId)
        }

        fun confirmSelection(onDone: (Long) -> Unit) {
            val current = _uiState.value
            if (current !is SingleCaseWidgetConfigureUiState.Picker) return
            val caseId = current.selectedCaseId ?: return
            onDone(caseId)
        }
    }
