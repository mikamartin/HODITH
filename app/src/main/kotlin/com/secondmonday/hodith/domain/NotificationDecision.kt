package com.secondmonday.hodith.domain

/**
 * Result of evaluating a Notification against current data (spec §11). Never persisted itself —
 * [newArmed] and [newLastFiredAt] are what the caller writes back to the `NotificationEntity` so
 * the next evaluation picks up the right state.
 */
data class NotificationDecision(
    val shouldFire: Boolean,
    val newArmed: Boolean,
    val newLastFiredAt: Long?,
)
