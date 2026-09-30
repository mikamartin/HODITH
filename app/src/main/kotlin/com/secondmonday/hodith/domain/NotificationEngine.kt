package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.tracksDuration

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

/**
 * Builds the [Expectation]/filtered-events pair that both `OFTEN`'s firing check ([evaluateOften])
 * and a later UI comparison line need to compute their answer from. Pulled out on its own so those
 * two consumers can't independently drift on windowing or intensity filtering — a UI line built
 * from slightly different inputs than the engine that actually fires would be misleading rather
 * than merely inconsistent. [allEvents] should be unbounded (every event that could possibly be
 * relevant to the Case), not pre-windowed: [computeVerdict] does its own span-overlap window
 * filtering and needs to see events whose span started before the window but still reaches into it.
 */
fun expectationInputsFor(
    notification: NotificationEntity,
    allEvents: List<EventEntity>,
    now: Long,
): Pair<Expectation, List<EventEntity>> {
    val windowStart = oftenWindowStart(now, notification.windowDays)
    val expectation =
        Expectation(
            count = notification.threshold,
            per = notification.expectedPer,
            metric = notification.metric,
            windowStart = windowStart,
        )
    val minIntensity = notification.minIntensity
    val filteredEvents =
        if (minIntensity == null) {
            allEvents
        } else {
            allEvents.filter { it.intensity != null && it.intensity >= minIntensity }
        }
    return expectation to filteredEvents
}

/**
 * `OFTEN`'s condition (spec §11): the observed rate over the notification's lookback window has
 * reached the expected rate. Firing is never tier-gated — [computeVerdict]'s `ConfidenceTier` and
 * `comparisonBand` play no role in this boolean decision, only in what a later UI displays; a
 * `NO_VERDICT` case can still fire if its raw observed-vs-expected rate clears the bar.
 */
fun evaluateOften(
    notification: NotificationEntity,
    allEvents: List<EventEntity>,
    now: Long,
    durationMode: DurationMode,
): NotificationDecision {
    val (expectation, filteredEvents) = expectationInputsFor(notification, allEvents, now)
    val result = computeVerdict(expectation, filteredEvents, now, durationMode)
    return evaluateNotification(notification, conditionMet = result.observedRate >= result.expectedRate, now = now)
}

/**
 * In-memory counterpart of `NotificationEvaluator.silenceAnchorFor`'s own doc comment
 * (notification/NotificationEvaluator.kt) — the same rule (a running `START_STOP` event reads as
 * "now"; a Case that no longer tracks duration anchors on the latest `occurredAt`; otherwise the
 * latest of each event's `endedAt` or its own `occurredAt` when no duration was recorded — the same
 * `MAX(IFNULL(endedAt, occurredAt))` [EventDao.getLatestEventEndForCase] runs in SQL), restated here
 * rather than shared because the bell tab's Now line already holds the Case's full event list in
 * memory and has no reason to make three separate repository suspend calls to re-derive it the way
 * the evaluator does. These two must be kept in sync by hand — a rule change in one needs the same
 * change made in the other. Null with no events at all, matching [evaluateQuiet]'s own
 * `mostRecentEventAt ?: caseCreatedAt` fallback — the caller falls back to the Case's `createdAt`.
 */
fun silenceAnchorForEvents(
    events: List<EventEntity>,
    durationMode: DurationMode,
    now: Long,
): Long? {
    if (events.isEmpty()) return null
    if (durationMode == DurationMode.START_STOP && events.any { it.endedAt == null }) return now
    if (!durationMode.tracksDuration) return events.maxOf { it.occurredAt }
    return events.maxOf { it.endedAt ?: it.occurredAt }
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
