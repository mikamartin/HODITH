package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WidgetLogSheetUiState(
    val caseName: String,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val tagSuggestions: List<TagEntity>,
    val draft: LogDraft,
)

/**
 * Backs [com.secondmonday.hodith.widget.WidgetLogTrampolineActivity] — the widget's one-off
 * DETAIL_SHEET entry point (spec §15). Deliberately its own tiny ViewModel rather than reusing
 * [HomeViewModel] or [CaseDetailViewModel]: those own a whole screen's worth of state, while this
 * only ever needs to load one Case, save one Event, and refresh the widget afterward.
 */
@HiltViewModel
class WidgetLogSheetViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        private val widgetRefresher: WidgetRefresher,
    ) : ViewModel() {
        private var caseId: Long = 0L

        private val _uiState = MutableStateFlow<WidgetLogSheetUiState?>(null)
        val uiState: StateFlow<WidgetLogSheetUiState?> = _uiState.asStateFlow()

        fun nowMillis(): Long = clock.nowMillis()

        fun load(caseId: Long) {
            this.caseId = caseId
            viewModelScope.launch {
                val case = repository.getCase(caseId) ?: return@launch
                val tagSuggestions = repository.observeTagsForCase(caseId).first()
                _uiState.value =
                    WidgetLogSheetUiState(
                        caseName = case.name,
                        durationMode = case.durationMode,
                        intensityEnabled = case.intensityEnabled,
                        tagSuggestions = tagSuggestions,
                        draft = draftFrom(event = null, now = clock.nowMillis()),
                    )
            }
        }

        fun save(
            draft: LogDraft,
            onSaved: () -> Unit,
        ) {
            val durationMode = _uiState.value?.durationMode ?: return
            viewModelScope.launch {
                val plan =
                    planSaveEvent(
                        caseId = caseId,
                        draft = draft,
                        existingEvent = null,
                        originalTags = emptyList(),
                        durationMode = durationMode,
                        now = clock.nowMillis(),
                    )
                val eventId = repository.insertEvent(plan.entity)
                plan.tagDiff.toAdd.forEach { repository.addTagToEvent(eventId, it) }
                widgetRefresher.refreshWidgets()
                onSaved()
            }
        }
    }
