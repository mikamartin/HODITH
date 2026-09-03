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

        /** Always immediate, regardless of `logFlow` — see [HomeViewModel.onQuickLogTap]. */
        fun stopEvent(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(endedAt = clock.nowMillis())) }
        }

        fun dismissStalePrompt(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(staleNudgeDismissedAt = clock.nowMillis())) }
        }

        fun newEventDraft(): LogDraft = draftFrom(event = null, now = clock.nowMillis())

        fun nowMillis(): Long = clock.nowMillis()

        /**
         * Quick-log / retro-log a *new* event from the Log tab's sheet. Editing an existing event
         * is [com.secondmonday.hodith.viewmodel.LogDetailScreenViewModel]'s job, on its own screen.
         */
        fun saveNewEvent(draft: LogDraft) {
            val durationMode = uiState.value.case?.durationMode ?: return
            val plan =
                planSaveEvent(
                    caseId = caseId,
                    draft = draft,
                    existingEvent = null,
                    originalTags = emptyList(),
                    durationMode = durationMode,
                    now = clock.nowMillis(),
                )
            viewModelScope.launch {
                val eventId = repository.insertEvent(plan.entity)
                plan.tagDiff.toAdd.forEach { repository.addTagToEvent(eventId, it) }
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
 * shows how long it lasted (via the same [formatElapsedDuration] the ongoing indicator uses)
 * only when [tracksDuration] — the Case's `durationMode != NONE` — and the span is non-zero;
 * a Case switched to `NONE`, or a zero-length event (`endedAt == occurredAt`), is a point
 * with no duration line. Stored `endedAt` is never read past this gate, so it survives intact.
 */
internal fun eventDetailSummary(
    event: EventEntity,
    tags: List<TagEntity>,
    voice: Voice,
    isOngoing: Boolean = false,
    tracksDuration: Boolean = true,
): String? {
    val parts = mutableListOf<String>()
    if (!isOngoing && tracksDuration) {
        event.endedAt
            ?.takeIf { it > event.occurredAt }
            ?.let { parts += voice.eventDurationLabel(formatElapsedDuration(event.occurredAt, it)) }
    }
    event.intensity?.let { parts += voice.eventIntensityLabel(it) }
    event.note?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (tags.isNotEmpty()) parts += tags.joinToString(" ") { "#${it.name}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
