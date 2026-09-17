package com.secondmonday.hodith.domain

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

class TrendsEngineTest {
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
}
