package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.TriggerEntity

/**
 * Spec §11: both Trigger kinds are edge-triggered — fire once when their condition first becomes
 * true, then stay quiet (re-armed only once the condition stops being true) rather than firing on
 * every subsequent evaluation. That's a single state machine; [evaluateAtLeast] and
 * [evaluateSilentFor] only differ in how they compute [conditionMet]. Never mutates [trigger] —
 * callers persist [TriggerDecision.newArmed]/[TriggerDecision.newLastFiredAt] back to it.
 */
internal fun evaluateTrigger(
    trigger: TriggerEntity,
    conditionMet: Boolean,
    now: Long,
): TriggerDecision =
    when {
        !trigger.enabled ->
            TriggerDecision(shouldFire = false, newArmed = trigger.armed, newLastFiredAt = trigger.lastFiredAt)
        trigger.armed && conditionMet ->
            TriggerDecision(shouldFire = true, newArmed = false, newLastFiredAt = now)
        !trigger.armed && !conditionMet ->
            TriggerDecision(shouldFire = false, newArmed = true, newLastFiredAt = trigger.lastFiredAt)
        else ->
            TriggerDecision(shouldFire = false, newArmed = trigger.armed, newLastFiredAt = trigger.lastFiredAt)
    }

/** `AT_LEAST`'s condition (spec §11): the rolling [TriggerEntity.windowDays]-day event count has reached [TriggerEntity.threshold]. */
internal fun evaluateAtLeast(
    trigger: TriggerEntity,
    events: List<EventEntity>,
    now: Long,
): TriggerDecision {
    val windowStart = now - (trigger.windowDays ?: 0) * MILLIS_PER_DAY
    val windowCount = events.count { it.occurredAt in windowStart..now }
    return evaluateTrigger(trigger, conditionMet = windowCount >= trigger.threshold, now = now)
}

/** `SILENT_FOR`'s condition (spec §11): days since the latest of last event / case creation has reached [TriggerEntity.threshold]. */
internal fun evaluateSilentFor(
    trigger: TriggerEntity,
    mostRecentEventAt: Long?,
    caseCreatedAt: Long,
    now: Long,
): TriggerDecision {
    val silentDays = daysBetween(mostRecentEventAt ?: caseCreatedAt, now)
    return evaluateTrigger(trigger, conditionMet = silentDays >= trigger.threshold, now = now)
}
