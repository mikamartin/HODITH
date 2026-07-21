package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

/**
 * Spec §6: an ongoing event older than this surfaces the "still going, or forgot to stop it?"
 * prompt (`isStaleOngoing`). Named rather than inlined per CLAUDE.md's product-constants rule.
 */
internal const val STALE_ONGOING_THRESHOLD_MILLIS = MILLIS_PER_DAY

/**
 * The Case's open-ended event, if any (spec §6: "one ongoing event per Case"). Only
 * `START_STOP` cases can be ongoing — `NONE`/`MANUAL` events always carry a real `endedAt` (or
 * no duration at all), so a null `endedAt` there would be a data bug, not an ongoing state, and
 * this deliberately doesn't surface it as one. Shared by Home's and Case Detail's mapping so
 * that rule lives in exactly one place.
 */
internal fun ongoingEventIn(
    case: CaseEntity,
    events: List<EventEntity>,
): EventEntity? {
    if (case.durationMode != DurationMode.START_STOP) return null
    return events.find { it.endedAt == null }
}

/**
 * Whether [event] should show the 24h-stale prompt at [now]. Re-arms after another
 * [STALE_ONGOING_THRESHOLD_MILLIS] once dismissed, rather than staying silenced forever, so a
 * genuinely-forgotten event doesn't go silent indefinitely (spec §6's "gentle prompt" is meant
 * to be periodic nudging, not a one-time notice).
 */
internal fun isStaleOngoing(
    event: EventEntity,
    now: Long,
): Boolean {
    if (now - event.occurredAt < STALE_ONGOING_THRESHOLD_MILLIS) return false
    val dismissedAt = event.staleNudgeDismissedAt ?: return true
    return now - dismissedAt >= STALE_ONGOING_THRESHOLD_MILLIS
}

/**
 * Renders the time since [startMillis] as of [nowMillis] for the ongoing indicator: minutes
 * alone under an hour, hours+minutes under a day, days+hours from a day on — matching how long
 * an ongoing event is actually likely to run (a forgotten Start can sit for days).
 */
internal fun formatElapsedDuration(
    startMillis: Long,
    nowMillis: Long,
): String {
    val elapsed = (nowMillis - startMillis).coerceAtLeast(0L)
    val totalMinutes = elapsed / MILLIS_PER_MINUTE
    return when {
        totalMinutes < 60 -> "${totalMinutes}m"
        totalMinutes < 24 * 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        else -> "${totalMinutes / (24 * 60)}d ${(totalMinutes % (24 * 60)) / 60}h"
    }
}
