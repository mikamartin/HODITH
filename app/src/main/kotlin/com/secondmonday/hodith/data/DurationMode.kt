package com.secondmonday.hodith.data

enum class DurationMode {
    NONE,
    MANUAL,
    START_STOP,
}

/**
 * Whether a Case in this mode still tracks how long events last. `NONE` doesn't: spec §9 —
 * its events are points at `occurredAt` on every day-counting surface, whatever `endedAt` is
 * stored (untouched, so switching the mode back on restores the spans). `MANUAL` and
 * `START_STOP` both do.
 */
internal val DurationMode.tracksDuration: Boolean
    get() = this != DurationMode.NONE
