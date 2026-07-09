package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
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
    val events: List<EventEntity> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class CaseDetailViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        val uiState: StateFlow<CaseDetailUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeEventsForCase(caseId),
            ) { case, events ->
                CaseDetailUiState(case = case, events = events, isLoading = false)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CaseDetailUiState(),
            )

        fun deleteEvent(event: EventEntity) {
            viewModelScope.launch { repository.deleteEvent(event) }
        }
    }

private val EVENT_TIME_FORMATTER =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.US)

/**
 * Pure formatting, split out from the Case Detail screen so it's unit-testable on the JVM
 * without Compose, same pattern as [homeCaseRows]. [zone] defaults to the device zone but is
 * overridable so tests don't depend on the machine running them.
 */
internal fun formatEventTime(
    occurredAt: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(occurredAt).atZone(zone).format(EVENT_TIME_FORMATTER)

/**
 * Pure mapping of an event's optional fields into its detail line, split out from the Case
 * Detail screen for the same reason as [formatEventTime]. Returns null when there's nothing
 * beyond the time to show.
 */
internal fun eventDetailSummary(
    event: EventEntity,
    voice: Voice,
): String? {
    val parts = mutableListOf<String>()
    event.intensity?.let { parts += voice.eventIntensityLabel(it) }
    event.note?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
