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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

    @Test
    fun `computeFrequencyStats buckets an event by its own captured offset, not the device's current zone`() {
        // 2026-01-05T23:30Z: still Jan 5 under UTC, but already Jan 6 under a +9h offset (Tokyo).
        val eventInstant = Instant.parse("2026-01-05T23:30:00Z").toEpochMilli()
        val event = eventAt(eventInstant).copy(utcOffsetMinutes = 9 * 60)
        val now = Instant.parse("2026-01-06T01:00:00Z").toEpochMilli()

        val result =
            computeFrequencyStats(listOf(event), now = now, spanDays = 10L, granularity = FrequencyGranularity.DAY, zone = ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 1, 6), result.buckets.last().periodStart)
        assertEquals(1, result.buckets.last().count)
        assertEquals(0, result.buckets[result.buckets.size - 2].count)
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

    @Test
    fun `computeRhythmStats resolves an event's day-of-week and time-of-day via its own captured offset`() {
        // 2026-01-05T23:30Z is Monday night under UTC, but already Tuesday morning under a +9h offset.
        val eventInstant = Instant.parse("2026-01-05T23:30:00Z").toEpochMilli()
        val event = eventAt(eventInstant).copy(utcOffsetMinutes = 9 * 60)

        val result = computeRhythmStats(listOf(event))

        val tuesdayMorning = result.cells.single { it.dayOfWeek == DayOfWeek.TUESDAY && it.timeOfDay == TimeOfDay.MORNING }
        val mondayNight = result.cells.single { it.dayOfWeek == DayOfWeek.MONDAY && it.timeOfDay == TimeOfDay.NIGHT }
        assertEquals(1, tuesdayMorning.count)
        assertEquals(0, mondayNight.count)
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

    // ---- computeTagShareShift ----

    @Test
    fun `computeTagShareShift detects a tag rising between the earlier and more recent half`() {
        val decaf = TagEntity(id = 1, name = "decaf")
        val eventsWithTags =
            (0 until 10).map { day ->
                val tagged = day == 4 || day in 5..8 // prior 1 of 5, recent 4 of 5
                EventWithTags(eventAtDay(day.toLong()), if (tagged) listOf(decaf) else emptyList())
            }

        val result = computeTagShareShift(eventsWithTags)

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals("decaf", finding.tagName)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(0.2, finding.priorShare, 0.0001)
        assertEquals(0.8, finding.recentShare, 0.0001)
        assertEquals(10, finding.sampleCount)
    }

    @Test
    fun `computeTagShareShift detects a tag falling between the earlier and more recent half`() {
        val soda = TagEntity(id = 2, name = "soda")
        val eventsWithTags =
            (0 until 10).map { day ->
                val tagged = day in 0..3 || day == 5 // prior 4 of 5, recent 1 of 5
                EventWithTags(eventAtDay(day.toLong()), if (tagged) listOf(soda) else emptyList())
            }

        val result = computeTagShareShift(eventsWithTags)

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals("soda", finding.tagName)
        assertEquals(ShiftDirection.DOWN, finding.direction)
        assertEquals(0.8, finding.priorShare, 0.0001)
        assertEquals(0.2, finding.recentShare, 0.0001)
    }

    @Test
    fun `computeTagShareShift finds nothing for a tag whose share hasn't moved`() {
        val standup = TagEntity(id = 3, name = "standup")
        val eventsWithTags =
            (0 until 10).map { day ->
                val tagged = day in setOf(0, 1, 5, 6) // 2 of 5 in each half
                EventWithTags(eventAtDay(day.toLong()), if (tagged) listOf(standup) else emptyList())
            }

        assertTrue(computeTagShareShift(eventsWithTags).isEmpty())
    }

    @Test
    fun `computeTagShareShift is empty below the minimum sample count`() {
        val decaf = TagEntity(id = 1, name = "decaf")
        val eventsWithTags =
            (0 until TAG_SHARE_SHIFT_MIN_SAMPLE_COUNT - 1).map { day ->
                EventWithTags(eventAtDay(day.toLong()), listOf(decaf))
            }

        assertTrue(computeTagShareShift(eventsWithTags).isEmpty())
    }

    @Test
    fun `computeTagShareShift skips a tag with too few total occurrences even if its share swings sharply`() {
        val rare = TagEntity(id = 4, name = "rare")
        val eventsWithTags =
            (0 until 10).map { day ->
                val tagged = day == 5 || day == 6 // recent-only, but only 2 total -- below TAG_SHARE_SHIFT_MIN_TAG_COUNT
                EventWithTags(eventAtDay(day.toLong()), if (tagged) listOf(rare) else emptyList())
            }

        assertTrue(computeTagShareShift(eventsWithTags).isEmpty())
    }

    @Test
    fun `computeTagShareShift caps findings and orders them by effect size, strongest first`() {
        val tagA = TagEntity(id = 1, name = "tagA")
        val tagB = TagEntity(id = 2, name = "tagB")
        val tagC = TagEntity(id = 3, name = "tagC")
        val tagD = TagEntity(id = 4, name = "tagD")

        val eventsWithTags =
            (0 until 10).map { day ->
                val tags = mutableListOf<TagEntity>()
                if (day in 5..9) tags += tagA // prior 0 of 5, recent 5 of 5 -- delta 1.0
                if (day == 4 || day in 5..8) tags += tagB // prior 1 of 5, recent 4 of 5 -- delta 0.6
                if (day == 3 || day in 5..7) tags += tagC // prior 1 of 5, recent 3 of 5 -- delta 0.4
                if (day == 2 || day in 5..6) tags += tagD // prior 1 of 5, recent 2 of 5 -- delta 0.2
                EventWithTags(eventAtDay(day.toLong()), tags)
            }

        val result = computeTagShareShift(eventsWithTags)

        assertEquals(TAG_SHARE_SHIFT_MAX_FINDINGS, result.size)
        assertEquals(listOf("tagA", "tagB", "tagC"), result.map { it.tagName })
    }

    // ---- computeTagOutcomeFindings ----

    private val aura = TagEntity(id = 1, name = "aura")

    /** [TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT] events carrying [aura] with [taggedIntensity], [TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT] without it with [untaggedIntensity], both well past their minimums. */
    private fun intensityEventsWithTags(
        taggedIntensity: Int,
        untaggedIntensity: Int,
        taggedCount: Int = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5,
        untaggedCount: Int = TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT + 10,
    ): List<EventWithTags> {
        val tagged =
            (0 until taggedCount).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = taggedIntensity), listOf(aura))
            }
        val untagged =
            (taggedCount until taggedCount + untaggedCount).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = untaggedIntensity), emptyList())
            }
        return tagged + untagged
    }

    @Test
    fun `computeTagOutcomeFindings reports a strong, low-variance intensity effect as significant`() {
        val eventsWithTags = intensityEventsWithTags(taggedIntensity = 5, untaggedIntensity = 1)

        val result = computeTagOutcomeFindings(eventsWithTags)

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals("aura", finding.tagName)
        assertEquals(TagOutcome.INTENSITY, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(1.0, finding.withoutTagMean, 0.0001)
        assertEquals(5.0, finding.withTagMean, 0.0001)
        assertEquals(eventsWithTags.size, finding.sampleCount)
    }

    @Test
    fun `computeTagOutcomeFindings reports a DOWN direction when the tagged mean is lower`() {
        val eventsWithTags = intensityEventsWithTags(taggedIntensity = 1, untaggedIntensity = 5)

        val finding = computeTagOutcomeFindings(eventsWithTags).single()

        assertEquals(ShiftDirection.DOWN, finding.direction)
    }

    @Test
    fun `computeTagOutcomeFindings is empty when the relative difference doesn't clear the descriptive floor`() {
        // untagged mean 3.0, tagged mean 3.2 -- under TAG_OUTCOME_MIN_RELATIVE_DIFFERENCE (0.2).
        val eventsWithTags =
            (0 until TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = 3), listOf(aura))
            } +
                (0 until TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT + 10).map { day ->
                    EventWithTags(eventAt(millisAtDay((day + 100).toLong()), intensity = 3), emptyList())
                }

        assertTrue(computeTagOutcomeFindings(eventsWithTags).isEmpty())
    }

    @Test
    fun `computeTagOutcomeFindings is empty below the minimum tagged sample count, even with a stark effect`() {
        val eventsWithTags =
            intensityEventsWithTags(
                taggedIntensity = 5,
                untaggedIntensity = 1,
                taggedCount = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT - 1,
            )

        assertTrue(computeTagOutcomeFindings(eventsWithTags).isEmpty())
    }

    @Test
    fun `computeTagOutcomeFindings is empty below the minimum untagged sample count, even with a stark effect`() {
        val eventsWithTags =
            intensityEventsWithTags(
                taggedIntensity = 5,
                untaggedIntensity = 1,
                untaggedCount = TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT - 1,
            )

        assertTrue(computeTagOutcomeFindings(eventsWithTags).isEmpty())
    }

    @Test
    fun `computeTagOutcomeFindings finds nothing for a null effect with real, shared variance`() {
        // A pool of 45 events split evenly across intensity 1..5 (9 each) -- a random 15-event
        // subset would typically carry ~3 of each value. This tagged subset (2,2,3,4,4) is only a
        // mild tilt away from that -- a plausible fluctuation, not an extreme partition -- and it
        // clears the 20% descriptive floor (tagged mean 3.4 vs the remaining untagged mean 2.8) only
        // by chance: the permutation test correctly finds plenty of equally-or-more-extreme
        // relabelings of the same pool, so it doesn't confirm significance.
        val taggedValues = listOf(1, 1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5)
        val poolCountPerValue = 9
        val untaggedValues =
            (1..5).flatMap { value -> List(poolCountPerValue - taggedValues.count { it == value }) { value } }
        val eventsWithTags =
            taggedValues.mapIndexed { index, intensity ->
                EventWithTags(eventAt(millisAtDay(index.toLong()), intensity = intensity), listOf(aura))
            } +
                untaggedValues.mapIndexed { index, intensity ->
                    EventWithTags(eventAt(millisAtDay((index + 100).toLong()), intensity = intensity), emptyList())
                }

        assertTrue(computeTagOutcomeFindings(eventsWithTags).none { it.tagName == "aura" })
    }

    @Test
    fun `computeTagOutcomeFindings caps findings and orders them by effect size, strongest first`() {
        val tagA = TagEntity(id = 1, name = "tagA")
        val tagB = TagEntity(id = 2, name = "tagB")
        val tagC = TagEntity(id = 3, name = "tagC")
        val tagD = TagEntity(id = 4, name = "tagD")
        val taggedCount = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5
        // Large relative to the four tagged groups combined, so each tag's own group counting as
        // part of every *other* tag's "untagged" comparison only nudges that baseline slightly --
        // keeps the four tags' effect sizes cleanly ordered rather than distorted by cross-tag
        // contamination (every event not carrying tag X counts as "untagged" for X, including events
        // that carry a different tag entirely).
        val baselineCount = 300

        fun taggedFor(
            tag: TagEntity,
            intensity: Int,
            offset: Int,
        ) = (0 until taggedCount).map { day ->
            EventWithTags(eventAt(millisAtDay((day + offset).toLong()), intensity = intensity), listOf(tag))
        }

        val baseline =
            (0 until baselineCount).map { day ->
                EventWithTags(eventAt(millisAtDay((day + 1000).toLong()), intensity = 1), emptyList())
            }
        val eventsWithTags =
            taggedFor(tagA, intensity = 5, offset = 0) +
                taggedFor(tagB, intensity = 4, offset = 100) +
                taggedFor(tagC, intensity = 3, offset = 200) +
                taggedFor(tagD, intensity = 2, offset = 300) + // still clears the 20% floor against the diluted baseline
                baseline

        val result = computeTagOutcomeFindings(eventsWithTags)

        assertEquals(TAG_OUTCOME_MAX_FINDINGS, result.size)
        assertEquals(listOf("tagA", "tagB", "tagC"), result.map { it.tagName })
    }

    /** As [intensityEventsWithTags], but for the DURATION outcome -- [taggedMinutes]/[untaggedMinutes] become each group's `endedAt - occurredAt`. */
    private fun durationEventsWithTags(
        taggedMinutes: Long,
        untaggedMinutes: Long,
        taggedCount: Int = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5,
        untaggedCount: Int = TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT + 10,
    ): List<EventWithTags> {
        val tagged =
            (0 until taggedCount).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + taggedMinutes * MILLIS_PER_MINUTE), listOf(aura))
            }
        val untagged =
            (taggedCount until taggedCount + untaggedCount).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + untaggedMinutes * MILLIS_PER_MINUTE), emptyList())
            }
        return tagged + untagged
    }

    @Test
    fun `computeTagOutcomeFindings reports a strong, low-variance duration effect as significant`() {
        val eventsWithTags = durationEventsWithTags(taggedMinutes = 90, untaggedMinutes = 30)

        val result = computeTagOutcomeFindings(eventsWithTags)

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals("aura", finding.tagName)
        assertEquals(TagOutcome.DURATION, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(30.0, finding.withoutTagMean, 0.0001)
        assertEquals(90.0, finding.withTagMean, 0.0001)
        assertEquals(eventsWithTags.size, finding.sampleCount)
    }

    @Test
    fun `computeTagOutcomeFindings excludes events with no recorded intensity from an intensity comparison`() {
        // 15 aura events carry intensity, 5 more aura events don't -- only the 15 should count.
        val validTagged =
            (0 until TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = 5), listOf(aura))
            }
        val noIntensityTagged =
            (100 until 105).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = null), listOf(aura))
            }
        val untagged =
            (200 until 200 + TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = 1), emptyList())
            }

        val finding = computeTagOutcomeFindings(validTagged + noIntensityTagged + untagged).single()

        assertEquals(5.0, finding.withTagMean, 0.0001)
        assertEquals(TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT, finding.sampleCount)
    }

    @Test
    fun `computeTagOutcomeFindings excludes a still-running event from a duration comparison`() {
        val validTagged =
            (0 until TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + 90 * MILLIS_PER_MINUTE), listOf(aura))
            }
        val stillRunningTagged =
            (100 until 105).map { day ->
                EventWithTags(eventAt(millisAtDay(day.toLong()), endedAt = null), listOf(aura))
            }
        val untagged =
            (200 until 200 + TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + 30 * MILLIS_PER_MINUTE), emptyList())
            }

        val finding = computeTagOutcomeFindings(validTagged + stillRunningTagged + untagged).single()

        assertEquals(90.0, finding.withTagMean, 0.0001)
        assertEquals(TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT, finding.sampleCount)
    }

    @Test
    fun `computeTagOutcomeFindings excludes a non-positive stored duration from a duration comparison`() {
        // A reversed endedAt (from a bad round-trip, spec §6/§10) -- excluded, not floored or counted as zero.
        val validTagged =
            (0 until TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + 90 * MILLIS_PER_MINUTE), listOf(aura))
            }
        val reversedTagged =
            (100 until 105).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt - 10 * MILLIS_PER_MINUTE), listOf(aura))
            }
        val untagged =
            (200 until 200 + TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + 30 * MILLIS_PER_MINUTE), emptyList())
            }

        val finding = computeTagOutcomeFindings(validTagged + reversedTagged + untagged).single()

        assertEquals(90.0, finding.withTagMean, 0.0001)
        assertEquals(TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT, finding.sampleCount)
    }

    @Test
    fun `computeTagOutcomeFindings can report both an intensity and a duration finding for the same tag`() {
        val taggedCount = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5
        val untaggedCount = TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT + 10
        val tagged =
            (0 until taggedCount).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + 90 * MILLIS_PER_MINUTE, intensity = 5), listOf(aura))
            }
        val untagged =
            (taggedCount until taggedCount + untaggedCount).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + 30 * MILLIS_PER_MINUTE, intensity = 1), emptyList())
            }

        val result = computeTagOutcomeFindings(tagged + untagged)

        assertEquals(2, result.size)
        assertEquals(setOf(TagOutcome.INTENSITY, TagOutcome.DURATION), result.map { it.outcome }.toSet())
        assertTrue(result.all { it.tagName == "aura" })
    }
}
