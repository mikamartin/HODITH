package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.HistorySortOrder
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.ui.voice.DOT_SEPARATOR
import com.secondmonday.hodith.ui.voice.Voice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class CaseDetailUiState(
    val case: CaseEntity? = null,
    val events: List<EventWithTags> = emptyList(),
    val historyEvents: List<EventWithTags> = emptyList(),
    val historyHasMore: Boolean = false,
    val historySortOrder: HistorySortOrder = HistorySortOrder.BY_START,
    val historyDateFrom: Long? = null,
    val historyDateTo: Long? = null,
    val historyVisibleFields: Set<HistoryRowField> = HistoryRowField.entries.toSet(),
    val tagSuggestions: List<TagEntity> = emptyList(),
    val mostRecentActivityAcrossCasesAt: Long? = null,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** History tab paging (spec §6, PROGRESS.md F4) — the row list starts at this many events... */
private const val HISTORY_INITIAL_LIMIT = 30

/** ...and each "Show more" tap grows the loaded window by this many, cumulatively (30 → 80 → 130 → ...). */
private const val HISTORY_LOAD_MORE_INCREMENT = 50

/** [CaseDetailViewModel.historyPage]'s combined query key — a named tuple in place of a five-element [Triple]-of-[Triple]s. */
private data class HistoryPageQuery(
    val case: CaseEntity?,
    val order: HistorySortOrder,
    val dateFrom: Long?,
    val dateTo: Long?,
    val limit: Int,
)

@HiltViewModel
class CaseDetailViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))
        private val zone = ZoneId.systemDefault()

        private val historySortOrder = settingsRepository.observeHistorySortOrder()
        private val historyDateFrom = settingsRepository.observeHistoryDateFrom()
        private val historyDateTo = settingsRepository.observeHistoryDateTo()
        private val historyVisibleFields = settingsRepository.observeHistoryVisibleFields()
        private val historyLimit = MutableStateFlow(HISTORY_INITIAL_LIMIT)

        /**
         * The History tab's capped, sorted page — re-queried (not re-sorted client-side) whenever
         * the sort order, the date range, the loaded limit, or the Case's `durationMode` changes;
         * the last of those matters because [HistorySortOrder.BY_END]'s "is this running?" check is
         * only meaningful for a `START_STOP` Case (PROGRESS.md F4).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val historyPage =
            combine(
                repository.observeCase(caseId),
                historySortOrder,
                historyDateFrom,
                historyDateTo,
                historyLimit,
            ) { case, order, dateFrom, dateTo, limit -> HistoryPageQuery(case, order, dateFrom, dateTo, limit) }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    repository.observeHistoryEventsForCase(
                        caseId,
                        query.order,
                        query.limit,
                        query.case?.durationMode ?: DurationMode.NONE,
                        query.dateFrom,
                        query.dateTo,
                    )
                }

        val uiState: StateFlow<CaseDetailUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
                repository.observeTagsForCase(caseId),
            ) { case, events, tagSuggestions ->
                CaseDetailUiState(
                    case = case,
                    events = events,
                    tagSuggestions = tagSuggestions,
                    isLoading = false,
                )
            }.combine(historyPage) { partial, page -> partial.copy(historyEvents = page.events, historyHasMore = page.hasMore) }
                .combine(historySortOrder) { partial, order -> partial.copy(historySortOrder = order) }
                .combine(historyDateFrom) { partial, dateFrom -> partial.copy(historyDateFrom = dateFrom) }
                .combine(historyDateTo) { partial, dateTo -> partial.copy(historyDateTo = dateTo) }
                .combine(historyVisibleFields) { partial, fields -> partial.copy(historyVisibleFields = fields) }
                .combine(repository.observeMostRecentLoggedAtAcrossActiveCases()) { partial, mostRecentActivityAcrossCasesAt ->
                    partial.copy(mostRecentActivityAcrossCasesAt = mostRecentActivityAcrossCasesAt)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = CaseDetailUiState(),
                )

        /**
         * Switches the History tab's sort order and resets the loaded window back to
         * [HISTORY_INITIAL_LIMIT] — a re-sorted list should read as a fresh top-[HISTORY_INITIAL_LIMIT], not
         * keep whatever window size was earned by tapping "Show more" under the old order.
         */
        fun setHistorySortOrder(order: HistorySortOrder) {
            viewModelScope.launch { settingsRepository.setHistorySortOrder(order) }
            historyLimit.value = HISTORY_INITIAL_LIMIT
        }

        /** Same reset-the-window rationale as [setHistorySortOrder]: a newly-narrowed range reads as a fresh top-[HISTORY_INITIAL_LIMIT]. */
        fun setHistoryDateFrom(date: LocalDate?) {
            viewModelScope.launch { settingsRepository.setHistoryDateFrom(date?.let { zone.startOfDayMillis(it) }) }
            historyLimit.value = HISTORY_INITIAL_LIMIT
        }

        /** See [setHistoryDateFrom]. `null` clears the upper bound back to "through today". */
        fun setHistoryDateTo(date: LocalDate?) {
            viewModelScope.launch { settingsRepository.setHistoryDateTo(date?.let { zone.endOfDayMillis(it) }) }
            historyLimit.value = HISTORY_INITIAL_LIMIT
        }

        /** Flips one field of the persisted History-tab row-display preference — purely a rendering choice, so it doesn't reset [historyLimit]. */
        fun setHistoryFieldVisible(
            field: HistoryRowField,
            visible: Boolean,
        ) {
            viewModelScope.launch {
                val current = settingsRepository.observeHistoryVisibleFields().first()
                settingsRepository.setHistoryVisibleFields(if (visible) current + field else current - field)
            }
        }

        /** "Show more" — cumulative, one-directional growth of the History tab's loaded window. */
        fun loadMoreHistoryEvents() {
            historyLimit.update { it + HISTORY_LOAD_MORE_INCREMENT }
        }

        /** Always immediate, regardless of `logFlow` — see [HomeViewModel.onQuickLogTap]. */
        fun stopEvent(event: EventEntity) {
            viewModelScope.launch { repository.updateEvent(event.copy(endedAt = clock.nowMillis())) }
        }

        fun newEventDraft(): LogDraft = draftFrom(event = null, now = clock.nowMillis())

        fun nowMillis(): Long = clock.nowMillis()

        /**
         * Quick-log / retro-log a *new* event from the History tab's sheet. Editing an existing event
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
 * Renders a verdict rate — "2.6×/week" for an occurrence-count expectation, "5.6 days/week" for a
 * days-active one. Shared by the expectation chip and verdict headline so the number always reads
 * the same way everywhere it appears (spec §8).
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
 * Renders a stated expectation — "~5×/week" or "~4 days/week" — the whole-number counterpart of
 * [formatRate], used wherever the expectation itself (not an observed rate) is quoted back.
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
 * [showIntensity] defaults to true (the History tab always shows it); the Insights tab's intensity
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
    return parts.takeIf { it.isNotEmpty() }?.joinToString(DOT_SEPARATOR)
}
