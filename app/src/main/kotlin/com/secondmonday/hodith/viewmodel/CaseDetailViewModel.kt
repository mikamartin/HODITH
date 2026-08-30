package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.ui.voice.Voice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class CaseDetailUiState(
    val case: CaseEntity? = null,
    val events: List<EventWithTags> = emptyList(),
    val tagSuggestions: List<TagEntity> = emptyList(),
    val activeHunch: HunchEntity? = null,
    val hunchHistory: List<HunchEntity> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class CaseDetailViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        val uiState: StateFlow<CaseDetailUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
                repository.observeTagsForCase(caseId),
                repository.observeActiveHunch(caseId),
                repository.observeHunchHistory(caseId),
            ) { case, events, tagSuggestions, activeHunch, hunchHistory ->
                CaseDetailUiState(
                    case = case,
                    events = events,
                    tagSuggestions = tagSuggestions,
                    activeHunch = activeHunch,
                    hunchHistory = hunchHistory,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CaseDetailUiState(),
            )

        fun deleteEvent(event: EventEntity) {
            viewModelScope.launch { repository.deleteEvent(event) }
        }

        /** Always immediate, regardless of `logFlow` — see [HomeViewModel.onQuickLogTap]. */
        fun stopEvent(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(endedAt = clock.nowMillis())) }
        }

        fun dismissStalePrompt(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(staleNudgeDismissedAt = clock.nowMillis())) }
        }

        fun newEventDraft(): LogDraft = draftFrom(event = null, now = clock.nowMillis())

        fun nowMillis(): Long = clock.nowMillis()

        fun saveEvent(
            draft: LogDraft,
            existingEvent: EventEntity?,
            originalTags: List<TagEntity>,
        ) {
            val durationMode = uiState.value.case?.durationMode ?: return
            val plan = planSaveEvent(caseId, draft, existingEvent, originalTags, durationMode, clock.nowMillis())
            viewModelScope.launch {
                val eventId =
                    if (plan.isUpdate) {
                        repository.updateEvent(plan.entity)
                        plan.entity.id
                    } else {
                        repository.insertEvent(plan.entity)
                    }
                plan.tagDiff.toAdd.forEach { repository.addTagToEvent(eventId, it) }
                plan.tagDiff.toRemove.forEach { repository.removeTagFromEvent(eventId, it.id) }
            }
        }

        fun addHunch(
            direction: HunchDirection,
            expectedCount: Int,
            expectedPer: ExpectedPer,
        ) {
            viewModelScope.launch {
                repository.insertHunch(
                    HunchEntity(
                        caseId = caseId,
                        direction = direction,
                        expectedCount = expectedCount,
                        expectedPer = expectedPer,
                        createdAt = clock.nowMillis(),
                        resolvedAt = null,
                    ),
                )
            }
        }

        fun resolveHunch(hunch: HunchEntity) {
            viewModelScope.launch { repository.updateHunch(hunch.copy(resolvedAt = clock.nowMillis())) }
        }

        fun dismissHunchNudge() {
            val case = uiState.value.case ?: return
            viewModelScope.launch { repository.updateCase(case.copy(hunchNudgeDismissed = true)) }
        }
    }

private val EVENT_TIME_WITH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a, EEE", Locale.US)
private val EVENT_TIME_NO_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a, EEE", Locale.US)
private val EVENT_DATE_ONLY_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
private val EVENT_TIME_ONLY_FORMATTER = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)

/**
 * Pure formatting for the event list row, split out from the Case Detail screen so it's
 * unit-testable on the JVM without Compose, same pattern as [homeCaseRows]. Always includes the
 * weekday; the year is shown only when [occurredAt] falls in a different calendar year than
 * [now] — most logged events are recent, so a same-year date reads better without the year's
 * visual noise, while older records still need it to stay unambiguous. [zone] defaults to the
 * device zone but is overridable so tests don't depend on the machine running them.
 */
internal fun formatEventTime(
    occurredAt: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val eventZoned = Instant.ofEpochMilli(occurredAt).atZone(zone)
    val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
    val formatter = if (eventZoned.year == nowZoned.year) EVENT_TIME_NO_YEAR_FORMATTER else EVENT_TIME_WITH_YEAR_FORMATTER
    return eventZoned.format(formatter)
}

/** Date-only counterpart of [formatEventTime], for the log sheet's separate date/time buttons. */
internal fun formatEventDate(
    occurredAt: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(occurredAt).atZone(zone).format(EVENT_DATE_ONLY_FORMATTER)

/** Time-only counterpart of [formatEventTime], for the log sheet's separate date/time buttons. */
internal fun formatEventTimeOfDay(
    occurredAt: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(occurredAt).atZone(zone).format(EVENT_TIME_ONLY_FORMATTER)

/**
 * Renders a verdict rate as "2.6×/week" — shared by the hunch chip, verdict headline, and
 * history rows so the number always reads the same way everywhere it appears (spec §8).
 */
internal fun formatRate(
    rate: Double,
    per: ExpectedPer,
): String {
    val perLabel =
        when (per) {
            ExpectedPer.DAY -> "day"
            ExpectedPer.WEEK -> "week"
            ExpectedPer.MONTH -> "month"
        }
    return String.format(Locale.US, "%.1f×/%s", rate, perLabel)
}

/**
 * Renders a Hunch's stated expectation as "~5×/week" — the whole-number counterpart of
 * [formatRate], used wherever the Hunch itself (not an observed rate) is quoted back.
 */
internal fun formatExpectedFrequency(
    expectedCount: Int,
    expectedPer: ExpectedPer,
): String {
    val perLabel =
        when (expectedPer) {
            ExpectedPer.DAY -> "day"
            ExpectedPer.WEEK -> "week"
            ExpectedPer.MONTH -> "month"
        }
    return "~$expectedCount×/$perLabel"
}

/**
 * Whole months between [pastMillis] and [nowMillis] in the device zone, for the hunch history
 * list's "N months ago" rows. Calendar-month-aware (via [java.time.temporal.ChronoUnit.MONTHS]),
 * not a fixed 30-day division, so it doesn't drift against actual month boundaries.
 */
internal fun monthsAgo(
    pastMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val past = Instant.ofEpochMilli(pastMillis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    return ChronoUnit.MONTHS.between(past, now)
}

/**
 * Pure mapping of an event's optional fields (plus its tags) into its detail line, split out
 * from the Case Detail screen for the same reason as [formatEventTime]. Returns null when
 * there's nothing beyond the time to show. [isOngoing] should only ever be true for a
 * `START_STOP` case's still-open event (spec §6) — the caller is responsible for that gate,
 * since a plain `endedAt == null` alone is ambiguous with `NONE`/`MANUAL` events that simply
 * have no duration. An ongoing event's running state is drawn separately (the "Ongoing" pill
 * + live elapsed), so this only contributes its intensity/note/tags. A finished duration event
 * (any mode with a real `endedAt`) shows how long it lasted, via the same [formatElapsedDuration]
 * the ongoing indicator uses.
 */
internal fun eventDetailSummary(
    event: EventEntity,
    tags: List<TagEntity>,
    voice: Voice,
    isOngoing: Boolean = false,
): String? {
    val parts = mutableListOf<String>()
    if (!isOngoing) {
        event.endedAt?.let { parts += voice.eventDurationLabel(formatElapsedDuration(event.occurredAt, it)) }
    }
    event.intensity?.let { parts += voice.eventIntensityLabel(it) }
    event.note?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (tags.isNotEmpty()) parts += tags.joinToString(" ") { "#${it.name}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
