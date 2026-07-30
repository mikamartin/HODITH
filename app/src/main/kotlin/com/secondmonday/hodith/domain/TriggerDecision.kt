package com.secondmonday.hodith.domain

/**
 * Result of evaluating a Trigger against current data (spec §11). Never persisted itself —
 * [newArmed] and [newLastFiredAt] are what the caller writes back to the `TriggerEntity` so the
 * next evaluation picks up the right state.
 */
data class TriggerDecision(
    val shouldFire: Boolean,
    val newArmed: Boolean,
    val newLastFiredAt: Long?,
)
