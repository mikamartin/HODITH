package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Spec §10 frequency/trend: below this span, a Case hasn't been running long enough to bucket by week/month or trend at all. */
internal const val STATS_SHORT_SPAN_MAX_DAYS = 56L // 8 weeks
internal const val STATS_MEDIUM_SPAN_MAX_DAYS = 182L // ~6 months

/** Spec §10 frequency-over-time: at most this many bars, however fine the granularity. */
internal const val FREQUENCY_MAX_BUCKETS = 12

/** Spec §10 trend arrow: rolling comparison window, and the minimum Case span before it's shown at all. */
internal const val TREND_WINDOW_DAYS = 30L
internal const val TREND_MIN_SPAN_DAYS = STATS_SHORT_SPAN_MAX_DAYS

/** Spec §10 rhythm heatmap: coarse time-of-day boundaries (hour-of-day, local time). */
internal const val MORNING_START_HOUR = 6
internal const val AFTERNOON_START_HOUR = 12
internal const val EVENING_START_HOUR = 17
internal const val NIGHT_START_HOUR = 21

internal const val INTENSITY_MIN = 1
internal const val INTENSITY_MAX = 5

/**
 * A Case's full observation span in days, from the earlier of its creation or earliest (possibly
 * retro-logged) event through [now] — mirrors [computeVerdict]'s window-start rule, since frequency
 * granularity and the trend arrow both need "how long has this Case actually been observed".
 */
internal fun observationSpanDays(
    events: List<EventEntity>,
    caseCreatedAt: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val startMillis = minOf(caseCreatedAt, events.minOfOrNull { it.occurredAt } ?: caseCreatedAt)
    return daysBetween(startMillis, now, zone)
}

/** Picks bucket granularity from the Case's observation span: short spans read fine day-by-day, long ones need months. */
internal fun pickFrequencyGranularity(spanDays: Long): FrequencyGranularity =
    when {
        spanDays <= STATS_SHORT_SPAN_MAX_DAYS -> FrequencyGranularity.DAY
        spanDays <= STATS_MEDIUM_SPAN_MAX_DAYS -> FrequencyGranularity.WEEK
        else -> FrequencyGranularity.MONTH
    }

private fun bucketStartFor(
    date: LocalDate,
    granularity: FrequencyGranularity,
): LocalDate =
    when (granularity) {
        FrequencyGranularity.DAY -> date
        FrequencyGranularity.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong())
        FrequencyGranularity.MONTH -> date.withDayOfMonth(1)
    }

private fun LocalDate.minusPeriods(
    n: Int,
    granularity: FrequencyGranularity,
): LocalDate =
    when (granularity) {
        FrequencyGranularity.DAY -> minusDays(n.toLong())
        FrequencyGranularity.WEEK -> minusWeeks(n.toLong())
        FrequencyGranularity.MONTH -> minusMonths(n.toLong())
    }

/**
 * Spec §10 frequency-over-time: counts per bucket, most recent [FREQUENCY_MAX_BUCKETS] buckets
 * only. [granularity] defaults to the auto-pick but is user-overridable at the call site.
 */
internal fun computeFrequencyStats(
    events: List<EventEntity>,
    now: Long,
    spanDays: Long,
    granularity: FrequencyGranularity = pickFrequencyGranularity(spanDays),
    zone: ZoneId = ZoneId.systemDefault(),
): FrequencyStats {
    val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val currentBucketStart = bucketStartFor(nowDate, granularity)

    val countsByBucket =
        events
            .groupingBy { bucketStartFor(Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate(), granularity) }
            .eachCount()

    val buckets =
        (FREQUENCY_MAX_BUCKETS - 1 downTo 0).map { periodsAgo ->
            val start = currentBucketStart.minusPeriods(periodsAgo, granularity)
            FrequencyBucket(periodStart = start, count = countsByBucket[start] ?: 0)
        }

    return FrequencyStats(granularity = granularity, buckets = buckets)
}

/** Hour-of-day (local time) to the rhythm heatmap's coarse [TimeOfDay] bucket. Night wraps past midnight. */
internal fun timeOfDayFor(hour: Int): TimeOfDay =
    when {
        hour < MORNING_START_HOUR -> TimeOfDay.NIGHT
        hour < AFTERNOON_START_HOUR -> TimeOfDay.MORNING
        hour < EVENING_START_HOUR -> TimeOfDay.AFTERNOON
        hour < NIGHT_START_HOUR -> TimeOfDay.EVENING
        else -> TimeOfDay.NIGHT
    }

/** Spec §10 rhythm heatmap: day-of-week × time-of-day counts, zero-filled across all 28 combinations. */
internal fun computeRhythmStats(
    events: List<EventEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
): RhythmStats {
    val counts = mutableMapOf<Pair<DayOfWeek, TimeOfDay>, Int>()
    events.forEach { event ->
        val dateTime = Instant.ofEpochMilli(event.occurredAt).atZone(zone)
        val key = dateTime.dayOfWeek to timeOfDayFor(dateTime.hour)
        counts[key] = (counts[key] ?: 0) + 1
    }

    val cells =
        DayOfWeek.entries.flatMap { day ->
            TimeOfDay.entries.map { timeOfDay -> RhythmCell(day, timeOfDay, counts[day to timeOfDay] ?: 0) }
        }
    return RhythmStats(cells = cells, maxCount = cells.maxOfOrNull { it.count } ?: 0)
}

/**
 * Spec §10 trend arrow: last [TREND_WINDOW_DAYS] vs. the [TREND_WINDOW_DAYS] before, hidden
 * entirely (`null`) below [TREND_MIN_SPAN_DAYS] of observation — too little history for the
 * comparison to mean anything.
 */
internal fun computeTrendStats(
    events: List<EventEntity>,
    now: Long,
    spanDays: Long,
): TrendStats? {
    if (spanDays < TREND_MIN_SPAN_DAYS) return null

    val recentCutoff = now - TREND_WINDOW_DAYS * MILLIS_PER_DAY
    val priorCutoff = now - 2 * TREND_WINDOW_DAYS * MILLIS_PER_DAY
    val recentCount = events.count { it.occurredAt in (recentCutoff + 1)..now }
    val priorCount = events.count { it.occurredAt in (priorCutoff + 1)..recentCutoff }

    val direction =
        when {
            recentCount > priorCount -> TrendDirection.UP
            recentCount < priorCount -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
    return TrendStats(direction = direction, recentCount = recentCount, priorCount = priorCount)
}

/** Spec §10 duration stats: `null` when no event in [events] has a recorded duration (`endedAt` set). */
internal fun computeDurationStats(events: List<EventEntity>): DurationStats? {
    val durationsMillis = events.mapNotNull { event -> event.endedAt?.let { it - event.occurredAt } }.filter { it > 0 }
    if (durationsMillis.isEmpty()) return null

    val totalMillis = durationsMillis.sum()
    return DurationStats(
        averageMinutes = (totalMillis.toDouble() / durationsMillis.size) / MILLIS_PER_MINUTE,
        longestMinutes = durationsMillis.max() / MILLIS_PER_MINUTE,
        totalMinutes = totalMillis / MILLIS_PER_MINUTE,
    )
}

/** Spec §10 intensity stats: `null` when no event in [events] has a recorded intensity. */
internal fun computeIntensityStats(events: List<EventEntity>): IntensityStats? {
    val intensities = events.mapNotNull { it.intensity }
    if (intensities.isEmpty()) return null

    val distribution = (INTENSITY_MIN..INTENSITY_MAX).associateWith { value -> intensities.count { it == value } }
    return IntensityStats(averageIntensity = intensities.average(), distribution = distribution)
}

/** Spec §10 tag breakdown: counts per tag name, busiest first. Empty (not null) when no event carries a tag. */
internal fun computeTagBreakdown(eventsWithTags: List<EventWithTags>): List<TagBreakdownEntry> =
    eventsWithTags
        .flatMap { it.tags }
        .groupingBy { it.name }
        .eachCount()
        .map { (name, count) -> TagBreakdownEntry(tagName = name, count = count) }
        .sortedByDescending { it.count }
