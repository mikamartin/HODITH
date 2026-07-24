package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Spec §8 confidence tiers: both the event count and the day count must clear their bar. */
internal const val PRELIMINARY_MIN_EVENTS = 5
internal const val PRELIMINARY_MIN_DAYS = 14L
internal const val CONFIDENT_MIN_EVENTS = 15
internal const val CONFIDENT_MIN_DAYS = 28L

/**
 * Spec §8 comparison-band cutoffs (observed ÷ expected): `<0.5` much less, `0.5–0.8` less,
 * `0.8–1.25` about right, `1.25–2.0` more, `>2.0` much more. Each boundary value itself belongs
 * to the higher band (e.g. exactly 0.8 is "about right", not "less").
 */
internal const val MUCH_LESS_MAX_RATIO = 0.5
internal const val LESS_MAX_RATIO = 0.8
internal const val ABOUT_RIGHT_MAX_RATIO = 1.25
internal const val MORE_MAX_RATIO = 2.0

private const val DAYS_PER_WEEK = 7.0
private const val DAYS_PER_MONTH = 30.0

/**
 * Spec §8's verdict engine: a pure function of a Hunch, its Case's events, and the current time.
 * Verdicts are computed fresh on every read, never stored, so this is the app's most
 * unit-testable — and riskiest to get wrong — surface.
 *
 * The observation window starts at the earlier of the Case's creation or its earliest event
 * (a retro-logged event can predate the Case itself) and runs to [now].
 */
internal fun computeVerdict(
    hunch: HunchEntity,
    events: List<EventEntity>,
    caseCreatedAt: Long,
    now: Long,
): VerdictResult {
    val zone = ZoneId.systemDefault()
    val windowStartMillis = minOf(caseCreatedAt, events.minOfOrNull { it.occurredAt } ?: caseCreatedAt)
    val windowStartDate = Instant.ofEpochMilli(windowStartMillis).atZone(zone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val windowDays = ChronoUnit.DAYS.between(windowStartDate, nowDate)

    val eventCount = events.size
    val tier = confidenceTierFor(eventCount, windowDays)
    val observedRate = observedRateFor(eventCount, windowDays, hunch.expectedPer)
    val expectedRate = hunch.expectedCount.toDouble()

    return VerdictResult(
        tier = tier,
        eventCount = eventCount,
        windowDays = windowDays,
        observedRate = observedRate,
        expectedRate = expectedRate,
        comparisonBand = if (tier == ConfidenceTier.NO_VERDICT) null else comparisonBandFor(observedRate, expectedRate),
    )
}

internal fun confidenceTierFor(
    eventCount: Int,
    windowDays: Long,
): ConfidenceTier =
    when {
        eventCount >= CONFIDENT_MIN_EVENTS && windowDays >= CONFIDENT_MIN_DAYS -> ConfidenceTier.CONFIDENT
        eventCount >= PRELIMINARY_MIN_EVENTS && windowDays >= PRELIMINARY_MIN_DAYS -> ConfidenceTier.PRELIMINARY
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
