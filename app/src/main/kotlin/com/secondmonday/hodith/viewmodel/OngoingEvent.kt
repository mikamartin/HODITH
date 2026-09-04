package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE

/**
 * Every open-ended event on the Case, earliest-started first. Only `START_STOP` cases can be
 * ongoing — `NONE`/`MANUAL` events always carry a real `endedAt` (or no duration at all), so a
 * null `endedAt` there would be a data bug, not an ongoing state, and this deliberately doesn't
 * surface it as one. Spec §6's "one ongoing event per Case" is about the Start affordance
 * (Start becomes Stop, so a second one can't be started from that button) — retro-logging and a
 * fast stop/restart both legitimately leave more than one event open at once. Shared by Home's
 * and Case Detail's mapping so that rule lives in exactly one place.
 */
internal fun ongoingEventsIn(
    case: CaseEntity,
    events: List<EventEntity>,
): List<EventEntity> {
    if (case.durationMode != DurationMode.START_STOP) return emptyList()
    return events.filter { it.endedAt == null }.sortedBy { it.occurredAt }
}

/**
 * The Case's earliest-started open event, if any — the one whose elapsed time the summary
 * surfaces show when exactly one is running. Deterministic where [List.find] over Room's
 * unordered relation was not.
 */
internal fun ongoingEventIn(
    case: CaseEntity,
    events: List<EventEntity>,
): EventEntity? = ongoingEventsIn(case, events).firstOrNull()

/**
 * The instant [event]'s active span (spec §9) ends, given the Case's current [durationMode]:
 * its stored `endedAt`; or [now] for a still-running `START_STOP` event; or its own `occurredAt`
 * for a point event and for every event on a Case that no longer tracks duration — whatever
 * `endedAt` is stored, the span collapses to a point there and the stored value is left untouched.
 *
 * Compare the result against a window start to ask "was this event active anywhere in that
 * window", or feed it to [com.secondmonday.hodith.domain.datesCovered] /
 * [com.secondmonday.hodith.domain.spansMultipleDays]. Shared by Home's row counts and the
 * Insights tab so both read the span the same way.
 */
internal fun activeSpanEnd(
    event: EventEntity,
    durationMode: DurationMode,
    now: Long,
): Long =
    if (!durationMode.tracksDuration) {
        event.occurredAt
    } else {
        event.endedAt ?: if (durationMode == DurationMode.START_STOP) now else event.occurredAt
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
    return formatMinutesDuration(elapsed / MILLIS_PER_MINUTE)
}

/** Shared by [formatElapsedDuration] and the Insights tab's duration stats — same minutes-to-"Xh Ym" rendering either way. */
internal fun formatMinutesDuration(totalMinutes: Long): String =
    when {
        totalMinutes < 60 -> "${totalMinutes}m"
        totalMinutes < 24 * 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        else -> "${totalMinutes / (24 * 60)}d ${(totalMinutes % (24 * 60)) / 60}h"
    }
