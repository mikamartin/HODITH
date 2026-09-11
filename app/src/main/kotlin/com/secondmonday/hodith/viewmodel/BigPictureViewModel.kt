package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.BigPictureDetail
import com.secondmonday.hodith.data.BigPictureDetailField
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CaseEventDetail
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventTagName
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One case's icon/name as shown in the Big Picture grid and its filter chips (spec §9). */
data class CalendarCase(
    val id: Long,
    val icon: String,
    val name: String,
)

/**
 * One event as plotted on the Big Picture grid (spec §9). Intensity is not encoded on the grid —
 * [intensity] carries through only for the day/week detail rows, and is null unless the Case has
 * intensity enabled. Duration is encoded on the grid only for an event whose active span covers
 * more than one calendar day: its icon then appears on every covered day and the start day's icon
 * is ringed. [endedAt] is null for a point event and for a still-running one — [isOngoing] tells
 * those apart, and a running event's span runs to today. [endedAt] is also null when the Case's
 * `durationMode` no longer tracks duration (spec §9: every event is then a point); this is a
 * render projection, never persisted, and the stored value stays intact.
 */
data class CalendarEvent(
    val id: Long,
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long? = null,
    val isOngoing: Boolean = false,
    val note: String? = null,
    val intensity: Int? = null,
    val tags: List<String> = emptyList(),
)

data class BigPictureUiState(
    val cases: List<CalendarCase> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val earliestMonth: YearMonth? = null,
    val currentMonth: YearMonth? = null,
    val today: LocalDate? = null,
    val detail: BigPictureDetail = BigPictureDetail.DEFAULT,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class BigPictureViewModel
    @Inject
    constructor(
        repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        clock: Clock,
    ) : ViewModel() {
        // Cases, event details, and tag names come from three lean projections (see
        // `CaseEventDetail`/`EventTagName`), not the events-per-Case `@Relation` graph. The mapping
        // runs on `Dispatchers.Default` so a rapid-logging burst never touches the main thread;
        // `clock.nowMillis()` is read per emission, so day-boundary rollover lands on the next
        // upstream emission or resubscribe.
        val uiState: StateFlow<BigPictureUiState> =
            combine(
                repository.observeActiveCases(),
                repository.observeActiveCaseEventDetails(),
                repository.observeActiveCaseEventTagNames(),
                settingsRepository.observeBigPictureDetail(),
            ) { cases, eventDetails, tagNames, detail ->
                bigPictureUiState(cases, eventDetails, tagNames, clock.nowMillis(), detail = detail)
            }.flowOn(Dispatchers.Default)
                .conflate()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = BigPictureUiState(),
                )

        /** Flips one field of the persisted "overview detail" preference (spec §9). */
        fun setDetail(
            field: BigPictureDetailField,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val current = settingsRepository.observeBigPictureDetail().first()
                settingsRepository.setBigPictureDetail(current.with(field, enabled))
            }
        }
    }

/**
 * Pure mapping, split out from [BigPictureViewModel] so it's unit-testable on the JVM without a
 * repository or Hilt. Takes the three lean projections separately rather than the events-per-Case
 * `@Relation` graph — [eventDetails] and [tagNames] are grouped by `caseId`/`eventId` here instead
 * of arriving pre-nested. `earliestMonth` comes from the oldest active case's `createdAt` in
 * [cases] — falls back to `currentMonth` when there are no cases at all.
 */
internal fun bigPictureUiState(
    cases: List<CaseEntity>,
    eventDetails: List<CaseEventDetail>,
    tagNames: List<EventTagName>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    detail: BigPictureDetail = BigPictureDetail.DEFAULT,
): BigPictureUiState {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val currentMonth = YearMonth.from(today)
    val earliestMonth =
        cases
            .minOfOrNull { it.createdAt }
            ?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()) }
            ?: currentMonth
    // Sorted so a detail row's pills read in a stable order — the flat projection returns them in
    // attach order, and the filter dialog's list is sorted too.
    val tagsByEvent = tagNames.groupBy(EventTagName::eventId) { it.tagName }.mapValues { (_, names) -> names.sorted() }
    val eventsByCase = eventDetails.groupBy(CaseEventDetail::caseId)
    return BigPictureUiState(
        cases = cases.map { CalendarCase(id = it.id, icon = it.icon, name = it.name) },
        events =
            cases.flatMap { case ->
                eventsByCase[case.id].orEmpty().map {
                    CalendarEvent(
                        id = it.id,
                        caseId = case.id,
                        occurredAt = it.occurredAt,
                        // Spec §9: a Case that no longer tracks duration renders every event as a
                        // point — drop the stored endedAt here so coveredDates collapses to the
                        // start day. START_STOP always tracks, so isOngoing is unaffected.
                        endedAt = if (case.durationMode.tracksDuration) it.endedAt else null,
                        isOngoing = case.durationMode == DurationMode.START_STOP && it.endedAt == null,
                        note = it.note,
                        // Detail-row only (spec §9: intensity is never on the grid); null unless the
                        // Case has intensity enabled, same gate the Insights row uses.
                        intensity = if (case.intensityEnabled) it.intensity else null,
                        tags = tagsByEvent[it.id].orEmpty(),
                    )
                }
            },
        earliestMonth = earliestMonth,
        currentMonth = currentMonth,
        today = today,
        detail = detail,
        isLoading = false,
    )
}
