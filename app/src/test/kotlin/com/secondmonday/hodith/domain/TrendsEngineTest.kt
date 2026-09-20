package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
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
