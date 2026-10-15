package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import com.secondmonday.hodith.domain.isStillRunning

/**
 * Every still-running event on the Case (per [isStillRunning]: no `endedAt` at all, or an
 * `endedAt` still in the future), earliest-started first. Only a Case that tracks duration can be
 * ongoing — a Case that doesn't has no running-event concept at all, and this deliberately doesn't
 * surface one. Spec §6's "one ongoing event per Case" is about the Start affordance (Start becomes
 * Stop, so a second one can't be started from that button) — retro-logging and a fast stop/restart
 * both legitimately leave more than one event open at once. Shared by Home's and Case Detail's
 * mapping so that rule lives in exactly one place.
 */
internal fun ongoingEventsIn(
    case: CaseEntity,
    events: List<EventEntity>,
    now: Long,
): List<EventEntity> {
    if (!case.tracksDuration) return emptyList()
    return events.filter { isStillRunning(it, case.tracksDuration, now) }.sortedBy { it.occurredAt }
}

/**
 * The Case's earliest-started open event, if any — the one whose elapsed time the summary
 * surfaces show when exactly one is running. Deterministic where [List.find] over Room's
 * unordered relation was not.
 */
internal fun ongoingEventIn(
    case: CaseEntity,
    events: List<EventEntity>,
    now: Long,
): EventEntity? = ongoingEventsIn(case, events, now).firstOrNull()

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
    return formatMinutesDuration(elapsed / MILLIS_PER_MINUTE)
}

/** Shared by [formatElapsedDuration] and the Insights tab's duration stats — same minutes-to-"Xh Ym" rendering either way. */
internal fun formatMinutesDuration(totalMinutes: Long): String =
    when {
        totalMinutes < 60 -> "${totalMinutes}m"
        totalMinutes < 24 * 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        else -> "${totalMinutes / (24 * 60)}d ${(totalMinutes % (24 * 60)) / 60}h"
    }
