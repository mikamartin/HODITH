package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity

/** The period a [HeroRate] is expressed per; [days] is its length in whole days. */
enum class RateUnit(
    val days: Int,
) {
    DAY(1),
    WEEK(7),
    MONTH(30),
}

/**
 * How the last [TREND_WINDOW_DAYS] compare with the [TREND_WINDOW_DAYS] before, expressed in the
 * same [RateUnit] as the [HeroRate] it rides on. [priorValue] is that earlier window's rate;
 * [direction] comes straight from the counts, so [TrendDirection.FLAT] is reported here even though
 * the Trends list drops a flat comparison.
 */
data class HeroRateComparison(
    val direction: TrendDirection,
    val priorValue: Double,
)

/**
 * The Square share card's headline rate. With a [comparison] it is the last [TREND_WINDOW_DAYS]'s
 * rate; without one it is the overall rate across the observed span. [belowOnePerMonth] marks a
 * rate under one a month ([value] is then the true, sub-1 monthly figure and the card says "<1").
 */
data class HeroRate(
    val value: Double,
    val unit: RateUnit,
    val belowOnePerMonth: Boolean,
    val comparison: HeroRateComparison?,
)

/** A rate needs this many events before it says anything; shared with the verdict's preliminary tier. */
internal const val HERO_RATE_MIN_EVENTS = PRELIMINARY_MIN_EVENTS

/** A rate needs this many observed days before it says anything; shared with the verdict's preliminary tier. */
internal const val HERO_RATE_MIN_DAYS = PRELIMINARY_MIN_DAYS

/**
 * The largest-grained unit in which [eventCount] events over [windowDays] still average at least
 * one: per day, else per week, else per month. Compared with integer math so a boundary such as
 * 30 events in 30 days is exactly one a day, with no floating-point drift. A rate under one a
 * month still reads per month (callers flag it with [HeroRate.belowOnePerMonth]).
 */
internal fun pickRateUnit(
    eventCount: Int,
    windowDays: Long,
): RateUnit =
    when {
        eventCount.toLong() * RateUnit.DAY.days >= windowDays -> RateUnit.DAY
        eventCount.toLong() * RateUnit.WEEK.days >= windowDays -> RateUnit.WEEK
        else -> RateUnit.MONTH
    }

private fun ratePer(
    unit: RateUnit,
    eventCount: Int,
    windowDays: Long,
): Double = eventCount.toDouble() * unit.days / windowDays

/**
 * The Square card's hero rate: `null` below [HERO_RATE_MIN_EVENTS] events or [HERO_RATE_MIN_DAYS]
 * observed days, so a card never states a rate it cannot support. [trendStats] (the existing 30
 * vs. 30 days comparison, itself absent below [TREND_MIN_SPAN_DAYS]) switches the basis: present,
 * the headline is the last window's rate with the earlier window as [HeroRate.comparison]; absent,
 * it is the overall rate with no comparison. The unit is picked once, from the headline's own
 * count, and the comparison is converted into that same unit so the card states a unit once.
 */
internal fun computeHeroRate(
    eventCount: Int,
    observedDays: Long,
    trendStats: TrendStats?,
): HeroRate? {
    if (eventCount < HERO_RATE_MIN_EVENTS || observedDays < HERO_RATE_MIN_DAYS) return null

    val headlineCount = trendStats?.recentCount ?: eventCount
    val windowDays = if (trendStats != null) TREND_WINDOW_DAYS else observedDays
    val unit = pickRateUnit(headlineCount, windowDays)
    val monthlyRateBelowOne = headlineCount.toLong() * RateUnit.MONTH.days < windowDays

    return HeroRate(
        value = ratePer(unit, headlineCount, windowDays),
        unit = unit,
        belowOnePerMonth = monthlyRateBelowOne,
        comparison =
            trendStats?.let {
                HeroRateComparison(direction = it.direction, priorValue = ratePer(unit, it.priorCount, TREND_WINDOW_DAYS))
            },
    )
}

/**
 * [computeHeroRate] for a Case's whole history, with the same trend basis Insights uses: the trend
 * comparison is skipped below [INSIGHTS_MIN_EVENTS], the gate Insights' own stats apply. [spanDays]
 * is the caller's already-computed [observationSpanDays], so it is not resolved twice.
 */
internal fun caseHeroRate(
    events: List<EventEntity>,
    spanDays: Long,
    now: Long,
): HeroRate? {
    val trendStats = if (events.size < INSIGHTS_MIN_EVENTS) null else computeTrendStats(events, now, spanDays)
    return computeHeroRate(events.size, spanDays, trendStats)
}
