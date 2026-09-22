package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.loggedZone
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

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
 * Spec §10 Trends "tag share shift" finding: needs at least this many events on the Case before a
 * first-half-vs-second-half tag-share comparison runs at all — higher than [GAP_SHIFT_MIN_SAMPLE_COUNT]
 * since each half needs enough events for a percentage to mean anything, not just an average.
 */
internal const val TAG_SHARE_SHIFT_MIN_SAMPLE_COUNT = 8

/** A tag must appear at least this many times total (both halves combined) before its share is considered — a tag seen once or twice can't support a share claim regardless of how big the swing looks, the same reasoning [GAP_BURST_MIN_GAP_COUNT] applies to gap variance. */
internal const val TAG_SHARE_SHIFT_MIN_TAG_COUNT = 3

/**
 * A tag's share shift only counts as "noticeable" once it clears both a relative and an absolute
 * floor — the same dual-threshold shape [SHIFT_MIN_FRACTION]/[SHIFT_MIN_ABSOLUTE_DAYS] use for
 * gap/streak shift, but stricter: a share of a small event count swings more easily by chance than
 * a day-average does, so a plain 30%-relative/1-day-absolute floor would fire too often here.
 */
internal const val TAG_SHARE_SHIFT_MIN_ABSOLUTE_FRACTION = 0.15
internal const val TAG_SHARE_SHIFT_MIN_RELATIVE_FRACTION = 0.5

/** Per-detector cap on [computeTagShareShift]'s own findings — independent of the shared [TRENDS_MAX_FINDINGS], so a Case with many tags can't crowd out every other detector's finding. */
internal const val TAG_SHARE_SHIFT_MAX_FINDINGS = 3

/**
 * A Case's full observation span in days, from the earlier of its creation or earliest (possibly
 * retro-logged) event through [now] — mirrors [computeVerdict]'s window-start rule, since frequency
 * granularity and the trend arrow both need "how long has this Case actually been observed". This
 * stays on the passed-in [zone] rather than the earliest event's own captured offset: it's a
 * start-point-vs-now comparison, not one event vs. another, so it follows the same now-side rule
 * [computeGapStats] documents.
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
            .groupingBy { bucketStartFor(Instant.ofEpochMilli(it.occurredAt).atZone(it.loggedZone()).toLocalDate(), granularity) }
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

/**
 * Spec §10 rhythm heatmap: day-of-week × time-of-day counts, zero-filled across all 28 combinations.
 * Every date here is per-event with no "now" reference, so each event resolves via its own captured
 * offset ([EventEntity.loggedZone]) rather than a shared zone — a traveler's events bucket by wherever they
 * actually happened, not the device's current zone.
 */
internal fun computeRhythmStats(events: List<EventEntity>): RhythmStats {
    val counts = mutableMapOf<Pair<DayOfWeek, TimeOfDay>, Int>()
    events.forEach { event ->
        val dateTime = Instant.ofEpochMilli(event.occurredAt).atZone(event.loggedZone())
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

/**
 * Spec §10 Trends "tag share shift" finding: whether a tag's share of the Case's own events has
 * shifted noticeably between the earlier and more recent half of its history (split by event
 * count, the same shape [computeGapShift]/[computeStreakShift] use, not a fixed day window).
 * Empty below [TAG_SHARE_SHIFT_MIN_SAMPLE_COUNT] events; a tag with fewer than
 * [TAG_SHARE_SHIFT_MIN_TAG_COUNT] total occurrences is skipped entirely rather than reported as
 * stable. Results are ordered by effect size (largest share change first) and capped at
 * [TAG_SHARE_SHIFT_MAX_FINDINGS], since this is the one Trends detector that can produce more than
 * one finding per Case.
 */
internal fun computeTagShareShift(eventsWithTags: List<EventWithTags>): List<TagShareShiftResult> {
    if (eventsWithTags.size < TAG_SHARE_SHIFT_MIN_SAMPLE_COUNT) return emptyList()

    val sorted = eventsWithTags.sortedBy { it.event.occurredAt }
    val mid = sorted.size / 2
    val priorHalf = sorted.take(mid)
    val recentHalf = sorted.takeLast(sorted.size - mid)
    val tagNames = sorted.flatMap { it.tags }.map { it.name }.distinct()

    return tagNames
        .mapNotNull { tagName ->
            val priorCount = priorHalf.count { entry -> entry.tags.any { it.name == tagName } }
            val recentCount = recentHalf.count { entry -> entry.tags.any { it.name == tagName } }
            if (priorCount + recentCount < TAG_SHARE_SHIFT_MIN_TAG_COUNT) return@mapNotNull null

            val priorShare = priorCount.toDouble() / priorHalf.size
            val recentShare = recentCount.toDouble() / recentHalf.size
            tagShareShiftDirectionFor(priorShare, recentShare)?.let { direction ->
                TagShareShiftResult(tagName, direction, priorShare, recentShare, sorted.size)
            }
        }.sortedByDescending { abs(it.recentShare - it.priorShare) }
        .take(TAG_SHARE_SHIFT_MAX_FINDINGS)
}

/** `null` unless the change from [priorShare] to [recentShare] clears both [TAG_SHARE_SHIFT_MIN_ABSOLUTE_FRACTION] and [TAG_SHARE_SHIFT_MIN_RELATIVE_FRACTION]. */
private fun tagShareShiftDirectionFor(
    priorShare: Double,
    recentShare: Double,
): ShiftDirection? {
    val delta = recentShare - priorShare
    val fraction = if (priorShare == 0.0) Double.POSITIVE_INFINITY else abs(delta) / priorShare
    if (abs(delta) < TAG_SHARE_SHIFT_MIN_ABSOLUTE_FRACTION || fraction < TAG_SHARE_SHIFT_MIN_RELATIVE_FRACTION) return null
    return if (delta > 0) ShiftDirection.UP else ShiftDirection.DOWN
}

/**
 * Spec §10 Trends "tag → outcome" finding (Story C T4): sample-size floors, gated independently per
 * outcome on events carrying that outcome's value ([TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT] with the
 * tag, [TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT] without) — a tag can qualify for intensity but not
 * duration, or vice versa. [TAG_OUTCOME_MIN_RELATIVE_DIFFERENCE] is a descriptive floor checked
 * before the (comparatively expensive) permutation test runs at all;
 * [TAG_OUTCOME_SIGNIFICANCE_ALPHA] is the permutation p-value bar a candidate must then clear.
 * [TAG_OUTCOME_PERMUTATION_ITERATIONS] is the shuffle count behind that test.
 * [TAG_OUTCOME_MAX_FINDINGS] caps this detector's own findings across both outcomes combined, the
 * same way [TAG_SHARE_SHIFT_MAX_FINDINGS] caps its.
 */
internal const val TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT = 15
internal const val TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT = 30
internal const val TAG_OUTCOME_MIN_RELATIVE_DIFFERENCE = 0.2
internal const val TAG_OUTCOME_SIGNIFICANCE_ALPHA = 0.05
internal const val TAG_OUTCOME_PERMUTATION_ITERATIONS = 1000
internal const val TAG_OUTCOME_MAX_FINDINGS = 3

/**
 * Spec §10 Trends "trend slope" finding (Story C T6): a real slope over time in intensity or
 * duration's own per-event values, gated per outcome — only for Cases where that outcome's stat
 * card is already shown (the call site's `eligibleOutcomes`), so this never reports on data the
 * user can't already see summarized elsewhere. [TREND_SLOPE_MIN_SAMPLE_COUNT] matches
 * [CHANGE_POINT_MIN_SAMPLE_COUNT] — a full time-ordered series, not a two-average comparison, so
 * the higher floor applies, same reasoning as change point's own doc comment.
 * [TREND_SLOPE_MIN_RELATIVE_DIFFERENCE] is a cheap descriptive floor (the two time-ordered halves'
 * relative difference, via [relativeDifferenceInMeans]) checked before the permutation run, matching
 * [TAG_OUTCOME_MIN_RELATIVE_DIFFERENCE]'s bar. The feasibility ruling's ship decision: this reuses
 * the shared [permutationPValue] engine directly rather than [timelineShufflePValue] (typed to a
 * single reordered `Long` series, built for CUSUM's re-search — it doesn't fit "shuffle y-values
 * against fixed time-x-positions, recompute a slope," the standard permutation test for regression
 * significance) — [TREND_SLOPE_SIGNIFICANCE_ALPHA]/[TREND_SLOPE_PERMUTATION_ITERATIONS] still match
 * the roster's existing precedent.
 */
internal const val TREND_SLOPE_MIN_SAMPLE_COUNT = 12
internal const val TREND_SLOPE_MIN_RELATIVE_DIFFERENCE = 0.2
internal const val TREND_SLOPE_SIGNIFICANCE_ALPHA = 0.05
internal const val TREND_SLOPE_PERMUTATION_ITERATIONS = 1000

/**
 * Spec §10 Trends "time-of-day split" finding (Story C T6): whether intensity or duration differs
 * between day ([timeOfDayFor]'s [TimeOfDay.MORNING]/[TimeOfDay.AFTERNOON]) and evening
 * ([TimeOfDay.EVENING]/[TimeOfDay.NIGHT]) events — the feasibility ruling's other T6 signal, sharing
 * the same per-outcome gating as [TREND_SLOPE_MIN_SAMPLE_COUNT]'s detector. Reuses
 * [labelShufflePValue] as-is: two fixed-size groups (day values, evening values), exactly
 * [TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT]'s own shape with day/evening standing in for
 * untagged/tagged, so [TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT] matches that floor for both groups
 * (day/evening aren't inherently imbalanced the way tag-presence is, unlike
 * [TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT]'s higher untagged-side bar).
 */
internal const val TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT = 15
internal const val TIME_OF_DAY_SPLIT_MIN_RELATIVE_DIFFERENCE = 0.2
internal const val TIME_OF_DAY_SPLIT_SIGNIFICANCE_ALPHA = 0.05
internal const val TIME_OF_DAY_SPLIT_PERMUTATION_ITERATIONS = 1000

/**
 * Spec §10 Trends "tag → outcome" finding (Story C T4): whether a tag's events differ from the
 * Case's other events on intensity or duration, backed by a real permutation-significance test
 * rather than a threshold check — the roster's first `Pattern`-capable detector. A (tag, outcome)
 * pair that clears the descriptive floor but not significance is dropped entirely, never reported —
 * the feasibility ruling's "suppressed, not shown as Hint" rule, since every kept result here already
 * carries a real test behind it, unlike every threshold-only detector above it. Results across both
 * outcomes are pooled, ordered by effect size (as [computeTagShareShift] orders its own results), and
 * capped at [TAG_OUTCOME_MAX_FINDINGS] — the second detector able to produce more than one finding
 * per Case.
 */
internal fun computeTagOutcomeFindings(eventsWithTags: List<EventWithTags>): List<TagOutcomeResult> {
    val caseId = eventsWithTags.firstOrNull()?.event?.caseId ?: return emptyList()
    val tagNames = eventsWithTags.flatMap { it.tags }.map { it.name }.distinct()

    return tagNames
        .flatMap { tagName -> TagOutcome.entries.mapNotNull { outcome -> tagOutcomeResultFor(eventsWithTags, caseId, tagName, outcome) } }
        .sortedByDescending { abs(it.withTagMean - it.withoutTagMean) / it.withoutTagMean }
        .take(TAG_OUTCOME_MAX_FINDINGS)
}

/** One (tagName, outcome) candidate: `null` below the sample-size or descriptive-floor gates, or when the permutation test isn't significant. */
private fun tagOutcomeResultFor(
    eventsWithTags: List<EventWithTags>,
    caseId: Long,
    tagName: String,
    outcome: TagOutcome,
): TagOutcomeResult? {
    val tagged = mutableListOf<Double>()
    val untagged = mutableListOf<Double>()
    eventsWithTags.forEach { entry ->
        val value = outcomeValueFor(entry.event, outcome) ?: return@forEach
        if (entry.tags.any { it.name == tagName }) tagged += value else untagged += value
    }
    if (tagged.size < TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT || untagged.size < TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT) return null

    val relativeDifference = relativeDifferenceInMeans(untagged, tagged)
    if (abs(relativeDifference) < TAG_OUTCOME_MIN_RELATIVE_DIFFERENCE) return null

    val sampleCount = tagged.size + untagged.size
    val seed = permutationSeedFor(caseId, tagName, outcome, sampleCount)
    val pValue = labelShufflePValue(untagged, tagged, TAG_OUTCOME_PERMUTATION_ITERATIONS, seed)
    if (pValue >= TAG_OUTCOME_SIGNIFICANCE_ALPHA) return null

    return TagOutcomeResult(
        tagName = tagName,
        outcome = outcome,
        direction = if (relativeDifference > 0) ShiftDirection.UP else ShiftDirection.DOWN,
        withoutTagMean = untagged.average(),
        withTagMean = tagged.average(),
        sampleCount = sampleCount,
    )
}

/** [event]'s value for [outcome], or `null` when it doesn't carry one — no recorded intensity, or no recorded (positive) duration, the same filter [computeDurationStats] uses. */
private fun outcomeValueFor(
    event: EventEntity,
    outcome: TagOutcome,
): Double? =
    when (outcome) {
        TagOutcome.INTENSITY -> event.intensity?.toDouble()
        TagOutcome.DURATION ->
            event.endedAt
                ?.let { it - event.occurredAt }
                ?.takeIf { it > 0 }
                ?.let { it.toDouble() / MILLIS_PER_MINUTE }
    }

/**
 * Spec §10 Trends "trend slope" finding (Story C T6): one [TrendSlopeResult] per outcome in
 * [eligibleOutcomes] whose events show a real, significant slope over time — see
 * [TREND_SLOPE_MIN_SAMPLE_COUNT]'s doc comment for the feasibility ruling behind this shape.
 */
internal fun computeTrendSlopeFindings(
    eventsWithTags: List<EventWithTags>,
    eligibleOutcomes: Set<TagOutcome>,
): List<TrendSlopeResult> {
    val caseId = eventsWithTags.firstOrNull()?.event?.caseId ?: return emptyList()
    return TagOutcome.entries
        .filter { it in eligibleOutcomes }
        .mapNotNull { outcome -> trendSlopeResultFor(eventsWithTags, caseId, outcome) }
}

/**
 * One outcome's slope candidate: `null` below [TREND_SLOPE_MIN_SAMPLE_COUNT] events carrying
 * [outcome]'s value, below [TREND_SLOPE_MIN_RELATIVE_DIFFERENCE]'s descriptive floor (checked before
 * the permutation run), or when the permutation test isn't significant. [x] is each qualifying
 * event's day offset from the earliest one (time-ordered, fixed across every shuffle); only [y] (the
 * outcome's values) is reshuffled — the standard permutation test for regression-slope significance.
 */
private fun trendSlopeResultFor(
    eventsWithTags: List<EventWithTags>,
    caseId: Long,
    outcome: TagOutcome,
): TrendSlopeResult? {
    val sorted = eventsWithTags.sortedBy { it.event.occurredAt }
    val firstOccurredAt = sorted.firstOrNull()?.event?.occurredAt ?: return null
    val pairs =
        sorted.mapNotNull { entry ->
            outcomeValueFor(entry.event, outcome)?.let { value ->
                (entry.event.occurredAt - firstOccurredAt).toDouble() / MILLIS_PER_DAY to value
            }
        }
    if (pairs.size < TREND_SLOPE_MIN_SAMPLE_COUNT) return null

    val mid = pairs.size / 2
    val firstHalf = pairs.take(mid).map { it.second }
    val secondHalf = pairs.takeLast(pairs.size - mid).map { it.second }
    val relativeDifference = relativeDifferenceInMeans(firstHalf, secondHalf)
    if (abs(relativeDifference) < TREND_SLOPE_MIN_RELATIVE_DIFFERENCE) return null

    val x = pairs.map { it.first }
    val y = pairs.map { it.second }
    val slope = olsSlope(x, y)
    val seed = trendSlopeSeedFor(caseId, outcome, pairs.size)
    val pValue =
        permutationPValue(slope, TREND_SLOPE_PERMUTATION_ITERATIONS, seed) { random ->
            olsSlope(x, y.shuffled(random))
        }
    if (pValue >= TREND_SLOPE_SIGNIFICANCE_ALPHA) return null

    return TrendSlopeResult(
        outcome = outcome,
        direction = if (slope > 0) ShiftDirection.UP else ShiftDirection.DOWN,
        priorValue = firstHalf.average(),
        recentValue = secondHalf.average(),
        sampleCount = pairs.size,
    )
}

/** Ordinary-least-squares slope of [y] on [x] (equal-length, index-paired). `0.0` when [x] has zero variance — a defensive edge no real caller here should actually hit, since every [x] is a distinct day offset. */
private fun olsSlope(
    x: List<Double>,
    y: List<Double>,
): Double {
    val meanX = x.average()
    val meanY = y.average()
    val numerator = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
    val denominator = x.sumOf { (it - meanX) * (it - meanX) }
    return if (denominator == 0.0) 0.0 else numerator / denominator
}

/**
 * Spec §10 Trends "time-of-day split" finding (Story C T6): one [TimeOfDaySplitResult] per outcome
 * in [eligibleOutcomes] whose events show a real, significant day-vs-evening difference — see
 * [TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT]'s doc comment for the feasibility ruling behind this
 * shape.
 */
internal fun computeTimeOfDaySplitFindings(
    eventsWithTags: List<EventWithTags>,
    eligibleOutcomes: Set<TagOutcome>,
): List<TimeOfDaySplitResult> {
    val caseId = eventsWithTags.firstOrNull()?.event?.caseId ?: return emptyList()
    return TagOutcome.entries
        .filter { it in eligibleOutcomes }
        .mapNotNull { outcome -> timeOfDaySplitResultFor(eventsWithTags, caseId, outcome) }
}

/**
 * One outcome's day-vs-evening candidate: `null` when either group is below
 * [TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT], below [TIME_OF_DAY_SPLIT_MIN_RELATIVE_DIFFERENCE]'s
 * descriptive floor, or when the permutation test isn't significant. Each qualifying event's hour
 * resolves via its own captured offset ([EventEntity.loggedZone]), the same per-event rule
 * [computeRhythmStats] already uses, then buckets via [timeOfDayFor] collapsed to two groups: day
 * ([TimeOfDay.MORNING]/[TimeOfDay.AFTERNOON]) and evening ([TimeOfDay.EVENING]/[TimeOfDay.NIGHT]).
 */
private fun timeOfDaySplitResultFor(
    eventsWithTags: List<EventWithTags>,
    caseId: Long,
    outcome: TagOutcome,
): TimeOfDaySplitResult? {
    val dayGroup = mutableListOf<Double>()
    val eveningGroup = mutableListOf<Double>()
    eventsWithTags.forEach { entry ->
        val value = outcomeValueFor(entry.event, outcome) ?: return@forEach
        val hour = Instant.ofEpochMilli(entry.event.occurredAt).atZone(entry.event.loggedZone()).hour
        when (timeOfDayFor(hour)) {
            TimeOfDay.MORNING, TimeOfDay.AFTERNOON -> dayGroup += value
            TimeOfDay.EVENING, TimeOfDay.NIGHT -> eveningGroup += value
        }
    }
    if (dayGroup.size < TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT ||
        eveningGroup.size < TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT
    ) {
        return null
    }

    val relativeDifference = relativeDifferenceInMeans(dayGroup, eveningGroup)
    if (abs(relativeDifference) < TIME_OF_DAY_SPLIT_MIN_RELATIVE_DIFFERENCE) return null

    val sampleCount = dayGroup.size + eveningGroup.size
    val seed = timeOfDaySplitSeedFor(caseId, outcome, sampleCount)
    val pValue = labelShufflePValue(dayGroup, eveningGroup, TIME_OF_DAY_SPLIT_PERMUTATION_ITERATIONS, seed)
    if (pValue >= TIME_OF_DAY_SPLIT_SIGNIFICANCE_ALPHA) return null

    return TimeOfDaySplitResult(
        outcome = outcome,
        direction = if (relativeDifference > 0) ShiftDirection.UP else ShiftDirection.DOWN,
        dayMean = dayGroup.average(),
        eveningMean = eveningGroup.average(),
        sampleCount = sampleCount,
    )
}
