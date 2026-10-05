package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.domain.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One Case alongside its derived Insights state and the events behind it, as the full-list screens need all three. */
internal data class CaseInsights(
    val case: CaseEntity,
    val eventsWithTags: List<EventWithTags>,
    val state: InsightsTabState,
)

/**
 * Re-derives a Case's [CaseInsights] whenever the Case, its events, or the most recent activity
 * across all Cases changes. Emits `null` when the Case no longer exists. Full-list screens use this
 * rather than sharing the Case Detail ViewModel's instance (see [TrendsListViewModel]).
 */
internal fun caseInsightsFlow(
    repository: HodithRepository,
    clock: Clock,
    caseId: Long,
): Flow<CaseInsights?> =
    combine(
        repository.observeCase(caseId),
        repository.observeEventsWithTagsForCase(caseId),
        repository.observeMostRecentLoggedAtAcrossActiveCases(),
    ) { case, events, mostRecentActivityAcrossCasesAt ->
        case?.let {
            CaseInsights(
                case = it,
                eventsWithTags = events,
                state =
                    insightsTabState(
                        it,
                        events,
                        clock.nowMillis(),
                        mostRecentActivityAcrossCasesAt = mostRecentActivityAcrossCasesAt,
                    ),
            )
        }
    }
