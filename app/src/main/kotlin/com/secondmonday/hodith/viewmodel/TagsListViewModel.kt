package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.TagBreakdownEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TagsListUiState(
    val caseIcon: String = "",
    val caseName: String = "",
    val durationMode: DurationMode = DurationMode.NONE,
    val totalEventCount: Int = 0,
    val distinctTagCount: Int = 0,
    val taggedEventCount: Int = 0,
    val tags: List<TagBreakdownEntry> = emptyList(),
    val eventsWithTags: List<EventWithTags> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Insights tag card's full-list screen: every tag, not just the collapsed card's busiest few. Derives its state through [caseInsightsFlow], as [TrendsListViewModel] does. */
@HiltViewModel
class TagsListViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        /** Read by the screen's ticking clock (see [com.secondmonday.hodith.ui.common.rememberTickingNow]) so ongoing events keep their elapsed time current. */
        fun nowMillis(): Long = clock.nowMillis()

        val uiState: StateFlow<TagsListUiState> =
            caseInsightsFlow(repository, clock, caseId)
                .map { insights ->
                    if (insights == null) {
                        TagsListUiState(isLoading = false)
                    } else {
                        val stats = (insights.state as? InsightsTabState.Ready)?.stats
                        TagsListUiState(
                            caseIcon = insights.case.icon,
                            caseName = insights.case.name,
                            durationMode = insights.case.durationMode,
                            totalEventCount = stats?.totalEventCount ?: 0,
                            distinctTagCount = stats?.distinctTagCount ?: 0,
                            taggedEventCount = stats?.taggedEventCount ?: 0,
                            tags = stats?.tags.orEmpty(),
                            eventsWithTags = insights.eventsWithTags,
                            isLoading = false,
                        )
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = TagsListUiState(),
                )
    }
