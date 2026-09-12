package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogSortOrder
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.ui.voice.Voice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class CaseDetailUiState(
    val case: CaseEntity? = null,
    val events: List<EventWithTags> = emptyList(),
    val logEvents: List<EventWithTags> = emptyList(),
    val logHasMore: Boolean = false,
    val logSortOrder: LogSortOrder = LogSortOrder.BY_START,
    val tagSuggestions: List<TagEntity> = emptyList(),
    val activeHunch: HunchEntity? = null,
    val hunchHistory: List<HunchEntity> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Log tab paging (spec §6, PROGRESS.md F4) — the row list starts at this many events... */
private const val LOG_INITIAL_LIMIT = 30

/** ...and each "Show more" tap grows the loaded window by this many, cumulatively (30 → 80 → 130 → ...). */
private const val LOG_LOAD_MORE_INCREMENT = 50

@HiltViewModel
class CaseDetailViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        private val logSortOrder = MutableStateFlow(LogSortOrder.BY_START)
        private val logLimit = MutableStateFlow(LOG_INITIAL_LIMIT)

        /**
         * The Log tab's capped, sorted page — re-queried (not re-sorted client-side) whenever the
         * sort order, the loaded limit, or the Case's `durationMode` changes; the last of those
         * matters because [LogSortOrder.BY_END]'s "is this running?" check is only meaningful for a
         * `START_STOP` Case (PROGRESS.md F4).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val logPage =
            combine(repository.observeCase(caseId), logSortOrder, logLimit) { case, order, limit -> Triple(case, order, limit) }
                .distinctUntilChanged()
                .flatMapLatest { (case, order, limit) ->
                    repository.observeLogEventsForCase(caseId, order, limit, case?.durationMode ?: DurationMode.NONE)
                }

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
            }.combine(logPage) { partial, page -> partial.copy(logEvents = page.events, logHasMore = page.hasMore) }
                .combine(logSortOrder) { partial, order -> partial.copy(logSortOrder = order) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = CaseDetailUiState(),
                )

        /**
         * Switches the Log tab's sort order and resets the loaded window back to
         * [LOG_INITIAL_LIMIT] — a re-sorted list should read as a fresh top-[LOG_INITIAL_LIMIT], not
         * keep whatever window size was earned by tapping "Show more" under the old order.
         */
        fun setLogSortOrder(order: LogSortOrder) {
            logSortOrder.value = order
            logLimit.value = LOG_INITIAL_LIMIT
        }

        /** "Show more" — cumulative, one-directional growth of the Log tab's loaded window. */
        fun loadMoreLogEvents() {
            logLimit.update { it + LOG_LOAD_MORE_INCREMENT }
        }

        /** Always immediate, regardless of `logFlow` — see [HomeViewModel.onQuickLogTap]. */
        fun stopEvent(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(endedAt = clock.nowMillis())) }
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
            metric: VerdictMetric,
            observationWindow: ObservationWindow,
            windowStartDate: Long?,
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
                        metric = metric,
                        observationWindow = observationWindow,
                        windowStartDate = windowStartDate,
                    ),
                )
            }
        }

        fun resolveHunch(hunch: HunchEntity) {
            viewModelScope.launch { repository.updateHunch(hunch.copy(resolvedAt = clock.nowMillis())) }
        }
    }

/** "day" / "week" / "month" / "3 months" — the period a rate or expectation is stated against. */
private fun perUnitLabel(per: ExpectedPer): String =
    when (per) {
        ExpectedPer.DAY -> "day"
        ExpectedPer.WEEK -> "week"
        ExpectedPer.MONTH -> "month"
        ExpectedPer.QUARTER -> "3 months"
    }

/**
 * Renders a verdict rate — "2.6×/week" for an occurrence-count Hunch, "5.6 days/week" for a
 * days-active one. Shared by the hunch chip, verdict headline, and history rows so the number
 * always reads the same way everywhere it appears (spec §8).
 */
internal fun formatRate(
    rate: Double,
    per: ExpectedPer,
    metric: VerdictMetric,
): String {
    val perLabel = perUnitLabel(per)
    return when (metric) {
        VerdictMetric.OCCURRENCE_COUNT -> String.format(Locale.US, "%.1f×/%s", rate, perLabel)
        VerdictMetric.DAYS_ACTIVE -> String.format(Locale.US, "%.1f days/%s", rate, perLabel)
    }
}

/**
 * Renders a Hunch's stated expectation — "~5×/week" or "~4 days/week" — the whole-number
 * counterpart of [formatRate], used wherever the Hunch itself (not an observed rate) is quoted back.
 */
internal fun formatExpectedFrequency(
    expectedCount: Int,
    expectedPer: ExpectedPer,
    metric: VerdictMetric,
): String {
    val perLabel = perUnitLabel(expectedPer)
    return when (metric) {
        VerdictMetric.OCCURRENCE_COUNT -> "~$expectedCount×/$perLabel"
        VerdictMetric.DAYS_ACTIVE -> "~$expectedCount days/$perLabel"
    }
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
 *
 * [showIntensity] defaults to true (the Log tab always shows it); the Insights tab's intensity
 * drill-down dialog (spec §10) passes false, since every row there already shares the intensity
 * the dialog's own title states — repeating it on each row would be pure noise, not information.
 */
internal fun eventDetailSummary(
    event: EventEntity,
    tags: List<TagEntity>,
    voice: Voice,
    isOngoing: Boolean = false,
    tracksDuration: Boolean = true,
    showIntensity: Boolean = true,
): String? =
    eventDetailSummary(
        occurredAt = event.occurredAt,
        endedAt = event.endedAt,
        intensity = event.intensity,
        note = event.note,
        tagNames = tags.map { it.name },
        voice = voice,
        isOngoing = isOngoing,
        tracksDuration = tracksDuration,
        showIntensity = showIntensity,
    )

/**
 * The primitive-parameter core of [eventDetailSummary] — the same ` · `-joined line (duration,
 * intensity, note, then tags, any of which may be absent; null when none apply) built from an
 * event's raw fields rather than an [EventEntity]. Big Picture's grid holds only its own
 * `CalendarEvent` projection, so its detail rows (spec §9) call this directly, passing no note or
 * tags (those render on their own lines) — it contributes only the duration/intensity line there.
 *
 * Tag names render alphabetically, matching the tag lists in the Big Picture and Insights filter
 * dialogs; the DB `@Relation` hands them back in attach order, so the sort lives here.
 */
internal fun eventDetailSummary(
    occurredAt: Long,
    endedAt: Long?,
    intensity: Int?,
    note: String?,
    tagNames: List<String>,
    voice: Voice,
    isOngoing: Boolean = false,
    tracksDuration: Boolean = true,
    showIntensity: Boolean = true,
): String? {
    val parts = mutableListOf<String>()
    if (!isOngoing && tracksDuration) {
        endedAt
            ?.takeIf { it > occurredAt }
            ?.let { parts += voice.eventDurationLabel(formatElapsedDuration(occurredAt, it)) }
    }
    if (showIntensity) intensity?.let { parts += voice.eventIntensityLabel(it) }
    note?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (tagNames.isNotEmpty()) parts += tagNames.sorted().joinToString(" ") { "#$it" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
