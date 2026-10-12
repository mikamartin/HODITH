package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.tracksDuration

/**
 * The instant [event]'s active span (spec §9) ends, given the Case's current [durationMode]:
 * its stored `endedAt`; or [now] for a still-running event ([isStillRunning]: a `START_STOP`
 * event with no `endedAt`, or a `MANUAL` event whose `endedAt` is still in the future); or its
 * own `occurredAt` for a point event and for every event on a Case that no longer tracks duration
 * — whatever `endedAt` is stored, the span collapses to a point there and the stored value is
 * left untouched.
 *
 * Compare the result against a window start to ask "was this event active anywhere in that
 * window", or feed it to [datesCovered] / [spansMultipleDays]. Shared by Home's row counts, the
 * Insights tab, and the verdict engine's window filter so every day-counting surface reads the
 * span the same way.
 */
internal fun activeSpanEnd(
    event: EventEntity,
    durationMode: DurationMode,
    now: Long,
): Long = activeSpanEnd(event.occurredAt, event.endedAt, durationMode, now)

/**
 * Primitive-parameter core of [activeSpanEnd] — the same span-end rule from an event's raw
 * `occurredAt` / `endedAt` rather than an [EventEntity]. Home's row counts read a lean
 * `caseId / occurredAt / endedAt / durationMode` projection (`CaseEventSpan`) rather than full
 * events, so the rule lives here in one place and stays JVM-tested.
 */
internal fun activeSpanEnd(
    occurredAt: Long,
    endedAt: Long?,
    durationMode: DurationMode,
    now: Long,
): Long =
    when {
        !durationMode.tracksDuration -> occurredAt
        isStillRunning(endedAt, durationMode, now) -> now
        else -> endedAt ?: occurredAt
    }

/**
 * Whether an event hasn't finished yet as of [now], given the Case's [durationMode]: a
 * `START_STOP` event with no `endedAt` at all, or a `MANUAL` event whose computed `endedAt` is
 * still in the future — a stated/expected duration
 * ([com.secondmonday.hodith.viewmodel.computeEndedAt] deliberately doesn't clamp this to `now`)
 * that hasn't actually elapsed yet. `NONE` events and
 * any already-finished `START_STOP`/`MANUAL` event are not still running. The single source for
 * what was previously six separate `durationMode == START_STOP && endedAt == null` checks spread
 * across the domain/viewmodel/UI layers — those only ever covered the `START_STOP` half of this.
 */
internal fun isStillRunning(
    event: EventEntity,
    durationMode: DurationMode,
    now: Long,
): Boolean = isStillRunning(event.endedAt, durationMode, now)

internal fun isStillRunning(
    endedAt: Long?,
    durationMode: DurationMode,
    now: Long,
): Boolean =
    when (durationMode) {
        DurationMode.START_STOP -> endedAt == null
        DurationMode.MANUAL -> endedAt != null && endedAt > now
        DurationMode.NONE -> false
    }
