package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.testsupport.TEST_ZONE
import com.secondmonday.hodith.testsupport.millisAt
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
    fun `computeTagShareShift finds nothing when only one of the two threshold floors is cleared`() {
        // prior 3 of 5 (0.6), recent 4 of 5 (0.8): delta 0.2 clears TAG_SHARE_SHIFT_MIN_ABSOLUTE_FRACTION
        // (0.15), but the relative move (0.2 / 0.6 = 0.33) doesn't clear TAG_SHARE_SHIFT_MIN_RELATIVE_FRACTION
        // (0.5) -- both floors must clear, not just one.
        val standing = TagEntity(id = 5, name = "standing")
        val eventsWithTags =
            (0 until 10).map { day ->
                val tagged = day in setOf(0, 1, 2) || day in 5..8
                EventWithTags(eventAtDay(day.toLong()), if (tagged) listOf(standing) else emptyList())
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
    fun `computeTagOutcomeFindings excludes a duration effect landing exactly at the significance boundary`() {
        // A permutation p-value of exactly TAG_OUTCOME_SIGNIFICANCE_ALPHA (0.05, the 50th-most-extreme
        // of 1000 shuffles) is excluded, not included -- the `>=` in tagOutcomeResultFor treats the
        // boundary itself as "not significant". This exact composition (9 high/6 low minutes tagged,
        // 9 high/21 low minutes untagged, both duration) was found empirically to land exactly on that
        // boundary for this fixed (caseId 1, "aura", DURATION, 45-sample) permutation seed.
        val highMinutes = 101L
        val lowMinutes = 1L
        val tagged =
            (List(9) { highMinutes } + List(6) { lowMinutes }).mapIndexed { index, minutes ->
                val occurredAt = millisAtDay(index.toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + minutes * MILLIS_PER_MINUTE), listOf(aura))
            }
        val untagged =
            (List(9) { highMinutes } + List(21) { lowMinutes }).mapIndexed { index, minutes ->
                val occurredAt = millisAtDay((index + 100).toLong())
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + minutes * MILLIS_PER_MINUTE), emptyList())
            }

        assertTrue(computeTagOutcomeFindings(tagged + untagged).none { it.tagName == "aura" })
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

    // ---- computeTrendSlopeFindings ----

    /** A time-ordered step: the first half of [count] events at [earlyIntensity], the second half at [lateIntensity]. */
    private fun intensitySlopeEventsWithTags(
        earlyIntensity: Int,
        lateIntensity: Int,
        count: Int = TREND_SLOPE_MIN_SAMPLE_COUNT + 8,
    ): List<EventWithTags> {
        val mid = count / 2
        return (0 until count).map { day ->
            val intensity = if (day < mid) earlyIntensity else lateIntensity
            EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = intensity), emptyList())
        }
    }

    @Test
    fun `computeTrendSlopeFindings reports a strong upward intensity slope as significant`() {
        val eventsWithTags = intensitySlopeEventsWithTags(earlyIntensity = 1, lateIntensity = 5)

        val result = computeTrendSlopeFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY))

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals(TagOutcome.INTENSITY, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(1.0, finding.priorValue, 0.0001)
        assertEquals(5.0, finding.recentValue, 0.0001)
        assertEquals(eventsWithTags.size, finding.sampleCount)
    }

    @Test
    fun `computeTrendSlopeFindings reports a DOWN direction when intensity falls over time`() {
        val eventsWithTags = intensitySlopeEventsWithTags(earlyIntensity = 5, lateIntensity = 1)

        val finding = computeTrendSlopeFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).single()

        assertEquals(ShiftDirection.DOWN, finding.direction)
    }

    @Test
    fun `computeTrendSlopeFindings is empty below the minimum sample count, even with a stark slope`() {
        val eventsWithTags =
            intensitySlopeEventsWithTags(earlyIntensity = 1, lateIntensity = 5, count = TREND_SLOPE_MIN_SAMPLE_COUNT - 1)

        assertTrue(computeTrendSlopeFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).isEmpty())
    }

    @Test
    fun `computeTrendSlopeFindings is empty when intensity stays flat over time`() {
        val eventsWithTags = intensitySlopeEventsWithTags(earlyIntensity = 3, lateIntensity = 3)

        assertTrue(computeTrendSlopeFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).isEmpty())
    }

    @Test
    fun `computeTrendSlopeFindings suppresses an outcome not in eligibleOutcomes`() {
        val eventsWithTags = intensitySlopeEventsWithTags(earlyIntensity = 1, lateIntensity = 5)

        assertTrue(computeTrendSlopeFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.DURATION)).isEmpty())
    }

    @Test
    fun `computeTrendSlopeFindings can report both an intensity and a duration slope at once`() {
        val count = TREND_SLOPE_MIN_SAMPLE_COUNT + 8
        val mid = count / 2
        val eventsWithTags =
            (0 until count).map { day ->
                val occurredAt = millisAtDay(day.toLong())
                val intensity = if (day < mid) 1 else 5
                val minutes = if (day < mid) 30L else 90L
                EventWithTags(eventAt(occurredAt, endedAt = occurredAt + minutes * MILLIS_PER_MINUTE, intensity = intensity), emptyList())
            }

        val result = computeTrendSlopeFindings(eventsWithTags, eligibleOutcomes = TagOutcome.entries.toSet())

        assertEquals(2, result.size)
        assertEquals(setOf(TagOutcome.INTENSITY, TagOutcome.DURATION), result.map { it.outcome }.toSet())
    }

    @Test
    fun `computeTrendSlopeFindings excludes events with no recorded intensity from the series`() {
        val count = TREND_SLOPE_MIN_SAMPLE_COUNT + 8
        val mid = count / 2
        val slopeEvents =
            (0 until count).map { day ->
                val intensity = if (day < mid) 1 else 5
                EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = intensity), emptyList())
            }
        val noIntensityEvents =
            (100 until 105).map { day -> EventWithTags(eventAt(millisAtDay(day.toLong()), intensity = null), emptyList()) }

        val finding = computeTrendSlopeFindings(slopeEvents + noIntensityEvents, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).single()

        assertEquals(count, finding.sampleCount)
    }

    // ---- computeTimeOfDaySplitFindings ----

    private fun eventAtHour(
        day: Long,
        hour: Int,
        intensity: Int? = null,
    ) = eventAt(millisAt(day, hour), intensity = intensity)

    /**
     * [dayCount] events at [hour] 9 (MORNING) with [dayIntensity], [eveningCount] events at hour 19
     * (EVENING) with [eveningIntensity] — spread across distinct days so no two events land at the
     * exact same instant.
     */
    private fun timeOfDaySplitEventsWithTags(
        dayIntensity: Int,
        eveningIntensity: Int,
        dayCount: Int = TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT + 5,
        eveningCount: Int = TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT + 5,
    ): List<EventWithTags> {
        val dayEvents =
            (0 until dayCount).map { i ->
                EventWithTags(eventAtHour(i.toLong(), hour = 9, intensity = dayIntensity), emptyList())
            }
        val eveningEvents =
            (0 until eveningCount).map { i ->
                EventWithTags(eventAtHour((i + 1000).toLong(), hour = 19, intensity = eveningIntensity), emptyList())
            }
        return dayEvents + eveningEvents
    }

    @Test
    fun `computeTimeOfDaySplitFindings reports evening running more intense than day`() {
        val eventsWithTags = timeOfDaySplitEventsWithTags(dayIntensity = 1, eveningIntensity = 5)

        val result = computeTimeOfDaySplitFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY))

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals(TagOutcome.INTENSITY, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(1.0, finding.dayMean, 0.0001)
        assertEquals(5.0, finding.eveningMean, 0.0001)
        assertEquals(eventsWithTags.size, finding.sampleCount)
    }

    @Test
    fun `computeTimeOfDaySplitFindings reports a DOWN direction when day events run more intense`() {
        val eventsWithTags = timeOfDaySplitEventsWithTags(dayIntensity = 5, eveningIntensity = 1)

        val finding = computeTimeOfDaySplitFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).single()

        assertEquals(ShiftDirection.DOWN, finding.direction)
    }

    @Test
    fun `computeTimeOfDaySplitFindings folds NIGHT hours into the evening group, not the day group`() {
        val dayCount = TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT + 5
        val nightCount = TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT + 5
        val dayEvents = (0 until dayCount).map { i -> EventWithTags(eventAtHour(i.toLong(), hour = 10, intensity = 1), emptyList()) }
        val nightEvents =
            (0 until nightCount).map { i -> EventWithTags(eventAtHour((i + 1000).toLong(), hour = 1, intensity = 5), emptyList()) }

        val finding =
            computeTimeOfDaySplitFindings(dayEvents + nightEvents, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).single()

        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(1.0, finding.dayMean, 0.0001)
        assertEquals(5.0, finding.eveningMean, 0.0001)
    }

    @Test
    fun `computeTimeOfDaySplitFindings is empty when either group is below the minimum group sample count`() {
        val eventsWithTags =
            timeOfDaySplitEventsWithTags(
                dayIntensity = 1,
                eveningIntensity = 5,
                eveningCount = TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT - 1,
            )

        assertTrue(computeTimeOfDaySplitFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).isEmpty())
    }

    @Test
    fun `computeTimeOfDaySplitFindings is empty when day and evening intensity don't differ`() {
        val eventsWithTags = timeOfDaySplitEventsWithTags(dayIntensity = 3, eveningIntensity = 3)

        assertTrue(computeTimeOfDaySplitFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.INTENSITY)).isEmpty())
    }

    @Test
    fun `computeTimeOfDaySplitFindings suppresses an outcome not in eligibleOutcomes`() {
        val eventsWithTags = timeOfDaySplitEventsWithTags(dayIntensity = 1, eveningIntensity = 5)

        assertTrue(computeTimeOfDaySplitFindings(eventsWithTags, eligibleOutcomes = setOf(TagOutcome.DURATION)).isEmpty())
    }

    // ---- computeTagTimingFindings ----

    private val focus = TagEntity(id = 2, name = "focus")

    @Test
    fun `computeTagTimingFindings reports a tag clustering in one time-of-day bucket`() {
        val hours = listOf(9, 14, 19, 1) // MORNING, AFTERNOON, EVENING, NIGHT
        val untagged =
            hours.flatMap { hour ->
                (0 until 20).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged = (0 until 20).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = 19), listOf(focus)) }

        val result = computeTagTimingFindings(untagged + tagged)

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals("focus", finding.tagName)
        assertEquals(TagTimingDimension.TIME_OF_DAY, finding.dimension)
        assertEquals(TimeOfDay.EVENING, finding.timeOfDay)
        assertEquals(0.40, finding.baselineShare, 0.0001)
        assertEquals(1.0, finding.taggedShare, 0.0001)
        assertEquals(20, finding.sampleCount)
    }

    @Test
    fun `computeTagTimingFindings is empty when a tag's time-of-day distribution matches the Case's own rhythm`() {
        val hours = listOf(9, 14, 19, 1)
        val untagged =
            hours.flatMap { hour ->
                (0 until 20).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged =
            hours.flatMap { hour ->
                (0 until 5).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = hour), listOf(focus)) }
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).none { it.tagName == "focus" })
    }

    @Test
    fun `computeTagTimingFindings is empty when the peak deviation doesn't clear the descriptive floor`() {
        val hours = listOf(9, 14, 19, 1)
        val untagged =
            hours.flatMap { hour ->
                (0 until 20).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged =
            listOf(19 to 6, 9 to 3, 14 to 3, 1 to 3).flatMap { (hour, count) ->
                (0 until count).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = hour), listOf(focus)) }
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).none { it.tagName == "focus" })
    }

    @Test
    fun `computeTagTimingFindings is empty when the peak deviation clears the absolute floor but not the relative one`() {
        // EVENING is already the pool's dominant bucket (baseline share ~0.392) before any tag
        // involvement; tagging nudges it further (delta ~0.158, clearing TAG_TIMING_MIN_ABSOLUTE_SHARE_DIFFERENCE
        // on its own), but that's still under 50% relative to the already-high baseline (~0.404
        // relative), so the dual floor correctly rejects it even though the absolute half alone would pass.
        val untagged =
            listOf(9 to 21, 14 to 21, 1 to 22, 19 to 36).flatMap { (hour, count) ->
                (0 until count).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged =
            listOf(9 to 3, 14 to 3, 1 to 3, 19 to 11).flatMap { (hour, count) ->
                (0 until count).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = hour), listOf(focus)) }
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).none { it.tagName == "focus" })
    }

    @Test
    fun `computeTagTimingFindings is empty when the peak deviation clears the relative floor but not the absolute one`() {
        // EVENING starts as the pool's least-common bucket (baseline share 0.10); tagging doubles the
        // tag's own share there (relative 1.0, clearing TAG_TIMING_MIN_RELATIVE_SHARE_DIFFERENCE), but
        // the raw point gain (delta 0.10) stays under TAG_TIMING_MIN_ABSOLUTE_SHARE_DIFFERENCE's 15-point floor.
        val untagged =
            listOf(9 to 31, 14 to 31, 1 to 30, 19 to 8).flatMap { (hour, count) ->
                (0 until count).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged =
            listOf(9 to 5, 14 to 5, 1 to 6, 19 to 4).flatMap { (hour, count) ->
                (0 until count).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = hour), listOf(focus)) }
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).none { it.tagName == "focus" })
    }

    @Test
    fun `computeTagTimingFindings finds nothing for a real but insignificant time-of-day skew`() {
        // EVENING is already the pool's busiest bucket before any tag involvement (25 of 80 events,
        // 31% vs a uniform 25%) -- a tagged subset moderately concentrated there (10 of 20, clearing
        // both descriptive floors on its own) is still the kind of skew random 20-of-80 draws against
        // this same lopsided pool turn up somewhat often, unlike the single-bucket, ~100%-concentration
        // planted-clustering test above. Exercises the permutation gate itself, not just the
        // descriptive floor -- every other null test here fails the floor before permutation runs at all.
        val hours = listOf(9, 14, 19, 1)
        val untagged =
            hours.flatMap { hour ->
                (0 until 15).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged =
            listOf(19 to 10, 9 to 4, 14 to 3, 1 to 3).flatMap { (hour, count) ->
                (0 until count).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = hour), listOf(focus)) }
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).none { it.tagName == "focus" })
    }

    @Test
    fun `computeTagTimingFindings is empty below the minimum tagged sample count for time-of-day, even with a stark effect`() {
        val hours = listOf(9, 14, 19, 1)
        val untagged =
            hours.flatMap { hour ->
                (0 until 20).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged =
            (0 until TAG_TIMING_MIN_TAGGED_SAMPLE_COUNT_TIME_OF_DAY - 1).map { i ->
                EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = 19), listOf(focus))
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).isEmpty())
    }

    @Test
    fun `computeTagTimingFindings is empty below the minimum case sample count for time-of-day, even with a stark effect`() {
        val hours = listOf(9, 14, 19, 1)
        val untagged =
            hours.flatMap { hour ->
                (0 until 2).map { i -> EventWithTags(eventAtHour((i * 4).toLong(), hour = hour), emptyList()) }
            }
        val tagged = (0 until 20).map { i -> EventWithTags(eventAtHour((i * 4 + 1000).toLong(), hour = 19), listOf(focus)) }

        assertTrue(computeTagTimingFindings(untagged + tagged).isEmpty())
    }

    @Test
    fun `computeTagTimingFindings reports a tag clustering on one weekday`() {
        // epochDay 0 (1970-01-01) is a Thursday; residues of 7 keep every event in a group on the same weekday.
        val untagged =
            (0 until 7).flatMap { residue ->
                (0 until 10).map { i -> EventWithTags(eventAtHour(residue + 7L * i, hour = 12), emptyList()) }
            }
        val tagged = (0 until 30).map { i -> EventWithTags(eventAtHour(7L * (i + 1000), hour = 12), listOf(focus)) }

        val result = computeTagTimingFindings(untagged + tagged)

        assertEquals(1, result.size)
        val finding = result.single()
        assertEquals("focus", finding.tagName)
        assertEquals(TagTimingDimension.WEEKDAY, finding.dimension)
        assertEquals(DayOfWeek.THURSDAY, finding.weekday)
        assertEquals(0.40, finding.baselineShare, 0.0001)
        assertEquals(1.0, finding.taggedShare, 0.0001)
        assertEquals(30, finding.sampleCount)
    }

    @Test
    fun `computeTagTimingFindings is empty when a tag's weekday distribution matches the Case's own rhythm`() {
        val untagged =
            (0 until 7).flatMap { residue ->
                (0 until 10).map { i -> EventWithTags(eventAtHour(residue + 7L * i, hour = 12), emptyList()) }
            }
        val tagged =
            (0 until 7).flatMap { residue ->
                (0 until 5).map { i -> EventWithTags(eventAtHour(residue + 7L * (i + 1000), hour = 12), listOf(focus)) }
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).none { it.tagName == "focus" })
    }

    @Test
    fun `computeTagTimingFindings is empty below the minimum tagged sample count for weekday, even with a stark effect`() {
        val untagged =
            (0 until 7).flatMap { residue ->
                (0 until 10).map { i -> EventWithTags(eventAtHour(residue + 7L * i, hour = 12), emptyList()) }
            }
        val tagged =
            (0 until TAG_TIMING_MIN_TAGGED_SAMPLE_COUNT_WEEKDAY - 1).map { i ->
                EventWithTags(eventAtHour(7L * (i + 1000), hour = 12), listOf(focus))
            }

        assertTrue(computeTagTimingFindings(untagged + tagged).isEmpty())
    }

    @Test
    fun `computeTagTimingFindings is empty below the minimum case sample count for weekday, even with a stark effect`() {
        val untagged =
            (0 until 7).flatMap { residue ->
                (0 until 2).map { i -> EventWithTags(eventAtHour(residue + 7L * i, hour = 12), emptyList()) }
            }
        val tagged = (0 until 30).map { i -> EventWithTags(eventAtHour(7L * (i + 1000), hour = 12), listOf(focus)) }

        assertTrue(computeTagTimingFindings(untagged + tagged).isEmpty())
    }

    @Test
    fun `computeTagTimingFindings orders results by effect size, strongest first`() {
        val chore = TagEntity(id = 3, name = "chore")
        val untagged =
            (0 until 7).flatMap { residue ->
                (0 until 10).map { i -> EventWithTags(eventAtHour(residue + 7L * i, hour = 12), emptyList()) }
            }
        val focusTagged = (0 until 30).map { i -> EventWithTags(eventAtHour(7L * (i + 1000), hour = 12), listOf(focus)) }
        val choreTagged = (0 until 29).map { i -> EventWithTags(eventAtHour(1 + 7L * (i + 2000), hour = 12), listOf(chore)) }

        val result = computeTagTimingFindings(untagged + focusTagged + choreTagged)

        assertEquals(2, result.size)
        assertEquals(result.sortedByDescending { it.taggedShare - it.baselineShare }, result)
    }

    @Test
    fun `computeTagTimingFindings caps findings at TAG_TIMING_MAX_FINDINGS`() {
        val chore = TagEntity(id = 3, name = "chore")
        val solo = TagEntity(id = 4, name = "solo")
        val extra = TagEntity(id = 5, name = "extra")
        val untagged =
            (0 until 7).flatMap { residue ->
                (0 until 10).map { i -> EventWithTags(eventAtHour(residue + 7L * i, hour = 12), emptyList()) }
            }
        val tagged =
            listOf(focus to 0, chore to 1, solo to 2, extra to 3).flatMap { (tag, residue) ->
                (0 until 30).map { i ->
                    EventWithTags(eventAtHour(residue + 7L * (i + 1000 * (residue + 1)), hour = 12), listOf(tag))
                }
            }

        val result = computeTagTimingFindings(untagged + tagged)

        assertEquals(TAG_TIMING_MAX_FINDINGS, result.size)
    }

    // ---- computeWeekdayWeekendFindings ----

    /** epochDay 0 (1970-01-01) is a Thursday, so residues 2/3 land on Saturday/Sunday, 0/1/4/5/6 on a weekday. */
    private fun weekdayWeekendEvents(
        weekendCount: Int,
        weekdayCount: Int,
    ): List<EventWithTags> {
        val weekendResidues = listOf(2L, 3L)
        val weekdayResidues = listOf(0L, 1L, 4L, 5L, 6L)
        val weekendEvents =
            (0 until weekendCount).map { i ->
                EventWithTags(eventAtDay(weekendResidues[i % weekendResidues.size] + 7L * i), emptyList())
            }
        val weekdayEvents =
            (0 until weekdayCount).map { i ->
                EventWithTags(eventAtDay(weekdayResidues[i % weekdayResidues.size] + 7L * (i + 10_000)), emptyList())
            }
        return weekendEvents + weekdayEvents
    }

    @Test
    fun `computeWeekdayWeekendFindings reports a Case whose events cluster on weekends`() {
        val eventsWithTags = weekdayWeekendEvents(weekendCount = 60, weekdayCount = 40)

        val finding = computeWeekdayWeekendFindings(eventsWithTags)

        assertEquals(ShiftDirection.UP, finding?.direction)
        assertEquals(WEEKDAY_WEEKEND_BASELINE_SHARE, finding?.baselineShare ?: 0.0, 0.0001)
        assertEquals(0.60, finding?.observedShare ?: 0.0, 0.0001)
        assertEquals(100, finding?.sampleCount)
    }

    @Test
    fun `computeWeekdayWeekendFindings reports a Case whose events cluster on weekdays`() {
        val eventsWithTags = weekdayWeekendEvents(weekendCount = 5, weekdayCount = 95)

        val finding = computeWeekdayWeekendFindings(eventsWithTags)

        assertEquals(ShiftDirection.DOWN, finding?.direction)
        assertEquals(0.05, finding?.observedShare ?: 0.0, 0.0001)
        assertEquals(100, finding?.sampleCount)
    }

    @Test
    fun `computeWeekdayWeekendFindings is null when the weekend share matches the calendar baseline`() {
        // 40 of 140 events on a weekend is exactly 2/7 -- zero deviation from the fixed baseline.
        val eventsWithTags = weekdayWeekendEvents(weekendCount = 40, weekdayCount = 100)

        assertNull(computeWeekdayWeekendFindings(eventsWithTags))
    }

    @Test
    fun `computeWeekdayWeekendFindings is null below the minimum sample count, even with a stark effect`() {
        val eventsWithTags = weekdayWeekendEvents(weekendCount = WEEKDAY_WEEKEND_MIN_SAMPLE_COUNT - 1, weekdayCount = 0)

        assertNull(computeWeekdayWeekendFindings(eventsWithTags))
    }

    @Test
    fun `computeWeekdayWeekendFindings is null when the deviation doesn't clear the descriptive floor`() {
        // 35 of 100 events on a weekend (0.35) is only ~0.064 above the 2/7 (~0.286) baseline --
        // under WEEKDAY_WEEKEND_MIN_ABSOLUTE_SHARE_DIFFERENCE's 15-point floor.
        val eventsWithTags = weekdayWeekendEvents(weekendCount = 35, weekdayCount = 65)

        assertNull(computeWeekdayWeekendFindings(eventsWithTags))
    }

    @Test
    fun `computeWeekdayWeekendFindings is null when the deviation clears the relative floor but not the absolute one`() {
        // 432 of 1000 events on a weekend (0.432) is ~0.146 above the 2/7 (~0.2857) baseline --
        // that's ~51% relative (clearing WEEKDAY_WEEKEND_MIN_RELATIVE_SHARE_DIFFERENCE's 50% floor),
        // but the raw point gain stays under WEEKDAY_WEEKEND_MIN_ABSOLUTE_SHARE_DIFFERENCE's 15-point
        // floor. Unlike tag timing's own per-bucket baseline, this detector's baseline is fixed
        // (2/7), so the reverse split -- clearing the absolute floor but not the relative one -- is
        // structurally unreachable here: 0.15 already exceeds 0.5 * 2/7 (~0.1429), so clearing the
        // absolute floor always clears the relative one too.
        val eventsWithTags = weekdayWeekendEvents(weekendCount = 432, weekdayCount = 568)

        assertNull(computeWeekdayWeekendFindings(eventsWithTags))
    }
}
