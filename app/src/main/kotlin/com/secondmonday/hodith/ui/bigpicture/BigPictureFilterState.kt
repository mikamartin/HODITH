package com.secondmonday.hodith.ui.bigpicture

import com.secondmonday.hodith.domain.YearMonth
import com.secondmonday.hodith.viewmodel.CalendarCase
import com.secondmonday.hodith.viewmodel.CalendarEvent
import com.secondmonday.hodith.viewmodel.loggedZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Pure filter/legend logic for the Big Picture grid, split out of [BigPictureGrid] so it's
 * unit-testable on the JVM without Compose (same rationale as `domain.weeksInGrid` and
 * `viewmodel.bigPictureUiState`). Lives in `ui.bigpicture` rather than `domain` because it depends
 * on [CalendarCase], a viewmodel-layer type.
 *
 * This function: whether an event's tags pass the current tag filter (spec §9).
 */
internal fun isTagVisible(
    eventTags: List<String>,
    visibleTagNames: Set<String>,
    allTagCount: Int,
): Boolean =
    when {
        visibleTagNames.size == allTagCount -> true
        visibleTagNames.isEmpty() -> eventTags.isEmpty()
        else -> eventTags.any { it in visibleTagNames }
    }

/**
 * The persisted form of a "which of [all] are visible" selection: [selected] equal to [all]
 * collapses to `null` ("no filter stored", spec §9's Big Picture filters) so a Case or tag added
 * later is visible by default instead of silently excluded by a stale snapshotted set.
 */
internal fun <T> normalizeVisibleSelection(
    selected: Set<T>,
    all: Set<T>,
): Set<T>? = if (selected == all) null else selected

/** Whether every item of [all] is in [selected] — drives the bulk toggle's "Clear all" label. */
internal fun <T> isAllSelected(
    selected: Set<T>,
    all: Iterable<T>,
): Boolean = all.all { it in selected }

/** The stored Case and Tag selections a Case-filter change leaves behind (spec §9). */
internal data class CaseFilterChange(
    val visibleCaseIds: Set<Long>?,
    val visibleTagNames: Set<String>?,
)

/**
 * Re-scoping the Case filter always resets the Tag filter to "everything in the new scope" — a tag
 * only offered by a now-hidden Case would otherwise leave the Cases and Tags filters ANDing down to
 * nothing (the "empty trap" spec §9 guards against). [selected] is normalized like any other filter.
 */
internal fun caseFilterChange(
    selected: Set<Long>,
    allCaseIds: Set<Long>,
): CaseFilterChange = CaseFilterChange(normalizeVisibleSelection(selected, allCaseIds), visibleTagNames = null)

/**
 * The live selection a stored, possibly-stale [stored] value resolves to against what currently
 * exists in [all]: `null` means "no filter stored" (everything visible); a stored set is
 * intersected with [all] so ids/names for a deleted Case or removed tag drop out harmlessly.
 */
internal fun <T> resolveVisibleSelection(
    stored: Set<T>?,
    all: Set<T>,
): Set<T> = stored?.intersect(all) ?: all

/**
 * Tag names offered by [events] scoped to [visibleCaseIds] — a tag only offered by a currently
 * hidden Case is excluded, so the Cases and Tags filters never silently AND down to nothing
 * (see [BigPictureGrid]'s Tags dialog wiring).
 */
internal fun bigPictureAllTagNames(
    events: List<CalendarEvent>,
    visibleCaseIds: Set<Long>,
): List<String> =
    events
        .filter { it.caseId in visibleCaseIds }
        .flatMap { it.tags }
        .distinct()
        .sorted()

/** Whether [date] is today or earlier — the Big Picture grid never renders future days (spec §9). */
internal fun isPastOrToday(
    date: LocalDate,
    today: LocalDate,
): Boolean = date <= today

/**
 * A day's events, chronological instead of case-grouped: [DayEvent.isAllDay] rows (a multi-day span
 * passing through this day with no start/end boundary of its own) sort first, since they have no
 * time to sort by; everything else sorts by whichever boundary actually falls on this day — the
 * start time, or the end time for [DayEvent.isSpanEnd].
 */
internal fun sortDayEvents(dayEvents: List<DayEvent>): List<DayEvent> {
    val (allDay, timed) = dayEvents.partition { it.isAllDay }
    return allDay.sortedBy { it.event.occurredAt } +
        timed.sortedBy { if (it.isSpanEnd) it.event.endedAt ?: it.event.occurredAt else it.event.occurredAt }
}

/**
 * The local dates [event] covers on the grid: its single day unless a finished event runs past
 * midnight (`occurredAt … endedAt`) or a still-running one does (`occurredAt … today`). Resolves via
 * the event's own captured offset ([CalendarEvent.loggedZone]), not the device's current zone, so a
 * traveler's events land on the day they actually happened; [today] is already the live
 * current-device date, needed only for the still-ongoing branch.
 */
internal fun coveredDates(
    event: CalendarEvent,
    today: LocalDate,
): List<LocalDate> {
    val eventZone = event.loggedZone().asTimeZone()
    val startDate = Instant.fromEpochMilliseconds(event.occurredAt).toLocalDateTime(eventZone).date
    val endDate =
        when {
            event.isOngoing -> today
            event.endedAt != null -> Instant.fromEpochMilliseconds(event.endedAt).toLocalDateTime(eventZone).date
            else -> startDate
        }
    if (endDate <= startDate) return listOf(startDate)
    return generateSequence(startDate) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= endDate }.toList()
}

/**
 * Every event bucketed onto each local day it covers ([coveredDates]), flagged per day
 * ([DayEvent.isSpanStart]/[DayEvent.isSpanEnd]/[DayEvent.isAllDay]) and sorted ([sortDayEvents]) —
 * the single source the day and week detail dialogs both read from (spec §9).
 */
internal fun buildEventsByDay(
    events: List<CalendarEvent>,
    today: LocalDate,
): Map<LocalDate, List<DayEvent>> =
    buildMap<LocalDate, MutableList<DayEvent>> {
        events.forEach { event ->
            val dates = coveredDates(event, today)
            val multiDay = dates.size > 1
            dates.forEachIndexed { index, date ->
                val isStart = multiDay && index == 0
                val isEnd = multiDay && !event.isOngoing && index == dates.lastIndex
                getOrPut(date) { mutableListOf() }
                    .add(
                        DayEvent(
                            event = event,
                            isSpanStart = isStart,
                            isSpanEnd = isEnd,
                            isAllDay = multiDay && !isStart && !isEnd,
                        ),
                    )
            }
        }
    }.mapValues { (_, dayEvents) -> sortDayEvents(dayEvents) }

/**
 * Descending months (current first, earliest last) — the Big Picture grid's rendering order and
 * the Year dialog's list order both derive from this (spec §9). [months] must already be ascending
 * (as built in [BigPictureGrid]).
 */
internal fun monthsNewestFirst(months: List<YearMonth>): List<YearMonth> = months.asReversed()

/** Whether the Year trigger chip should render — more than one year of data, same "a one-value
 * filter is dead weight" reasoning as the existing Tags chip's `allTagNames.isNotEmpty()`. */
internal fun bigPictureYearFilterVisible(
    earliestMonth: YearMonth,
    currentMonth: YearMonth,
): Boolean = earliestMonth.year != currentMonth.year

/** Years present in [months], current year first, oldest last — drives the Year dialog's list. */
internal fun bigPictureYearOptions(months: List<YearMonth>): List<Int> = months.map { it.year }.distinct().sortedDescending()

/** Narrows [months] (ascending) to [year]; a null [year] means "All years". */
internal fun filterMonthsByYear(
    months: List<YearMonth>,
    year: Int?,
): List<YearMonth> = if (year == null) months else months.filter { it.year == year }

/** What the combined legend row shows for the Case dimension. */
internal sealed interface CaseLegendState {
    data object AllSelected : CaseLegendState

    data class Some(
        val cases: List<CalendarCase>,
    ) : CaseLegendState

    data object NoneSelected : CaseLegendState
}

/** What the combined legend row shows for the tag dimension. */
internal sealed interface TagLegendState {
    data object AllSelected : TagLegendState

    data class Some(
        val tags: List<String>,
    ) : TagLegendState

    data object UntaggedOnly : TagLegendState
}

internal fun bigPictureCaseLegend(
    allCases: List<CalendarCase>,
    selectedCaseIds: Set<Long>,
): CaseLegendState =
    when {
        selectedCaseIds.isEmpty() -> CaseLegendState.NoneSelected
        selectedCaseIds.size == allCases.size -> CaseLegendState.AllSelected
        else -> CaseLegendState.Some(allCases.filter { it.id in selectedCaseIds })
    }

/** [allTagNames] empty (no event carries any tag) is treated as vacuously [TagLegendState.AllSelected] — there's nothing to filter, so it never renders as "Untagged only". */
internal fun bigPictureTagLegend(
    allTagNames: List<String>,
    selectedTagNames: Set<String>,
): TagLegendState =
    when {
        allTagNames.isEmpty() || selectedTagNames.size == allTagNames.size -> TagLegendState.AllSelected
        selectedTagNames.isEmpty() -> TagLegendState.UntaggedOnly
        else -> TagLegendState.Some(allTagNames.filter { it in selectedTagNames })
    }

// A rough character budget for the legend row's generous multi-line space, not an exact
// rendered-width measurement (that would need real text measurement, and would need to be redone
// per theme given how much chip chrome -- halo rings, border widths -- varies between them). A
// typical phone's content width after the screen's own padding holds somewhere around 35-40
// labelSmall characters per line; three lines and a little slack land near 120. CHROME_CHARS
// approximates each chip's own padding/border/gap (not part of the label text) as
// character-equivalent width, so a chip with a short label still "costs" something even if its
// label alone would fit many times over.
internal const val LEGEND_VISIBLE_CHAR_BUDGET = 120
internal const val LEGEND_CHIP_CHROME_CHARS = 5

/**
 * Whether the legend row's itemized Case/Tag chips fit a generous multi-line budget. A plain
 * item-count cap can't track actual rendered width when case/tag names range from a couple of
 * characters to a full sentence, and a partial list plus a "+N" remainder reads as a disabled
 * button and can't say which group the hidden items came from once one group collapses to a
 * single label -- so this is deliberately all-or-nothing: the legend either lists every selected
 * Case/Tag, or (when the caller finds this false) shows nothing at all, same as it already does
 * when every Case and Tag is selected. The trigger chips above still state the counts either way.
 * [caseLabels]/[tagLabels] are the exact text each chip would render (Case icon+name, Tag name).
 */
internal fun bigPictureLegendFits(
    caseLabels: List<String>,
    tagLabels: List<String>,
    budget: Int = LEGEND_VISIBLE_CHAR_BUDGET,
): Boolean {
    val totalCost = (caseLabels + tagLabels).sumOf { it.length + LEGEND_CHIP_CHROME_CHARS }
    return totalCost <= budget
}
