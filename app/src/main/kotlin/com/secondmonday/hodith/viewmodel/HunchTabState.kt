package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.domain.HUNCH_NUDGE_EVENT_THRESHOLD
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_DAYS
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_EVENTS
import com.secondmonday.hodith.domain.VerdictResult
import com.secondmonday.hodith.domain.computeVerdict
import com.secondmonday.hodith.domain.resolvedVerdictSnapshotOrNull

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
 * A resolved Hunch's verdict, frozen at the moment it was resolved. An active Hunch's verdict is
 * never stored (spec §8) and is always recomputed live — but a *resolved* Hunch's verdict is
 * snapshotted onto [HunchEntity] at resolution time (`CaseDetailViewModel.resolveHunch`), so a
 * history entry is read straight from those stored fields rather than recomputed. That's what
 * actually keeps it frozen: recomputing from live Events, even filtered to `occurredAt <=
 * resolvedAt`, would still drift if an old Event inside that window were later edited or deleted.
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
    val result =
        resolvedVerdictSnapshotOrNull() ?: run {
            // Defensive fallback for the brief window right after an upgrade, before
            // HodithApplication's one-time backfill has snapshotted this row.
            val eventsAtResolution = events.filter { it.occurredAt <= resolvedAt }
            computeVerdict(this, eventsAtResolution, case.createdAt, now = resolvedAt, case.durationMode)
        }
    // A hunch resolved before it ever reached a verdict (comparisonBand == null) has nothing
    // meaningful to show in history — the app's own "Resolve Hunch" button only appears once a
    // band exists, so this only guards against manually-edited/imported data.
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
