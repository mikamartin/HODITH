package com.secondmonday.hodith.viewmodel

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.TagRenameOutcome
import com.secondmonday.hodith.domain.TagSummary
import com.secondmonday.hodith.domain.classifyTagRename
import com.secondmonday.hodith.domain.filterTagSummaries
import com.secondmonday.hodith.domain.shouldShowTagFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An operation waiting on its warning. Counts are captured when the request is made, so the warning states what will happen. */
sealed interface PendingTagAction {
    val tag: TagEntity

    data class Rename(
        override val tag: TagEntity,
        val newName: String,
        val eventCount: Int,
    ) : PendingTagAction

    data class Merge(
        override val tag: TagEntity,
        val target: TagEntity,
        val sourceEventCount: Int,
        val overlapCount: Int,
    ) : PendingTagAction

    data class Delete(
        override val tag: TagEntity,
        val eventCount: Int,
    ) : PendingTagAction
}

data class ManageTagsUiState(
    val isLoading: Boolean = true,
    val tags: List<TagSummary> = emptyList(),
    val visibleTags: List<TagSummary> = emptyList(),
    val showFilter: Boolean = false,
    val query: String = "",
    val pending: PendingTagAction? = null,
    /** Set when a read or write failed at the database. The failed operation wrote nothing; cleared by the next request. */
    val writeFailed: Boolean = false,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Global tag management (Settings → Manage tags). Tags are shared across every Case, so counts and
 * every write here cover archived Cases too. Nothing is written until the user confirms the warning
 * for the operation; [onConfirmPending] performs it.
 */
@HiltViewModel
class ManageTagsViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val pending = MutableStateFlow<PendingTagAction?>(null)
        private val writeFailed = MutableStateFlow(false)

        val uiState: StateFlow<ManageTagsUiState> =
            combine(
                repository.observeAllTags(),
                repository.observeTagEventCounts(),
                query,
                pending,
                writeFailed,
            ) { tags, counts, filter, action, failed ->
                val countsByTagId = counts.associate { it.tagId to it.eventCount }
                val summaries = tags.map { TagSummary(tag = it, eventCount = countsByTagId[it.id] ?: 0) }
                val showFilter = shouldShowTagFilter(summaries.size)
                ManageTagsUiState(
                    isLoading = false,
                    tags = summaries,
                    visibleTags = if (showFilter) filterTagSummaries(summaries, filter) else summaries,
                    showFilter = showFilter,
                    query = filter,
                    pending = action,
                    writeFailed = failed,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ManageTagsUiState(),
            )

        fun onQueryChange(value: String) {
            query.value = value
        }

        /**
         * Resolves a rename into its outcome before anything is written: a plain rename, a merge into
         * an existing tag, or nothing (blank or unchanged). The result becomes [ManageTagsUiState.pending].
         */
        fun onRenameRequested(
            summary: TagSummary,
            requestedName: String,
        ) {
            writeFailed.value = false
            viewModelScope.launch {
                attempt {
                    val collision = repository.findOtherTagByName(requestedName.trim(), excludeId = summary.tag.id)
                    when (val outcome = classifyTagRename(summary.tag, requestedName, collision)) {
                        TagRenameOutcome.NoChange -> Unit
                        TagRenameOutcome.Rename ->
                            pending.value = PendingTagAction.Rename(summary.tag, requestedName.trim(), summary.eventCount)
                        is TagRenameOutcome.Merge -> {
                            val overlap = repository.countEventsWithBoth(summary.tag.id, outcome.target.id)
                            pending.value =
                                PendingTagAction.Merge(
                                    tag = summary.tag,
                                    target = outcome.target,
                                    sourceEventCount = summary.eventCount,
                                    overlapCount = overlap,
                                )
                        }
                    }
                }
            }
        }

        fun onDeleteRequested(summary: TagSummary) {
            writeFailed.value = false
            pending.value = PendingTagAction.Delete(summary.tag, summary.eventCount)
        }

        fun onConfirmPending() {
            val action = pending.value ?: return
            pending.value = null
            writeFailed.value = false
            viewModelScope.launch {
                attempt {
                    when (action) {
                        is PendingTagAction.Rename -> {
                            // Re-checked at confirm time: a tag matching the new name could have appeared since the warning.
                            // Writing anyway would violate the unique index, so the rename is dropped and the list shows the current state.
                            val collision = repository.findOtherTagByName(action.newName, excludeId = action.tag.id)
                            if (collision == null) repository.renameTag(action.tag.id, action.newName)
                        }
                        // A target deleted since the warning makes the merge throw; the transaction rolls back and [attempt] reports it.
                        is PendingTagAction.Merge -> repository.mergeTag(sourceId = action.tag.id, targetId = action.target.id)
                        is PendingTagAction.Delete -> repository.deleteTag(action.tag.id)
                    }
                }
            }
        }

        fun onDismissPending() {
            pending.value = null
        }

        /** Catches only [SQLException], as [com.secondmonday.hodith.viewmodel.SettingsViewModel] does, so cancellation still propagates. */
        private suspend fun attempt(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: SQLException) {
                writeFailed.value = true
            }
        }
    }
