package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [com.secondmonday.hodith.ui.logsheet.LogDetailScreen] — the full-screen editor for an
 * *existing* event. Unlike the new-event [com.secondmonday.hodith.ui.logsheet.LogDetailSheet]
 * (which has no back-stack entry and borrows each caller's ViewModel, see [LogDraft]'s doc), the
 * edit screen is its own destination, so it owns a real ViewModel here — keyed by `caseId` +
 * `eventId` nav args, mirroring [CaseEditViewModel]. The save/delete logic is the same plan the
 * [CaseDetailViewModel] runs, reusing [planSaveEvent].
 */
data class LogDetailScreenUiState(
    val isLoading: Boolean = true,
    val durationMode: DurationMode = DurationMode.NONE,
    val intensityEnabled: Boolean = false,
    val tagSuggestions: List<TagEntity> = emptyList(),
    val initialDraft: LogDraft? = null,
    val now: Long = 0L,
    /** The event or its Case is gone (deleted from elsewhere) — nothing to edit, bounce straight back. */
    val isFinished: Boolean = false,
)

@HiltViewModel
class LogDetailScreenViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))
        private val eventId: Long = requireNotNull(savedStateHandle.get<Long>("eventId"))

        private var loadedEvent: EventEntity? = null
        private var originalTags: List<TagEntity> = emptyList()
        private var durationMode: DurationMode = DurationMode.NONE

        private val _uiState = MutableStateFlow(LogDetailScreenUiState())
        val uiState: StateFlow<LogDetailScreenUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val case = repository.getCase(caseId)
                val eventWithTags =
                    repository
                        .observeEventsWithTagsForCase(caseId)
                        .first()
                        .find { it.event.id == eventId }
                if (case == null || eventWithTags == null) {
                    _uiState.update { it.copy(isLoading = false, isFinished = true) }
                    return@launch
                }
                loadedEvent = eventWithTags.event
                originalTags = eventWithTags.tags
                durationMode = case.durationMode
                val now = clock.nowMillis()
                _uiState.value =
                    LogDetailScreenUiState(
                        isLoading = false,
                        durationMode = case.durationMode,
                        intensityEnabled = case.intensityEnabled,
                        tagSuggestions = repository.observeTagsForCase(caseId).first(),
                        initialDraft = draftFrom(eventWithTags.event, now = now, tags = eventWithTags.tags),
                        now = now,
                    )
            }
        }

        fun save(draft: LogDraft) {
            val existing = loadedEvent ?: return
            val plan = planSaveEvent(caseId, draft, existing, originalTags, durationMode, clock.nowMillis())
            viewModelScope.launch {
                val savedId =
                    if (plan.isUpdate) {
                        repository.updateEvent(plan.entity)
                        plan.entity.id
                    } else {
                        repository.insertEvent(plan.entity)
                    }
                plan.tagDiff.toAdd.forEach { repository.addTagToEvent(savedId, it) }
                plan.tagDiff.toRemove.forEach { repository.removeTagFromEvent(savedId, it.id) }
                _uiState.update { it.copy(isFinished = true) }
            }
        }

        fun delete() {
            val existing = loadedEvent ?: return
            viewModelScope.launch {
                repository.deleteEvent(existing)
                _uiState.update { it.copy(isFinished = true) }
            }
        }
    }
