package com.secondmonday.hodith.data

/**
 * Spec §8: which slice of a Case's history a Hunch's verdict is measured over. The window *end*
 * is always `now` in every mode (verdicts recompute fresh, never freeze); only the start moves.
 * Chosen once at Hunch creation.
 *
 * - [SINCE_START] — from the earlier of the Case's creation or its first event (today's only
 *   behaviour, and the default).
 * - [LAST_3_MONTHS] — a rolling fixed 90-day window ending at `now`, sliding forward over time.
 * - [CUSTOM] — a user-picked fixed start date (stored in [HunchEntity.windowStartDate]), floored
 *   at the Case's own `createdAt`.
 */
enum class ObservationWindow {
    SINCE_START,
    LAST_3_MONTHS,
    CUSTOM,
}
