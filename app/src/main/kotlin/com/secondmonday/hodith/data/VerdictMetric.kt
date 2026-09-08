package com.secondmonday.hodith.data

/**
 * Spec §8: how a Hunch's verdict counts observation. Chosen once at Hunch creation, never
 * re-asked. [OCCURRENCE_COUNT] is every Case's default and the only option a `NONE`-duration
 * Case sees; [DAYS_ACTIVE] is opt-in and only offered when the Case already tracks duration
 * (`DurationMode.tracksDuration`), where a raw event tally undersells how much of the time an
 * event with long, overlapping spans was actually happening.
 */
enum class VerdictMetric {
    OCCURRENCE_COUNT,
    DAYS_ACTIVE,
}
