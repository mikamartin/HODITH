package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.testsupport.millisAt
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun gapStatsOf(pastGaps: List<Long>) =
    GapStats(
        currentGapDays = 0,
        longestGapDays = pastGaps.maxOrNull() ?: 0,
        isCurrentGapLongest = false,
        averageGapDays = if (pastGaps.isEmpty()) 0.0 else pastGaps.average(),
        isBursty = false,
        pastGaps = pastGaps,
    )

private fun wentQuietGapStatsOf(
    pastGaps: List<Long>,
    currentGapDays: Long,
) = GapStats(
    currentGapDays = currentGapDays,
    longestGapDays = maxOf(pastGaps.maxOrNull() ?: 0L, currentGapDays),
    isCurrentGapLongest = currentGapDays >= (pastGaps.maxOrNull() ?: 0L),
    averageGapDays = if (pastGaps.isEmpty()) 0.0 else pastGaps.average(),
    isBursty = false,
    pastGaps = pastGaps,
)

class TrendsEngineTest {
    // ---- computeTrendFindings: went quiet ----

    @Test
    fun `computeTrendFindings reports a went-quiet finding when the current gap is a record and the user is active elsewhere`() {
        val gapStats = wentQuietGapStatsOf(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 20L)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, recentlyActiveElsewhere = true)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(TrendFindingKind.WENT_QUIET, finding.kind)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.HINT, finding.reliability)
        assertEquals(QUIET_SIGNAL_MIN_SAMPLE_COUNT, finding.sampleCount)
        assertEquals(5.0, finding.priorValue, 0.0001)
        assertEquals(20.0, finding.recentValue, 0.0001)
    }

    @Test
    fun `computeTrendFindings omits the went-quiet finding when recentlyActiveElsewhere is omitted`() {
        val gapStats = wentQuietGapStatsOf(pastGaps = List(QUIET_SIGNAL_MIN_SAMPLE_COUNT) { 5L }, currentGapDays = 20L)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null)

        assertEquals(emptyList<TrendFinding>(), findings)
    }

    @Test
    fun `computeTrendFindings puts the went-quiet finding first when combined with a gap-shift finding`() {
        // Gap history shaped to trigger both: computeGapShift sees the widening 2,2,2 -> 10,10,10
        // split, and the current gap (15) beats every past gap, so isCurrentGapLongest is also true.
        val pastGaps = listOf(2L, 2L, 2L, 10L, 10L, 10L)
        val gapStats = wentQuietGapStatsOf(pastGaps = pastGaps, currentGapDays = 15L)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, recentlyActiveElsewhere = true)

        assertEquals(listOf(TrendFindingKind.WENT_QUIET, TrendFindingKind.GAP_SHIFT), findings.map { it.kind })
    }

    // ---- computeTrendFindings: gap/streak shift ----

    @Test
    fun `computeTrendFindings is empty when nothing shifts and there is no frequency trend`() {
        val flatGaps = gapStatsOf(List(GAP_SHIFT_MIN_SAMPLE_COUNT) { 5L })
        val flatDates = (0 until STREAK_SHIFT_MIN_SAMPLE_COUNT.toLong()).map { LocalDate.ofEpochDay(it * 10) }

        assertEquals(emptyList<TrendFinding>(), computeTrendFindings(flatGaps, flatDates, trendStats = null))
    }

    @Test
    fun `computeTrendFindings reports only a gap-shift finding when only the gap average shifts`() {
        // Past gaps: 2, 2, 2, 10, 10, 10 -- clearly widening; no streak data at all.
        val gapStats = gapStatsOf(listOf(2L, 2L, 2L, 10L, 10L, 10L))

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(TrendFindingKind.GAP_SHIFT, finding.kind)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.HINT, finding.reliability)
        assertEquals(6, finding.sampleCount)
        assertEquals(2.0, finding.priorValue, 0.0001)
        assertEquals(10.0, finding.recentValue, 0.0001)
    }

    @Test
    fun `computeTrendFindings reports only a streak-shift finding when only the streak length shifts`() {
        // First half: three 1-day runs. Second half: three 4-day runs. No gap data at all.
        val isolatedDays = listOf(0L, 10L, 20L)
        val longRunStarts = listOf(100L, 200L, 300L)
        val dates = (isolatedDays + longRunStarts.flatMap { start -> (start until start + 4) }).map { LocalDate.ofEpochDay(it) }

        val findings = computeTrendFindings(gapStatsOf(emptyList()), dates, trendStats = null)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(TrendFindingKind.STREAK_SHIFT, finding.kind)
        assertEquals(ShiftDirection.UP, finding.direction)
    }

    @Test
    fun `computeTrendFindings reports both findings when both the gap and streak shift`() {
        val gapStats = gapStatsOf(listOf(2L, 2L, 2L, 10L, 10L, 10L))
        val isolatedDays = listOf(0L, 10L, 20L)
        val longRunStarts = listOf(100L, 200L, 300L)
        val dates = (isolatedDays + longRunStarts.flatMap { start -> (start until start + 4) }).map { LocalDate.ofEpochDay(it) }

        val findings = computeTrendFindings(gapStats, dates, trendStats = null)

        assertEquals(setOf(TrendFindingKind.GAP_SHIFT, TrendFindingKind.STREAK_SHIFT), findings.map { it.kind }.toSet())
    }

    // ---- computeTrendFindings: frequency shift (the former standalone Trend arrow) ----

    private val noShiftGapStats = gapStatsOf(emptyList())
    private val noShiftDates = emptyList<LocalDate>()

    @Test
    fun `computeTrendFindings reports an UP frequency-shift finding with real counts`() {
        val trendStats = TrendStats(direction = TrendDirection.UP, recentCount = 12, priorCount = 5)

        val findings = computeTrendFindings(noShiftGapStats, noShiftDates, trendStats)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(TrendFindingKind.FREQUENCY_SHIFT, finding.kind)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.HINT, finding.reliability)
        assertEquals(17, finding.sampleCount)
        assertEquals(5.0, finding.priorValue, 0.0001)
        assertEquals(12.0, finding.recentValue, 0.0001)
    }

    @Test
    fun `computeTrendFindings reports a DOWN frequency-shift finding`() {
        val trendStats = TrendStats(direction = TrendDirection.DOWN, recentCount = 3, priorCount = 9)

        val finding = computeTrendFindings(noShiftGapStats, noShiftDates, trendStats).single()

        assertEquals(TrendFindingKind.FREQUENCY_SHIFT, finding.kind)
        assertEquals(ShiftDirection.DOWN, finding.direction)
    }

    @Test
    fun `computeTrendFindings omits a FLAT frequency trend entirely`() {
        val trendStats = TrendStats(direction = TrendDirection.FLAT, recentCount = 8, priorCount = 8)

        assertEquals(emptyList<TrendFinding>(), computeTrendFindings(noShiftGapStats, noShiftDates, trendStats))
    }

    @Test
    fun `computeTrendFindings omits frequency shift when trendStats is null`() {
        assertEquals(emptyList<TrendFinding>(), computeTrendFindings(noShiftGapStats, noShiftDates, trendStats = null))
    }

    @Test
    fun `computeTrendFindings combines a gap shift and a frequency shift`() {
        val gapStats = gapStatsOf(listOf(2L, 2L, 2L, 10L, 10L, 10L))
        val trendStats = TrendStats(direction = TrendDirection.UP, recentCount = 12, priorCount = 5)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats)

        assertEquals(setOf(TrendFindingKind.GAP_SHIFT, TrendFindingKind.FREQUENCY_SHIFT), findings.map { it.kind }.toSet())
    }

    // ---- computeTrendFindings: tag share shift ----

    private fun risingTagEventsWithTags(tagName: String = "decaf"): List<EventWithTags> {
        val tag = TagEntity(id = 1, name = tagName)
        return (0 until 10).map { day ->
            val tagged = day == 4 || day in 5..8 // prior 1 of 5, recent 4 of 5
            EventWithTags(testEvent(occurredAt = millisAtDay(day.toLong())), if (tagged) listOf(tag) else emptyList())
        }
    }

    @Test
    fun `computeTrendFindings reports a tag-share-shift finding with the tag name attached`() {
        val findings = computeTrendFindings(noShiftGapStats, noShiftDates, trendStats = null, eventsWithTags = risingTagEventsWithTags())

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(TrendFindingKind.TAG_SHARE_SHIFT, finding.kind)
        assertEquals("decaf", finding.tagName)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.HINT, finding.reliability)
    }

    @Test
    fun `computeTrendFindings is empty when no tag's share shifts and nothing else does either`() {
        val findings = computeTrendFindings(noShiftGapStats, noShiftDates, trendStats = null, eventsWithTags = emptyList())

        assertEquals(emptyList<TrendFinding>(), findings)
    }

    @Test
    fun `computeTrendFindings places tag-share-shift findings after gap shift`() {
        val gapStats = gapStatsOf(listOf(2L, 2L, 2L, 10L, 10L, 10L))

        val findings =
            computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, eventsWithTags = risingTagEventsWithTags())

        assertEquals(listOf(TrendFindingKind.GAP_SHIFT, TrendFindingKind.TAG_SHARE_SHIFT), findings.map { it.kind })
    }

    // ---- computeTrendFindings: recurrence shape ----

    // Ten short gaps at 2, two long ones at 20 -- one long gap in each literal half so
    // computeGapShift's first-half/second-half averages tie (5.0 vs 5.0) and stay silent, isolating
    // the recurrence-shape finding. Mean 5.0, threshold 2.5, 10 of 12 gaps landed early.
    private val recurrenceSpikePastGaps = List(5) { 2L } + 20L + List(5) { 2L } + 20L

    @Test
    fun `computeTrendFindings reports a recurrence-shape finding with real numbers`() {
        val gapStats = gapStatsOf(recurrenceSpikePastGaps)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null)

        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(TrendFindingKind.RECURRENCE_SHAPE, finding.kind)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.HINT, finding.reliability)
        assertEquals(12, finding.sampleCount)
        assertEquals(2.5, finding.priorValue, 0.0001)
        assertEquals(10.0 / 12.0, finding.recentValue, 0.0001)
    }

    @Test
    fun `computeTrendFindings places the recurrence-shape finding after tag share shift`() {
        val gapStats = gapStatsOf(recurrenceSpikePastGaps)

        val findings =
            computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, eventsWithTags = risingTagEventsWithTags())

        assertEquals(listOf(TrendFindingKind.TAG_SHARE_SHIFT, TrendFindingKind.RECURRENCE_SHAPE), findings.map { it.kind })
    }

    @Test
    fun `computeTrendFindings can report a recurrence-shape finding alongside went-quiet`() {
        // Current gap set as a fresh record on top of recurrenceSpikePastGaps, so went-quiet also
        // fires -- the two are independent claims and can appear on the same Case together.
        val gapStats = wentQuietGapStatsOf(pastGaps = recurrenceSpikePastGaps, currentGapDays = 25L)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, recentlyActiveElsewhere = true)

        assertEquals(setOf(TrendFindingKind.WENT_QUIET, TrendFindingKind.RECURRENCE_SHAPE), findings.map { it.kind }.toSet())
        assertEquals(TrendFindingKind.WENT_QUIET, findings.first().kind)
    }

    // ---- computeTrendFindings: tag outcome ----

    // Interleaved across the same day range (rather than tagged-early/untagged-late) so this doesn't
    // also read as a TAG_SHARE_SHIFT -- a tag confined to one half of a Case's history would trip
    // that detector too, which isn't what this fixture is meant to isolate.
    private fun strongTagOutcomeEventsWithTags(tagName: String = "aura"): List<EventWithTags> {
        val tag = TagEntity(id = 1, name = tagName)
        val taggedCount = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5
        val untaggedCount = TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT + 10
        val totalCount = taggedCount + untaggedCount
        return (0 until totalCount).map { day ->
            val tagged = day % 3 == 0 // roughly taggedCount of totalCount, evenly spread across the span
            val event = testEvent(occurredAt = millisAtDay(day.toLong()), intensity = if (tagged) 5 else 1)
            EventWithTags(event, if (tagged) listOf(tag) else emptyList())
        }
    }

    @Test
    fun `computeTrendFindings reports a tag-outcome finding as PATTERN with the tag name and outcome attached`() {
        val findings =
            computeTrendFindings(noShiftGapStats, noShiftDates, trendStats = null, eventsWithTags = strongTagOutcomeEventsWithTags())

        val finding = findings.single { it.kind == TrendFindingKind.TAG_OUTCOME }
        assertEquals("aura", finding.tagName)
        assertEquals(TagOutcome.INTENSITY, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.PATTERN, finding.reliability)
    }

    @Test
    fun `computeTrendFindings places the tag-outcome finding after recurrence shape`() {
        val gapStats = gapStatsOf(recurrenceSpikePastGaps)

        val findings =
            computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, eventsWithTags = strongTagOutcomeEventsWithTags())

        assertEquals(listOf(TrendFindingKind.RECURRENCE_SHAPE, TrendFindingKind.TAG_OUTCOME), findings.map { it.kind })
    }

    @Test
    fun `computeTrendFindings reports a DURATION tag-outcome finding, not just intensity`() {
        val tag = TagEntity(id = 1, name = "aura")
        val taggedCount = TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT + 5
        val untaggedCount = TAG_OUTCOME_MIN_UNTAGGED_SAMPLE_COUNT + 10
        val totalCount = taggedCount + untaggedCount
        val eventsWithTags =
            (0 until totalCount).map { day ->
                val tagged = day % 3 == 0
                val occurredAt = millisAtDay(day.toLong())
                val minutes = if (tagged) 90L else 30L
                val event = testEvent(occurredAt = occurredAt, endedAt = occurredAt + minutes * MILLIS_PER_MINUTE)
                EventWithTags(event, if (tagged) listOf(tag) else emptyList())
            }

        val findings = computeTrendFindings(noShiftGapStats, noShiftDates, trendStats = null, eventsWithTags = eventsWithTags)

        val finding = findings.single { it.kind == TrendFindingKind.TAG_OUTCOME }
        assertEquals(TagOutcome.DURATION, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.PATTERN, finding.reliability)
    }

    // ---- computeTrendFindings: change point ----

    // Ten 3-day gaps, then ten 9-day gaps -- the same planted shift InsightsEngineTest's
    // computeChangePoint tests use. gapStats here comes from a real computeGapStats call over the
    // same events, not gapStatsOf -- this detector is the first one where the two parameters must
    // actually correspond (see computeChangePoint's own KDoc).
    private val changePointDays = (0L..30L step 3L).toList() + (39L..120L step 9L).toList()

    private fun changePointGapStats(eventsWithTags: List<EventWithTags>) =
        computeGapStats(eventsWithTags.map { it.event }, now = millisAtDay(changePointDays.last() + 10))

    @Test
    fun `computeTrendFindings reports a change-point finding as PATTERN with the split date attached`() {
        // This fixture's split lands exactly at the midpoint (10 of 20 gaps each side), so
        // computeGapShift's own fixed-midpoint comparison fires too -- expected, since the two
        // detectors are looking at the same real shift from two different angles; isolate the one
        // this test cares about by kind, the same way the tag-outcome finding test above does.
        val eventsWithTags = changePointDays.map { day -> EventWithTags(testEvent(occurredAt = millisAtDay(day)), emptyList()) }
        val gapStats = changePointGapStats(eventsWithTags)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, eventsWithTags = eventsWithTags)

        val finding = findings.single { it.kind == TrendFindingKind.CHANGE_POINT }
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.PATTERN, finding.reliability)
        assertEquals(LocalDate.ofEpochDay(30), finding.changePointDate)
    }

    @Test
    fun `computeTrendFindings places the change-point finding after tag share shift`() {
        // The same first-half-tagged pattern risingTagEventsWithTags uses, laid over the planted
        // change-point's own event dates so both detectors fire from one self-consistent gapStats.
        // Also trips gap shift, for the same fixed-midpoint reason as the test above.
        val tag = TagEntity(id = 1, name = "decaf")
        val eventsWithTags =
            changePointDays.mapIndexed { index, day ->
                EventWithTags(testEvent(occurredAt = millisAtDay(day)), if (index < 15) listOf(tag) else emptyList())
            }
        val gapStats = changePointGapStats(eventsWithTags)

        val findings = computeTrendFindings(gapStats, activeDates = emptyList(), trendStats = null, eventsWithTags = eventsWithTags)

        assertEquals(
            listOf(TrendFindingKind.GAP_SHIFT, TrendFindingKind.TAG_SHARE_SHIFT, TrendFindingKind.CHANGE_POINT),
            findings.map { it.kind },
        )
    }

    // ---- computeTrendFindings: trend slope / time-of-day split ----

    // A stark, time-ordered step in intensity -- the same shape StatsEngineTest's
    // intensitySlopeEventsWithTags uses -- spread across a date range with no gap-shift structure
    // (a single gap length throughout) so this detector's own finding isn't entangled with others.
    private fun trendSlopeEventsWithTags(): List<EventWithTags> {
        val count = TREND_SLOPE_MIN_SAMPLE_COUNT + 8
        val mid = count / 2
        return (0 until count).map { day ->
            val intensity = if (day < mid) 1 else 5
            EventWithTags(testEvent(occurredAt = millisAtDay(day.toLong()), intensity = intensity), emptyList())
        }
    }

    @Test
    fun `computeTrendFindings reports a trend-slope finding as PATTERN with the outcome attached when eligible`() {
        val findings =
            computeTrendFindings(
                noShiftGapStats,
                noShiftDates,
                trendStats = null,
                eventsWithTags = trendSlopeEventsWithTags(),
                statsShownOutcomes = setOf(TagOutcome.INTENSITY),
            )

        val finding = findings.single { it.kind == TrendFindingKind.TREND_SLOPE }
        assertEquals(TagOutcome.INTENSITY, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.PATTERN, finding.reliability)
    }

    @Test
    fun `computeTrendFindings suppresses a trend-slope finding when its outcome isn't in statsShownOutcomes`() {
        val findings =
            computeTrendFindings(
                noShiftGapStats,
                noShiftDates,
                trendStats = null,
                eventsWithTags = trendSlopeEventsWithTags(),
                statsShownOutcomes = setOf(TagOutcome.DURATION),
            )

        assertTrue(findings.none { it.kind == TrendFindingKind.TREND_SLOPE })
    }

    @Test
    fun `computeTrendFindings omits trend-slope and time-of-day-split findings when statsShownOutcomes is omitted`() {
        val findings =
            computeTrendFindings(noShiftGapStats, noShiftDates, trendStats = null, eventsWithTags = trendSlopeEventsWithTags())

        assertTrue(findings.none { it.kind == TrendFindingKind.TREND_SLOPE || it.kind == TrendFindingKind.TIME_OF_DAY_SPLIT })
    }

    private fun timeOfDaySplitEventsWithTags(): List<EventWithTags> {
        val groupCount = TIME_OF_DAY_SPLIT_MIN_GROUP_SAMPLE_COUNT + 5
        val dayEvents =
            (0 until groupCount).map { i ->
                EventWithTags(testEvent(occurredAt = millisAt(i.toLong(), hour = 9), intensity = 1), emptyList())
            }
        val eveningEvents =
            (0 until groupCount).map { i ->
                EventWithTags(testEvent(occurredAt = millisAt((i + 1000).toLong(), hour = 19), intensity = 5), emptyList())
            }
        return dayEvents + eveningEvents
    }

    @Test
    fun `computeTrendFindings reports a time-of-day-split finding as PATTERN with the outcome attached`() {
        val findings =
            computeTrendFindings(
                noShiftGapStats,
                noShiftDates,
                trendStats = null,
                eventsWithTags = timeOfDaySplitEventsWithTags(),
                statsShownOutcomes = setOf(TagOutcome.INTENSITY),
            )

        val finding = findings.single { it.kind == TrendFindingKind.TIME_OF_DAY_SPLIT }
        assertEquals(TagOutcome.INTENSITY, finding.outcome)
        assertEquals(ShiftDirection.UP, finding.direction)
        assertEquals(TrendReliability.PATTERN, finding.reliability)
    }

    @Test
    fun `computeTrendFindings places trend-slope and time-of-day-split findings after change point`() {
        // Reuses changePointDays' own planted gap-shift shape (unrelated to intensity) so
        // computeChangePoint's gapStats/eventsWithTags correspondence still holds, and additionally
        // splits those same events' intensity early-low/late-high so computeTrendSlopeFindings fires
        // too -- two orthogonal signals over the same 22 events, both expected to co-occur.
        val eventsWithTags =
            changePointDays.mapIndexed { index, day ->
                val intensity = if (index < changePointDays.size / 2) 1 else 5
                EventWithTags(testEvent(occurredAt = millisAtDay(day), intensity = intensity), emptyList())
            }
        val gapStats = changePointGapStats(eventsWithTags)

        val findings =
            computeTrendFindings(
                gapStats,
                activeDates = emptyList(),
                trendStats = null,
                eventsWithTags = eventsWithTags,
                statsShownOutcomes = setOf(TagOutcome.INTENSITY),
            )

        val changePointIndex = findings.indexOfFirst { it.kind == TrendFindingKind.CHANGE_POINT }
        val trendSlopeIndex = findings.indexOfFirst { it.kind == TrendFindingKind.TREND_SLOPE }
        assertTrue(changePointIndex >= 0 && trendSlopeIndex > changePointIndex)
    }

    // ---- capTrendFindings ----

    private fun syntheticFinding() =
        TrendFinding(
            kind = TrendFindingKind.GAP_SHIFT,
            direction = ShiftDirection.UP,
            reliability = TrendReliability.HINT,
            sampleCount = 6,
            priorValue = 2.0,
            recentValue = 5.0,
        )

    @Test
    fun `capTrendFindings caps at TRENDS_MAX_FINDINGS`() {
        val findings = List(TRENDS_MAX_FINDINGS + 4) { syntheticFinding() }

        assertEquals(TRENDS_MAX_FINDINGS, capTrendFindings(findings).size)
    }

    @Test
    fun `capTrendFindings leaves a list under the cap unchanged`() {
        val findings = List(TRENDS_MAX_FINDINGS - 1) { syntheticFinding() }

        assertEquals(findings, capTrendFindings(findings))
        assertTrue(capTrendFindings(findings).size < TRENDS_MAX_FINDINGS)
    }

    @Test
    fun `capTrendFindings keeps a went-quiet finding when it's first, even over the cap`() {
        val wentQuiet = syntheticFinding().copy(kind = TrendFindingKind.WENT_QUIET)
        val findings = listOf(wentQuiet) + List(TRENDS_MAX_FINDINGS) { syntheticFinding() }

        val capped = capTrendFindings(findings)

        assertEquals(TRENDS_MAX_FINDINGS, capped.size)
        assertEquals(TrendFindingKind.WENT_QUIET, capped.first().kind)
    }
}
