package com.secondmonday.hodith.ui.bigpicture

import com.secondmonday.hodith.viewmodel.CalendarCase
import java.time.LocalDate

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

/** Whether [date] is today or earlier — the Big Picture grid never renders future days (spec §9). */
internal fun isPastOrToday(
    date: LocalDate,
    today: LocalDate,
): Boolean = !date.isAfter(today)

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
