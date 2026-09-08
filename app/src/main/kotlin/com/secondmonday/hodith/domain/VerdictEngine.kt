package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.VerdictMetric
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Spec §8 confidence tiers: both the observation count and the day count must clear their bar. */
internal const val PRELIMINARY_MIN_EVENTS = 5
internal const val PRELIMINARY_MIN_DAYS = 14L
internal const val CONFIDENT_MIN_EVENTS = 15
internal const val CONFIDENT_MIN_DAYS = 28L

/** Spec §7: the nudge card offers a Hunch after this many logged events on a hunch-less Case. */
internal const val HUNCH_NUDGE_EVENT_THRESHOLD = 5

/**
 * Spec §8 comparison-band cutoffs (observed ÷ expected): `<0.5` much less, `0.5–0.8` less,
 * `0.8–1.25` about right, `1.25–2.0` more, `>2.0` much more. Each boundary value itself belongs
 * to the higher band (e.g. exactly 0.8 is "about right", not "less").
 */
internal const val MUCH_LESS_MAX_RATIO = 0.5
internal const val LESS_MAX_RATIO = 0.8
internal const val ABOUT_RIGHT_MAX_RATIO = 1.25
internal const val MORE_MAX_RATIO = 2.0

/**
 * Spec §8's verdict engine: a pure function of a Hunch, its Case's events, the Case's duration
 * mode, and the current time. Verdicts are computed fresh on every read, never stored, so this
 * is the app's most unit-testable — and riskiest to get wrong — surface.
 *
 * The observation window ends at [now] in every mode; its start comes from the Hunch's
 * [HunchEntity.observationWindow] (see [windowStartFor]). Events whose active span never reaches
 * into the window are excluded before anything is counted. What is then counted depends on
 * [HunchEntity.metric]: the raw in-window event tally ([VerdictMetric.OCCURRENCE_COUNT]) or the
 * number of distinct calendar days an in-window event's span touched ([VerdictMetric.DAYS_ACTIVE]).
 */
internal fun computeVerdict(
    hunch: HunchEntity,
    events: List<EventEntity>,
    caseCreatedAt: Long,
    now: Long,
    durationMode: DurationMode,
): VerdictResult {
    val zone = ZoneId.systemDefault()
    val windowStartMillis = windowStartFor(hunch, events, caseCreatedAt, now)
    val windowDays = daysBetween(windowStartMillis, now, zone)

    // In-window = the event's active span intersects [windowStart, now]: it must reach into the
    // window (span-overlap, not just occurredAt) and must have started by now.
    val inWindow = events.filter { it.occurredAt <= now && activeSpanEnd(it, durationMode, now) >= windowStartMillis }
    val eventCount = inWindow.size
    val activeDayCount = distinctActiveDays(inWindow, durationMode, windowStartMillis, now, zone)
    val observationCount = if (hunch.metric == VerdictMetric.DAYS_ACTIVE) activeDayCount else eventCount

    val tier = confidenceTierFor(observationCount, windowDays)
    val observedRate = observedRateFor(observationCount, windowDays, hunch.expectedPer)
    val expectedRate = hunch.expectedCount.toDouble()

    return VerdictResult(
        tier = tier,
        metric = hunch.metric,
        eventCount = eventCount,
        activeDayCount = activeDayCount,
        windowDays = windowDays,
        observedRate = observedRate,
        expectedRate = expectedRate,
        comparisonBand = if (tier == ConfidenceTier.NO_VERDICT) null else comparisonBandFor(observedRate, expectedRate),
    )
}

/**
 * The observation window's start instant for [hunch]'s window mode:
 * - [ObservationWindow.SINCE_START] — the earlier of the Case's creation or its earliest event
 *   (a retro-logged event can predate the Case itself).
 * - [ObservationWindow.LAST_3_MONTHS] — a rolling fixed 90-day span ending at [now]
 *   ([DAYS_PER_QUARTER], matching the app's other calendar approximations), floored so it never
 *   predates the Case.
 * - [ObservationWindow.CUSTOM] — the stored [HunchEntity.windowStartDate], floored at the Case's
 *   own creation.
 */
internal fun windowStartFor(
    hunch: HunchEntity,
    events: List<EventEntity>,
    caseCreatedAt: Long,
    now: Long,
): Long =
    when (hunch.observationWindow) {
        ObservationWindow.SINCE_START ->
            minOf(caseCreatedAt, events.minOfOrNull { it.occurredAt } ?: caseCreatedAt)
        ObservationWindow.LAST_3_MONTHS ->
            maxOf(caseCreatedAt, now - (DAYS_PER_QUARTER.toLong() * MILLIS_PER_DAY))
        ObservationWindow.CUSTOM ->
            maxOf(caseCreatedAt, hunch.windowStartDate ?: caseCreatedAt)
    }

/**
 * Distinct calendar days in [[windowStartMillis], [now]] that any of [events]' active spans
 * touched, in [zone]. Days a span covers outside the window don't count — a duration event that
 * began before the window still only contributes its in-window days.
 */
internal fun distinctActiveDays(
    events: List<EventEntity>,
    durationMode: DurationMode,
    windowStartMillis: Long,
    now: Long,
    zone: ZoneId,
): Int {
    val windowStartDate = Instant.ofEpochMilli(windowStartMillis).atZone(zone).toLocalDate()
    val windowEndDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = mutableSetOf<LocalDate>()
    for (event in events) {
        for (date in datesCovered(event.occurredAt, activeSpanEnd(event, durationMode, now), zone)) {
            if (!date.isBefore(windowStartDate) && !date.isAfter(windowEndDate)) days += date
        }
    }
    return days.size
}

internal fun confidenceTierFor(
    observationCount: Int,
    windowDays: Long,
): ConfidenceTier =
    when {
        observationCount >= CONFIDENT_MIN_EVENTS && windowDays >= CONFIDENT_MIN_DAYS -> ConfidenceTier.CONFIDENT
        observationCount >= PRELIMINARY_MIN_EVENTS && windowDays >= PRELIMINARY_MIN_DAYS -> ConfidenceTier.PRELIMINARY
        else -> ConfidenceTier.NO_VERDICT
    }

/** Normalizes a per-day rate up to the Hunch's own unit so it's directly comparable to [HunchEntity.expectedCount]. */
internal fun observedRateFor(
    eventCount: Int,
    windowDays: Long,
    expectedPer: ExpectedPer,
): Double {
    if (windowDays == 0L) return 0.0
    val ratePerDay = eventCount.toDouble() / windowDays
    return when (expectedPer) {
        ExpectedPer.DAY -> ratePerDay
        ExpectedPer.WEEK -> ratePerDay * DAYS_PER_WEEK
        ExpectedPer.MONTH -> ratePerDay * DAYS_PER_MONTH
        ExpectedPer.QUARTER -> ratePerDay * DAYS_PER_QUARTER
    }
}

internal fun comparisonBandFor(
    observedRate: Double,
    expectedRate: Double,
): ComparisonBand {
    val ratio = observedRate / expectedRate
    return when {
        ratio < MUCH_LESS_MAX_RATIO -> ComparisonBand.MUCH_LESS
        ratio < LESS_MAX_RATIO -> ComparisonBand.LESS
        ratio < ABOUT_RIGHT_MAX_RATIO -> ComparisonBand.ABOUT_RIGHT
        ratio < MORE_MAX_RATIO -> ComparisonBand.MORE
        else -> ComparisonBand.MUCH_MORE
    }
}
