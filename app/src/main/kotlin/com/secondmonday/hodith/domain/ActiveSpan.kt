package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity

/**
 * The instant [event]'s active span (spec §9) ends, given the Case's [tracksDuration] flag: its
 * stored `endedAt`; or [now] for a still-running event ([isStillRunning]: no `endedAt` at all, or
 * an `endedAt` still in the future); or its own `occurredAt` for a point event and for every event
 * on a Case that doesn't track duration — whatever `endedAt` is stored, the span collapses to a
 * point there and the stored value is left untouched.
 *
 * Compare the result against a window start to ask "was this event active anywhere in that
 * window", or feed it to [datesCovered] / [spansMultipleDays]. Shared by Home's row counts, the
 * Insights tab, and the verdict engine's window filter so every day-counting surface reads the
 * span the same way.
 */
internal fun activeSpanEnd(
    event: EventEntity,
    tracksDuration: Boolean,
    now: Long,
): Long = activeSpanEnd(event.occurredAt, event.endedAt, tracksDuration, now)

/**
 * Primitive-parameter core of [activeSpanEnd] — the same span-end rule from an event's raw
 * `occurredAt` / `endedAt` rather than an [EventEntity]. Home's row counts read a lean
 * `caseId / occurredAt / endedAt / tracksDuration` projection (`CaseEventSpan`) rather than full
 * events, so the rule lives here in one place and stays JVM-tested.
 */
internal fun activeSpanEnd(
    occurredAt: Long,
    endedAt: Long?,
    tracksDuration: Boolean,
    now: Long,
): Long =
    when {
        !tracksDuration -> occurredAt
        isStillRunning(endedAt, tracksDuration, now) -> now
        else -> endedAt ?: occurredAt
    }

/**
 * Whether an event hasn't finished yet as of [now], given the Case's [tracksDuration] flag: no
 * `endedAt` at all, or a computed `endedAt` still in the future — a stated/expected duration
 * ([com.secondmonday.hodith.viewmodel.computeEndedAt] deliberately doesn't clamp this to `now`)
 * that hasn't actually elapsed yet. A Case that doesn't track duration, and any already-finished
 * event, are not still running. The single source for what was previously several separate
 * `endedAt == null` / `endedAt > now` checks spread across the domain/viewmodel/UI layers.
 */
internal fun isStillRunning(
    event: EventEntity,
    tracksDuration: Boolean,
    now: Long,
): Boolean = isStillRunning(event.endedAt, tracksDuration, now)

internal fun isStillRunning(
    endedAt: Long?,
    tracksDuration: Boolean,
    now: Long,
): Boolean = tracksDuration && (endedAt == null || endedAt > now)
