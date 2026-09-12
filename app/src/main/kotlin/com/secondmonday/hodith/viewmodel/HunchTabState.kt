package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.domain.HUNCH_NUDGE_EVENT_THRESHOLD
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_DAYS
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_EVENTS
import com.secondmonday.hodith.domain.VerdictResult
import com.secondmonday.hodith.domain.computeVerdict

/**
 * What the Case Detail Hunch tab renders (spec §7), derived fresh from raw data on every read —
 * mirrors [homeCaseRows]/[ongoingEventIn]'s pure-mapping pattern. The "creating" state (the
 * Hunch-creation sheet) isn't represented here: it's a UI overlay that can sit on top of any
 * of these three states, not a data state of its own.
 *
 * [history] carries any previously-resolved Hunches and is present on every state, not only
 * [NoActiveHunch] — an active Hunch never hides the record of past ones.
 */
sealed interface HunchTabState {
    val history: List<HunchHistoryEntry>

    /** No currently-active Hunch. [showNudge] gates the "got a feeling about this one?" card. */
    data class NoActiveHunch(
        val showNudge: Boolean,
        override val history: List<HunchHistoryEntry>,
    ) : HunchTabState

    /** Active Hunch, but [VerdictResult.tier] is [com.secondmonday.hodith.domain.ConfidenceTier.NO_VERDICT]. */
    data class EarlyDays(
        val hunch: HunchEntity,
        val result: VerdictResult,
        override val history: List<HunchHistoryEntry>,
    ) : HunchTabState

    /** Active Hunch with a preliminary or confident verdict — resolvable. */
    data class Verdict(
        val hunch: HunchEntity,
        val result: VerdictResult,
        override val history: List<HunchHistoryEntry>,
    ) : HunchTabState
}

/**
 * A resolved Hunch's verdict, frozen at the moment it was resolved. Verdicts are never stored
 * (spec §8), so a history entry is reconstructed by recomputing [computeVerdict] as of
 * [HunchEntity.resolvedAt] over only the events that existed by then — recomputing it against
 * today's events/`now` would silently change a past verdict as new events keep arriving.
 */
data class HunchHistoryEntry(
    val hunch: HunchEntity,
    val result: VerdictResult,
)

internal fun hunchTabState(
    case: CaseEntity,
    activeHunch: HunchEntity?,
    events: List<EventEntity>,
    history: List<HunchEntity>,
    now: Long,
): HunchTabState {
    val historyEntries = history.mapNotNull { it.toHistoryEntry(events, case) }

    if (activeHunch == null) {
        val showNudge = events.size >= HUNCH_NUDGE_EVENT_THRESHOLD
        return HunchTabState.NoActiveHunch(showNudge = showNudge, history = historyEntries)
    }

    val result = computeVerdict(activeHunch, events, case.createdAt, now, case.durationMode)
    return if (result.comparisonBand == null) {
        HunchTabState.EarlyDays(activeHunch, result, historyEntries)
    } else {
        HunchTabState.Verdict(activeHunch, result, historyEntries)
    }
}

private fun HunchEntity.toHistoryEntry(
    events: List<EventEntity>,
    case: CaseEntity,
): HunchHistoryEntry? {
    val resolvedAt = resolvedAt ?: return null
    val eventsAtResolution = events.filter { it.occurredAt <= resolvedAt }
    // now = resolvedAt keeps a resolved Hunch's verdict frozen — a rolling window is measured as
    // of the resolution instant, not the live clock, so a history entry never drifts.
    val result = computeVerdict(this, eventsAtResolution, case.createdAt, now = resolvedAt, case.durationMode)
    // A hunch resolved before it ever reached a verdict (comparisonBand == null) has nothing
    // meaningful to show in history — the app's own "Resolve Hunch" button only appears once a
    // band exists, so this only guards against manually-edited or imported data.
    if (result.comparisonBand == null) return null
    return HunchHistoryEntry(this, result)
}

/**
 * How far toward the Preliminary bar an active Hunch's Early Days card is — whichever of the
 * observation-count or window-length requirement is furthest behind, since both must clear
 * together (spec §8). Drives the progress bar's fill fraction. [observationCount] is the event
 * count for an occurrence-count Hunch and the active-day count for a days-active one.
 */
internal fun hunchProgressFraction(
    observationCount: Int,
    windowDays: Long,
): Float {
    val countFraction = observationCount.toFloat() / PRELIMINARY_MIN_EVENTS
    val dayFraction = windowDays.toFloat() / PRELIMINARY_MIN_DAYS
    return minOf(countFraction, dayFraction).coerceIn(0f, 1f)
}
