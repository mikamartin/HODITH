package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HodithRepository
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
import java.util.Locale
import javax.inject.Inject

data class CaseDetailUiState(
    val case: CaseEntity? = null,
    val events: List<EventWithTags> = emptyList(),
    val tagSuggestions: List<TagEntity> = emptyList(),
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
            ) { case, events, tagSuggestions ->
                CaseDetailUiState(case = case, events = events, tagSuggestions = tagSuggestions, isLoading = false)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CaseDetailUiState(),
            )

        fun deleteEvent(event: EventEntity) {
            viewModelScope.launch { repository.deleteEvent(event) }
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
 * Pure mapping of an event's optional fields (plus its tags) into its detail line, split out
 * from the Case Detail screen for the same reason as [formatEventTime]. Returns null when
 * there's nothing beyond the time to show.
 */
internal fun eventDetailSummary(
    event: EventEntity,
    tags: List<TagEntity>,
    voice: Voice,
): String? {
    val parts = mutableListOf<String>()
    event.intensity?.let { parts += voice.eventIntensityLabel(it) }
    event.note?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (tags.isNotEmpty()) parts += tags.joinToString(" ") { "#${it.name}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
