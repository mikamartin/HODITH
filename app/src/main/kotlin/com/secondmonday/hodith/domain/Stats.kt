package com.secondmonday.hodith.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** Spec §10 frequency-over-time: bucket granularity, auto-picked from the Case's observation span. */
enum class FrequencyGranularity {
    DAY,
    WEEK,
    MONTH,
}

/** One bar in the frequency-over-time chart. [periodStart] is the bucket's first day. */
data class FrequencyBucket(
    val periodStart: LocalDate,
    val count: Int,
)

data class FrequencyStats(
    val granularity: FrequencyGranularity,
    val buckets: List<FrequencyBucket>,
)

/** Spec §10 rhythm heatmap: coarse time-of-day buckets — exact hours would be noise, not rhythm. */
enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

data class RhythmCell(
    val dayOfWeek: DayOfWeek,
    val timeOfDay: TimeOfDay,
    val count: Int,
)

/** [cells] always has all 28 day-of-week × time-of-day combinations, zero-filled. */
data class RhythmStats(
    val cells: List<RhythmCell>,
    val maxCount: Int,
)

enum class TrendDirection {
    UP,
    DOWN,
    FLAT,
}

/** Spec §10 trend arrow: last 30 days vs. the 30 before. Absent entirely below the minimum span. */
data class TrendStats(
    val direction: TrendDirection,
    val recentCount: Int,
    val priorCount: Int,
)

/** Spec §10 duration stats — only meaningful when the Case's `durationMode != NONE`. */
data class DurationStats(
    val averageMinutes: Double,
    val longestMinutes: Long,
    val totalMinutes: Long,
)

/** Spec §10 intensity stats — only meaningful when the Case has `intensityEnabled`. [distribution] keys are 1..5. */
data class IntensityStats(
    val averageIntensity: Double,
    val distribution: Map<Int, Int>,
)

data class TagBreakdownEntry(
    val tagName: String,
    val count: Int,
)
