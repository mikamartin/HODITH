package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.tracksDuration

/**
 * The instant [event]'s active span (spec §9) ends, given the Case's current [durationMode]:
 * its stored `endedAt`; or [now] for a still-running `START_STOP` event; or its own `occurredAt`
 * for a point event and for every event on a Case that no longer tracks duration — whatever
 * `endedAt` is stored, the span collapses to a point there and the stored value is left untouched.
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
): Long =
    if (!durationMode.tracksDuration) {
        event.occurredAt
    } else {
        event.endedAt ?: if (durationMode == DurationMode.START_STOP) now else event.occurredAt
    }
