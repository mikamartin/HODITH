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
 * mirrors [homeCaseRows]/[ongoingEventIn]'s pure-mapping pattern. The mockup's "creating" toggle
 * (the Hunch-creation sheet) isn't represented here: it's a UI overlay that can sit on top of any
 * of these three states, not a data state of its own.
 */
sealed interface HunchTabState {
    /**
     * No currently-active Hunch. [showNudge] gates the "got a feeling about this one?" card;
     * [history] carries any previously-resolved Hunches, shown below regardless of [showNudge].
     */
    data class NoActiveHunch(
        val showNudge: Boolean,
        val history: List<HunchHistoryEntry>,
    ) : HunchTabState

    /** Active Hunch, but [VerdictResult.tier] is [com.secondmonday.hodith.domain.ConfidenceTier.NO_VERDICT]. */
    data class EarlyDays(
        val hunch: HunchEntity,
        val result: VerdictResult,
    ) : HunchTabState

    /** Active Hunch with a preliminary or confident verdict — resolvable. */
    data class Verdict(
        val hunch: HunchEntity,
        val result: VerdictResult,
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
    if (activeHunch == null) {
        val showNudge = !case.hunchNudgeDismissed && events.size >= HUNCH_NUDGE_EVENT_THRESHOLD
        return HunchTabState.NoActiveHunch(
            showNudge = showNudge,
            history = history.mapNotNull { it.toHistoryEntry(events, case.createdAt) },
        )
    }

    val result = computeVerdict(activeHunch, events, case.createdAt, now)
    return if (result.comparisonBand == null) {
        HunchTabState.EarlyDays(activeHunch, result)
    } else {
        HunchTabState.Verdict(activeHunch, result)
    }
}

private fun HunchEntity.toHistoryEntry(
    events: List<EventEntity>,
    caseCreatedAt: Long,
): HunchHistoryEntry? {
    val resolvedAt = resolvedAt ?: return null
    val eventsAtResolution = events.filter { it.occurredAt <= resolvedAt }
    val result = computeVerdict(this, eventsAtResolution, caseCreatedAt, now = resolvedAt)
    // A hunch resolved before it ever reached a verdict (comparisonBand == null) has nothing
    // meaningful to show in history — the app's own "Resolve Hunch" button only appears once a
    // band exists, so this only guards against manually-edited or imported data.
    if (result.comparisonBand == null) return null
    return HunchHistoryEntry(this, result)
}

/**
 * How far toward the Preliminary bar an active Hunch's Early Days card is — whichever of the
 * event-count or day-count requirement is furthest behind, since both must clear together
 * (spec §8). Drives the progress bar's fill fraction.
 */
internal fun hunchProgressFraction(
    eventCount: Int,
    windowDays: Long,
): Float {
    val eventFraction = eventCount.toFloat() / PRELIMINARY_MIN_EVENTS
    val dayFraction = windowDays.toFloat() / PRELIMINARY_MIN_DAYS
    return minOf(eventFraction, dayFraction).coerceIn(0f, 1f)
}
