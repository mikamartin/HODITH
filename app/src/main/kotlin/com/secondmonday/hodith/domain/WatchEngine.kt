package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.tracksDuration

/**
 * Spec §11: both Watch kinds are edge-triggered — fire once when their condition first
 * becomes true, then stay quiet (re-armed only once the condition stops being true) rather than
 * firing on every subsequent evaluation. That's a single state machine; [evaluateOften] and
 * [evaluateQuiet] only differ in how they compute [conditionMet]. Never mutates [watch] —
 * callers persist [WatchDecision.newArmed]/[WatchDecision.newLastFiredAt] back to it.
 */
fun evaluateWatch(
    watch: WatchEntity,
    conditionMet: Boolean,
    now: Long,
): WatchDecision =
    when {
        !watch.enabled ->
            WatchDecision(shouldFire = false, newArmed = watch.armed, newLastFiredAt = watch.lastFiredAt)
        watch.armed && conditionMet ->
            WatchDecision(shouldFire = true, newArmed = false, newLastFiredAt = now)
        !watch.armed && !conditionMet ->
            WatchDecision(shouldFire = false, newArmed = true, newLastFiredAt = watch.lastFiredAt)
        else ->
            WatchDecision(shouldFire = false, newArmed = watch.armed, newLastFiredAt = watch.lastFiredAt)
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
    watch: WatchEntity,
    allEvents: List<EventEntity>,
    now: Long,
): Pair<Expectation, List<EventEntity>> {
    val windowStart = oftenWindowStart(now, watch.windowDays)
    val expectation =
        Expectation(
            count = watch.threshold,
            per = watch.expectedPer,
            metric = watch.metric,
            windowStart = windowStart,
        )
    val minIntensity = watch.minIntensity
    val filteredEvents =
        if (minIntensity == null) {
            allEvents
        } else {
            allEvents.filter { it.intensity != null && it.intensity >= minIntensity }
        }
    return expectation to filteredEvents
}

/**
 * `OFTEN`'s condition (spec §11): the observed rate over the watch's lookback window has
 * reached the expected rate. Firing is never tier-gated — [computeVerdict]'s `ConfidenceTier` and
 * `comparisonBand` play no role in this boolean decision, only in what a later UI displays; a
 * `NO_VERDICT` case can still fire if its raw observed-vs-expected rate clears the bar.
 */
fun evaluateOften(
    watch: WatchEntity,
    allEvents: List<EventEntity>,
    now: Long,
    durationMode: DurationMode,
): WatchDecision {
    val (expectation, filteredEvents) = expectationInputsFor(watch, allEvents, now)
    val result = computeVerdict(expectation, filteredEvents, now, durationMode)
    return evaluateWatch(watch, conditionMet = result.observedRate >= result.expectedRate, now = now)
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

/** `QUIET`'s condition (spec §11): days since the latest of last event / case creation has reached [WatchEntity.threshold]. */
fun evaluateQuiet(
    watch: WatchEntity,
    mostRecentEventAt: Long?,
    caseCreatedAt: Long,
    now: Long,
): WatchDecision {
    // anchor-vs-now: "now" has no captured offset by definition, so daysBetween's default
    // device-current-zone resolution is already correct here — see computeGapStats's doc comment
    // for the general per-event-vs-now rule this follows.
    val silentDays = daysBetween(mostRecentEventAt ?: caseCreatedAt, now)
    return evaluateWatch(watch, conditionMet = silentDays >= watch.threshold, now = now)
}
