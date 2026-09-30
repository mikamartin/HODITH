package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.NotificationEntity

/**
 * Spec §11: both Notification kinds are edge-triggered — fire once when their condition first
 * becomes true, then stay quiet (re-armed only once the condition stops being true) rather than
 * firing on every subsequent evaluation. That's a single state machine; [evaluateOften] and
 * [evaluateQuiet] only differ in how they compute [conditionMet]. Never mutates [notification] —
 * callers persist [NotificationDecision.newArmed]/[NotificationDecision.newLastFiredAt] back to it.
 */
fun evaluateNotification(
    notification: NotificationEntity,
    conditionMet: Boolean,
    now: Long,
): NotificationDecision =
    when {
        !notification.enabled ->
            NotificationDecision(shouldFire = false, newArmed = notification.armed, newLastFiredAt = notification.lastFiredAt)
        notification.armed && conditionMet ->
            NotificationDecision(shouldFire = true, newArmed = false, newLastFiredAt = now)
        !notification.armed && !conditionMet ->
            NotificationDecision(shouldFire = false, newArmed = true, newLastFiredAt = notification.lastFiredAt)
        else ->
            NotificationDecision(shouldFire = false, newArmed = notification.armed, newLastFiredAt = notification.lastFiredAt)
    }

/** Rolling-window start for `OFTEN` (spec §11): shared by [evaluateOften]'s own count and `NotificationEvaluator`'s fetch, so the two can't silently diverge. */
fun oftenWindowStart(
    now: Long,
    windowDays: Int?,
): Long = now - (windowDays ?: 0) * MILLIS_PER_DAY

/** `OFTEN`'s condition (spec §11): the rolling [NotificationEntity.windowDays]-day event count has reached [NotificationEntity.threshold]. */
fun evaluateOften(
    notification: NotificationEntity,
    events: List<EventEntity>,
    now: Long,
): NotificationDecision {
    val windowStart = oftenWindowStart(now, notification.windowDays)
    val windowCount = events.count { it.occurredAt in windowStart..now }
    return evaluateNotification(notification, conditionMet = windowCount >= notification.threshold, now = now)
}

/** `QUIET`'s condition (spec §11): days since the latest of last event / case creation has reached [NotificationEntity.threshold]. */
fun evaluateQuiet(
    notification: NotificationEntity,
    mostRecentEventAt: Long?,
    caseCreatedAt: Long,
    now: Long,
): NotificationDecision {
    // anchor-vs-now: "now" has no captured offset by definition, so daysBetween's default
    // device-current-zone resolution is already correct here — see computeGapStats's doc comment
    // for the general per-event-vs-now rule this follows.
    val silentDays = daysBetween(mostRecentEventAt ?: caseCreatedAt, now)
    return evaluateNotification(notification, conditionMet = silentDays >= notification.threshold, now = now)
}
