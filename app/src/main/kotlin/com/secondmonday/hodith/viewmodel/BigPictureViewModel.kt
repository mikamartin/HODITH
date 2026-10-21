package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.BigPictureDetail
import com.secondmonday.hodith.data.BigPictureDetailField
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CaseEventDetail
import com.secondmonday.hodith.data.EventTagName
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.utcOffsetFromMinutes
import com.secondmonday.hodith.di.DefaultDispatcher
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.YearMonth
import com.secondmonday.hodith.domain.isStillRunning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
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
 * is ringed. [endedAt] is null for a point event and for a still-running one with no stated end —
 * [isOngoing] tells those apart, and a running event's span runs to today. [endedAt] is also null
 * when the Case's `tracksDuration` flag is off (spec §9: every event is then a point); this is a
 * render projection, never persisted, and the stored value stays intact.
 *
 * [expectedEndAt] is the one case where [endedAt] *isn't* null on a still-running event: a stated/
 * expected duration that hasn't elapsed yet ([isStillRunning]), so there's a real (not-yet-true)
 * end instant to show rather than an open-ended "ongoing since" — the day/week detail row reads
 * this instead of [isOngoing] when it's set.
 */
data class CalendarEvent(
    val id: Long,
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long? = null,
    val isOngoing: Boolean = false,
    val expectedEndAt: Long? = null,
    val note: String? = null,
    val intensity: Int? = null,
    val tags: List<String> = emptyList(),
    val utcOffsetMinutes: Int = 0,
)

/** This event's own captured offset, for bucketing its timestamps into the calendar day/hour they actually occurred in. */
internal fun CalendarEvent.loggedZone(): UtcOffset = utcOffsetFromMinutes(utcOffsetMinutes)

data class BigPictureUiState(
    val cases: List<CalendarCase> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val earliestMonth: YearMonth? = null,
    val currentMonth: YearMonth? = null,
    val today: LocalDate? = null,
    val detail: BigPictureDetail = BigPictureDetail.DEFAULT,
    val isLoading: Boolean = true,
    // Raw persisted filter selections (spec §9) — `null` means "no filter stored", resolved
    // against the live cases/tags in `BigPictureGrid`, not here (see `resolveVisibleSelection`).
    val visibleCaseIds: Set<Long>? = null,
    val visibleTagNames: Set<String>? = null,
    val selectedYear: Int? = null,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class BigPictureViewModel
    @Inject
    constructor(
        repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        clock: Clock,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        // Cases, event details, and tag names come from three lean projections (see
        // `CaseEventDetail`/`EventTagName`), not the events-per-Case `@Relation` graph. The mapping
        // runs on `defaultDispatcher` (`Dispatchers.Default` in production, injected so a
        // rapid-logging burst never touches the main thread; a deterministic test dispatcher in JVM
        // unit tests, so the recombination stays inside `runTest`'s virtual scheduler instead of
        // racing a real thread); `clock.nowMillis()` is read per emission, so day-boundary rollover
        // lands on the next upstream emission or resubscribe.
        val uiState: StateFlow<BigPictureUiState> =
            combine(
                repository.observeActiveCases(),
                repository.observeActiveCaseEventDetails(),
                repository.observeActiveCaseEventTagNames(),
                settingsRepository.observeBigPictureDetail(),
            ) { cases, eventDetails, tagNames, detail ->
                bigPictureUiState(cases, eventDetails, tagNames, clock.nowMillis(), detail = detail)
            }.combine(settingsRepository.observeBigPictureVisibleCaseIds()) { partial, visibleCaseIds ->
                partial.copy(visibleCaseIds = visibleCaseIds)
            }.combine(settingsRepository.observeBigPictureVisibleTagNames()) { partial, visibleTagNames ->
                partial.copy(visibleTagNames = visibleTagNames)
            }.combine(settingsRepository.observeBigPictureSelectedYear()) { partial, selectedYear ->
                partial.copy(selectedYear = selectedYear)
            }.flowOn(defaultDispatcher)
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

        /** Persists the Cases filter (spec §9); `null` means "no filter stored" (all visible). */
        fun setVisibleCaseIds(caseIds: Set<Long>?) {
            viewModelScope.launch { settingsRepository.setBigPictureVisibleCaseIds(caseIds) }
        }

        /** Persists the Tags filter (spec §9); `null` means "no filter stored" (all visible). */
        fun setVisibleTagNames(tagNames: Set<String>?) {
            viewModelScope.launch { settingsRepository.setBigPictureVisibleTagNames(tagNames) }
        }

        /** Persists the Year filter (spec §9); `null` means "All years". */
        fun setSelectedYear(year: Int?) {
            viewModelScope.launch { settingsRepository.setBigPictureSelectedYear(year) }
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
    zoneId: TimeZone = TimeZone.currentSystemDefault(),
    detail: BigPictureDetail = BigPictureDetail.DEFAULT,
): BigPictureUiState {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zoneId).date
    val currentMonth = YearMonth.from(today)
    val earliestMonth =
        cases
            .minOfOrNull { it.createdAt }
            ?.let { YearMonth.from(Instant.fromEpochMilliseconds(it).toLocalDateTime(zoneId).date) }
            ?: currentMonth
    // Sorted so a detail row's pills read in a stable order — the flat projection returns them in
    // attach order, and the filter dialog's list is sorted too.
    val tagsByEvent = tagNames.groupBy(EventTagName::eventId) { it.tagName }.mapValues { (_, names) -> names.sorted() }
    val eventsByCase = eventDetails.groupBy(CaseEventDetail::caseId)
    return BigPictureUiState(
        cases = cases.map { CalendarCase(id = it.id, icon = it.icon, name = it.name) },
        events =
            cases.flatMap { case ->
                eventsByCase[case.id].orEmpty().map { event ->
                    val stillRunning = isStillRunning(event.endedAt, case.tracksDuration, nowMillis)
                    CalendarEvent(
                        id = event.id,
                        caseId = case.id,
                        occurredAt = event.occurredAt,
                        // Spec §9: a Case that doesn't track duration renders every event as a
                        // point — drop the stored endedAt here so coveredDates collapses to the
                        // start day.
                        endedAt = if (case.tracksDuration) event.endedAt else null,
                        isOngoing = stillRunning,
                        // A stated endedAt is only ever reached here while still running if it's in
                        // the future (see isStillRunning) — the projected/expected end to show.
                        expectedEndAt = event.endedAt.takeIf { stillRunning },
                        note = event.note,
                        // Detail-row only (spec §9: intensity is never on the grid); null unless the
                        // Case has intensity enabled, same gate the Insights row uses.
                        intensity = if (case.intensityEnabled) event.intensity else null,
                        tags = tagsByEvent[event.id].orEmpty(),
                        utcOffsetMinutes = event.utcOffsetMinutes,
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
