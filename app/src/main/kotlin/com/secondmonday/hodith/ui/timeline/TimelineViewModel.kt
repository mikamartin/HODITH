package com.secondmonday.hodith.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseWithEvents
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.timeline.TimeWindow
import com.secondmonday.hodith.domain.timeline.TimelineEvent
import com.secondmonday.hodith.domain.timeline.ZoomLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel
    @Inject
    constructor(
        repository: HodithRepository,
        clock: Clock,
    ) : ViewModel() {
        val initialWindow: TimeWindow =
            clock.nowMillis().let { now -> TimeWindow(now - ZoomLevel.MONTH.durationMillis, now) }

        val rows: StateFlow<List<TimelineRowData>> =
            repository
                .observeActiveCasesWithEvents()
                .map { cases -> cases.map { it.toTimelineRowData() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

fun CaseWithEvents.toTimelineRowData(): TimelineRowData =
    TimelineRowData(
        caseId = case.id,
        icon = case.icon,
        name = case.name,
        events =
            events.map {
                TimelineEvent(id = it.id, occurredAt = it.occurredAt, endedAt = it.endedAt, intensity = it.intensity)
            },
        intensityEnabled = case.intensityEnabled,
    )
