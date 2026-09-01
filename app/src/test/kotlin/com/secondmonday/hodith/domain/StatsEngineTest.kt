package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.testsupport.TEST_ZONE
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val ZONE = TEST_ZONE

private fun eventAt(
    occurredAt: Long,
    endedAt: Long? = null,
    intensity: Int? = null,
): EventEntity = testEvent(occurredAt = occurredAt, endedAt = endedAt, intensity = intensity)

private fun eventAtDay(epochDay: Long) = eventAt(millisAtDay(epochDay))

class StatsEngineTest {
    // ---- observationSpanDays ----

    @Test
    fun `observationSpanDays spans from the earliest event when it predates case creation`() {
        val events = listOf(eventAtDay(5))

        val result = observationSpanDays(events, caseCreatedAt = millisAtDay(10), now = millisAtDay(20))

        assertEquals(15L, result)
    }

    @Test
    fun `observationSpanDays falls back to case creation when there are no events`() {
        val result = observationSpanDays(emptyList(), caseCreatedAt = millisAtDay(10), now = millisAtDay(20))

        assertEquals(10L, result)
    }

    // ---- pickFrequencyGranularity ----

    @Test
    fun `pickFrequencyGranularity picks DAY at and below the short-span cutoff`() {
        assertEquals(FrequencyGranularity.DAY, pickFrequencyGranularity(STATS_SHORT_SPAN_MAX_DAYS))
    }

    @Test
    fun `pickFrequencyGranularity picks WEEK just above the short-span cutoff`() {
        assertEquals(FrequencyGranularity.WEEK, pickFrequencyGranularity(STATS_SHORT_SPAN_MAX_DAYS + 1))
    }

    @Test
    fun `pickFrequencyGranularity picks MONTH above the medium-span cutoff`() {
        assertEquals(FrequencyGranularity.MONTH, pickFrequencyGranularity(STATS_MEDIUM_SPAN_MAX_DAYS + 1))
    }

    // ---- computeFrequencyStats ----

    @Test
    fun `computeFrequencyStats buckets daily counts into the most recent buckets only`() {
        val today = 100L
        val events = listOf(eventAtDay(today), eventAtDay(today), eventAtDay(today - 1))

        val result =
            computeFrequencyStats(events, now = millisAtDay(today), spanDays = 10L, granularity = FrequencyGranularity.DAY)

        assertEquals(FREQUENCY_MAX_BUCKETS, result.buckets.size)
        assertEquals(2, result.buckets.last().count)
        assertEquals(1, result.buckets[result.buckets.size - 2].count)
        assertEquals(LocalDate.ofEpochDay(today), result.buckets.last().periodStart)
    }

    @Test
    fun `computeFrequencyStats buckets weekly counts to a Monday-aligned week start`() {
        // Anchor a Monday explicitly rather than relying on epoch-day 0's weekday.
        val monday = LocalDate.of(2026, 1, 5) // a Monday
        val now =
            monday
                .plusDays(3)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli() // Thursday same week
        val events =
            listOf(
                eventAt(
                    monday
                        .plusDays(2)
                        .atStartOfDay(ZONE)
                        .toInstant()
                        .toEpochMilli(),
                ),
            )

        val result = computeFrequencyStats(events, now = now, spanDays = 10L, granularity = FrequencyGranularity.WEEK)

        assertEquals(monday, result.buckets.last().periodStart)
        assertEquals(1, result.buckets.last().count)
    }

    // ---- timeOfDayFor ----

    @Test
    fun `timeOfDayFor buckets hours into the four coarse periods`() {
        assertEquals(TimeOfDay.NIGHT, timeOfDayFor(0))
        assertEquals(TimeOfDay.NIGHT, timeOfDayFor(5))
        assertEquals(TimeOfDay.MORNING, timeOfDayFor(6))
        assertEquals(TimeOfDay.MORNING, timeOfDayFor(11))
        assertEquals(TimeOfDay.AFTERNOON, timeOfDayFor(12))
        assertEquals(TimeOfDay.AFTERNOON, timeOfDayFor(16))
        assertEquals(TimeOfDay.EVENING, timeOfDayFor(17))
        assertEquals(TimeOfDay.EVENING, timeOfDayFor(20))
        assertEquals(TimeOfDay.NIGHT, timeOfDayFor(21))
        assertEquals(TimeOfDay.NIGHT, timeOfDayFor(23))
    }

    // ---- computeRhythmStats ----

    @Test
    fun `computeRhythmStats zero-fills all 28 day-of-week by time-of-day combinations`() {
        val result = computeRhythmStats(emptyList())

        assertEquals(28, result.cells.size)
        assertTrue(result.cells.all { it.count == 0 })
        assertEquals(0, result.maxCount)
    }

    @Test
    fun `computeRhythmStats counts events into their day-of-week and time-of-day cell`() {
        val monday = LocalDate.of(2026, 1, 5) // a Monday
        val events =
            listOf(
                eventAt(
                    monday
                        .atTime(8, 0)
                        .atZone(ZONE)
                        .toInstant()
                        .toEpochMilli(),
                ),
                eventAt(
                    monday
                        .atTime(9, 0)
                        .atZone(ZONE)
                        .toInstant()
                        .toEpochMilli(),
                ),
                eventAt(
                    monday
                        .atTime(19, 0)
                        .atZone(ZONE)
                        .toInstant()
                        .toEpochMilli(),
                ),
            )

        val result = computeRhythmStats(events)

        val mondayMorning = result.cells.single { it.dayOfWeek == monday.dayOfWeek && it.timeOfDay == TimeOfDay.MORNING }
        val mondayEvening = result.cells.single { it.dayOfWeek == monday.dayOfWeek && it.timeOfDay == TimeOfDay.EVENING }
        assertEquals(2, mondayMorning.count)
        assertEquals(1, mondayEvening.count)
        assertEquals(2, result.maxCount)
    }

    // ---- computeTrendStats ----

    @Test
    fun `computeTrendStats is null below the minimum observation span`() {
        val result = computeTrendStats(emptyList(), now = millisAtDay(100), spanDays = TREND_MIN_SPAN_DAYS - 1)

        assertNull(result)
    }

    @Test
    fun `computeTrendStats reports UP when the recent window has more events than the prior one`() {
        val today = 100L
        val events = listOf(eventAtDay(today - 5), eventAtDay(today - 10), eventAtDay(today - 40))

        val result = computeTrendStats(events, now = millisAtDay(today), spanDays = TREND_MIN_SPAN_DAYS)

        assertEquals(TrendDirection.UP, result!!.direction)
        assertEquals(2, result.recentCount)
        assertEquals(1, result.priorCount)
    }

    @Test
    fun `computeTrendStats reports DOWN when the recent window has fewer events than the prior one`() {
        val today = 100L
        val events = listOf(eventAtDay(today - 5), eventAtDay(today - 40), eventAtDay(today - 45))

        val result = computeTrendStats(events, now = millisAtDay(today), spanDays = TREND_MIN_SPAN_DAYS)

        assertEquals(TrendDirection.DOWN, result!!.direction)
    }

    @Test
    fun `computeTrendStats reports FLAT when both windows have equal counts`() {
        val today = 100L
        val events = listOf(eventAtDay(today - 5), eventAtDay(today - 40))

        val result = computeTrendStats(events, now = millisAtDay(today), spanDays = TREND_MIN_SPAN_DAYS)

        assertEquals(TrendDirection.FLAT, result!!.direction)
    }

    // ---- computeDurationStats ----

    @Test
    fun `computeDurationStats is null when no event has a recorded duration`() {
        val events = listOf(eventAt(millisAtDay(0)), eventAt(millisAtDay(1)))

        assertNull(computeDurationStats(events))
    }

    @Test
    fun `computeDurationStats averages, maxes and totals minutes across durationed events`() {
        val start = millisAtDay(0)
        val events =
            listOf(
                eventAt(start, endedAt = start + 10 * 60_000L),
                eventAt(start, endedAt = start + 30 * 60_000L),
                eventAt(start), // no duration, excluded
            )

        val result = computeDurationStats(events)!!

        assertEquals(20.0, result.averageMinutes, 0.0001)
        assertEquals(30L, result.longestMinutes)
        assertEquals(40L, result.totalMinutes)
    }

    @Test
    fun `computeDurationStats ignores several concurrent still-running events`() {
        val start = millisAtDay(0)
        val events =
            listOf(
                eventAt(start, endedAt = start + 15 * 60_000L),
                eventAt(start), // running, excluded
                eventAt(start + 60_000L), // running, excluded
            )

        val result = computeDurationStats(events)!!

        assertEquals(15.0, result.averageMinutes, 0.0001)
        assertEquals(15L, result.longestMinutes)
        assertEquals(15L, result.totalMinutes)
    }

    // ---- computeIntensityStats ----

    @Test
    fun `computeIntensityStats is null when no event has a recorded intensity`() {
        assertNull(computeIntensityStats(listOf(eventAt(millisAtDay(0)))))
    }

    @Test
    fun `computeIntensityStats averages intensity and fills the 1 to 5 distribution`() {
        val events =
            listOf(
                eventAt(millisAtDay(0), intensity = 2),
                eventAt(millisAtDay(1), intensity = 2),
                eventAt(millisAtDay(2), intensity = 5),
                eventAt(millisAtDay(3)), // no intensity, excluded
            )

        val result = computeIntensityStats(events)!!

        assertEquals(3.0, result.averageIntensity, 0.0001)
        assertEquals(mapOf(1 to 0, 2 to 2, 3 to 0, 4 to 0, 5 to 1), result.distribution)
    }

    // ---- computeTagBreakdown ----

    @Test
    fun `computeTagBreakdown counts tags across events, busiest first`() {
        val standup = TagEntity(id = 1, name = "standup")
        val weekend = TagEntity(id = 2, name = "weekend")
        val eventsWithTags =
            listOf(
                EventWithTags(eventAt(millisAtDay(0)), listOf(standup)),
                EventWithTags(eventAt(millisAtDay(1)), listOf(standup, weekend)),
                EventWithTags(eventAt(millisAtDay(2)), emptyList()),
            )

        val result = computeTagBreakdown(eventsWithTags)

        assertEquals(listOf(TagBreakdownEntry("standup", 2), TagBreakdownEntry("weekend", 1)), result)
    }

    @Test
    fun `computeTagBreakdown is empty when no event carries a tag`() {
        val eventsWithTags = listOf(EventWithTags(eventAt(millisAtDay(0)), emptyList()))

        assertTrue(computeTagBreakdown(eventsWithTags).isEmpty())
    }
}
