package com.secondmonday.hodith.domain.timeline

import kotlin.math.abs

private const val DAY_MILLIS = 24L * 60 * 60 * 1000L

const val MIN_DOT_SIZE_FRACTION = 0.4f
const val MAX_DOT_SIZE_FRACTION = 1.0f
const val MIN_INTENSITY = 1
const val MAX_INTENSITY = 5

data class TimeWindow(
    val startMillis: Long,
    val endMillis: Long,
) {
    init {
        require(endMillis > startMillis) { "endMillis must be after startMillis" }
    }

    val durationMillis: Long get() = endMillis - startMillis
}

enum class ZoomLevel(
    val durationMillis: Long,
) {
    WEEK(DAY_MILLIS * 7),
    MONTH(DAY_MILLIS * 30),
    THREE_MONTH(DAY_MILLIS * 90),
    YEAR(DAY_MILLIS * 365),
    ;

    companion object {
        fun nearestTo(durationMillis: Long): ZoomLevel = entries.minBy { abs(it.durationMillis - durationMillis) }
    }
}

/**
 * Domain-level stand-in for [com.secondmonday.hodith.data.EventEntity], decoupled from the Room
 * schema so this package stays pure Kotlin per CLAUDE.md's domain/ rule.
 */
data class TimelineEvent(
    val id: Long,
    val occurredAt: Long,
    val endedAt: Long? = null,
    val intensity: Int? = null,
)

sealed interface TimelineMark {
    data class Dot(
        val xFraction: Float,
        val sizeFraction: Float,
        val eventIds: List<Long>,
    ) : TimelineMark

    data class Bar(
        val startXFraction: Float,
        val endXFraction: Float,
        val eventId: Long,
    ) : TimelineMark
}

/**
 * Lays out one case's events against a shared [window], clustering point events that fall
 * within the same slot so dots never overlap closer than [slotCount] allows across the row.
 * All positions are fractions of the row width (0f-1f) — density/dp are the caller's concern.
 */
fun layoutRow(
    events: List<TimelineEvent>,
    window: TimeWindow,
    slotCount: Int,
    intensityEnabled: Boolean,
): List<TimelineMark> {
    require(slotCount > 0) { "slotCount must be positive" }

    val visible =
        events.filter { event ->
            (event.endedAt ?: event.occurredAt) >= window.startMillis && event.occurredAt <= window.endMillis
        }

    val bars = mutableListOf<TimelineMark.Bar>()
    val pointEvents = mutableListOf<TimelineEvent>()
    for (event in visible) {
        val endedAt = event.endedAt
        if (endedAt != null && endedAt > event.occurredAt) {
            bars +=
                TimelineMark.Bar(
                    startXFraction = xFraction(event.occurredAt, window).coerceIn(0f, 1f),
                    endXFraction = xFraction(endedAt, window).coerceIn(0f, 1f),
                    eventId = event.id,
                )
        } else {
            pointEvents += event
        }
    }

    val slotWidthMillis = window.durationMillis.toDouble() / slotCount
    val buckets = LinkedHashMap<Int, MutableList<TimelineEvent>>()
    for (event in pointEvents) {
        val slotIndex =
            ((event.occurredAt - window.startMillis) / slotWidthMillis)
                .toInt()
                .coerceIn(0, slotCount - 1)
        buckets.getOrPut(slotIndex) { mutableListOf() }.add(event)
    }

    val dots =
        buckets.map { (slotIndex, bucketEvents) ->
            val slotCenterMillis = window.startMillis + ((slotIndex + 0.5) * slotWidthMillis).toLong()
            TimelineMark.Dot(
                xFraction = xFraction(slotCenterMillis, window).coerceIn(0f, 1f),
                sizeFraction = dotSize(bucketEvents, intensityEnabled),
                eventIds = bucketEvents.map { it.id },
            )
        }

    return bars + dots
}

private fun xFraction(
    millis: Long,
    window: TimeWindow,
): Float = ((millis - window.startMillis).toDouble() / window.durationMillis).toFloat()

private fun dotSize(
    bucketEvents: List<TimelineEvent>,
    intensityEnabled: Boolean,
): Float {
    if (intensityEnabled) {
        val maxIntensity = bucketEvents.mapNotNull { it.intensity }.maxOrNull()
        if (maxIntensity != null) {
            val clamped = maxIntensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
            val span = (MAX_INTENSITY - MIN_INTENSITY).toFloat()
            return MIN_DOT_SIZE_FRACTION + (clamped - MIN_INTENSITY) / span * (MAX_DOT_SIZE_FRACTION - MIN_DOT_SIZE_FRACTION)
        }
    }
    return if (bucketEvents.size > 1) MAX_DOT_SIZE_FRACTION else MIN_DOT_SIZE_FRACTION
}
