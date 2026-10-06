package com.secondmonday.hodith.ui.casedetail

import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TrendFiguresTest {
    @Test
    fun `tag share compares the recent share against the prior share`() {
        val finding =
            TrendFinding(TrendFindingKind.TAG_SHARE_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 31, 0.19, 0.41, tagName = "Coffee")

        val figures = trendFigures(finding, PlainVoice)

        assertEquals("41%", figures.primary)
        assertEquals("19%", figures.reference)
    }

    @Test
    fun `weekday weekend split compares against the chance baseline`() {
        val finding = TrendFinding(TrendFindingKind.WEEKDAY_WEEKEND_SPLIT, ShiftDirection.UP, TrendReliability.PATTERN, 58, 2.0 / 7, 0.62)

        val figures = trendFigures(finding, PlainVoice)

        assertEquals("62%", figures.primary)
        assertEquals(PlainVoice.trendChanceBaselineLabel, figures.reference)
    }

    @Test
    fun `tag timing compares the tagged share against the case-wide share`() {
        val finding =
            TrendFinding(
                TrendFindingKind.TAG_TIMING,
                ShiftDirection.UP,
                TrendReliability.PATTERN,
                31,
                priorValue = 0.31,
                recentValue = 0.58,
                tagName = "Coffee",
            )

        val figures = trendFigures(finding, PlainVoice)

        assertEquals("58%", figures.primary)
        assertEquals(PlainVoice.trendCaseWideReference("31%"), figures.reference)
    }

    @Test
    fun `tag combo reads as a count of the total events, with no comparison`() {
        val finding =
            TrendFinding(
                TrendFindingKind.TAG_COMBO,
                ShiftDirection.UP,
                TrendReliability.HINT,
                5,
                priorValue = 5.0,
                recentValue = 8.0,
                tagNames = listOf("Late", "Coffee"),
            )

        val figures = trendFigures(finding, PlainVoice)

        assertEquals("5 of 8", figures.primary)
        assertNull(figures.reference)
    }

    @Test
    fun `duration outcome formats its values as durations, not intensity`() {
        val finding =
            TrendFinding(
                TrendFindingKind.TAG_OUTCOME,
                ShiftDirection.UP,
                TrendReliability.PATTERN,
                40,
                priorValue = 30.0,
                recentValue = 45.0,
                tagName = "aura",
                outcome = TagOutcome.DURATION,
            )

        val figures = trendFigures(finding, PlainVoice)

        assertEquals("45m", figures.primary)
        assertEquals("30m", figures.reference)
    }

    @Test
    fun `tag outcome detail states the relative change the sentence used to carry`() {
        val finding =
            TrendFinding(
                TrendFindingKind.TAG_OUTCOME,
                ShiftDirection.UP,
                TrendReliability.PATTERN,
                20,
                priorValue = 30.0,
                recentValue = 45.0,
                tagName = "aura",
                outcome = TagOutcome.DURATION,
            )

        val figures = trendFigures(finding, PlainVoice)

        assertEquals(PlainVoice.trendRelativeChange("50%"), figures.detail)
    }

    @Test
    fun `change point detail places the shift at its approximate month`() {
        val finding =
            TrendFinding(
                TrendFindingKind.CHANGE_POINT,
                ShiftDirection.UP,
                TrendReliability.PATTERN,
                20,
                priorValue = 3.0,
                recentValue = 9.0,
                changePointDate = LocalDate.of(2026, 3, 14),
            )

        val figures = trendFigures(finding, PlainVoice)

        assertEquals(PlainVoice.trendChangePointDetail("mid-March"), figures.detail)
    }

    @Test
    fun `recurrence detail names the gap threshold the share counts under`() {
        val finding = TrendFinding(TrendFindingKind.RECURRENCE_SHAPE, ShiftDirection.UP, TrendReliability.HINT, 11, 3.0, 0.73)

        val figures = trendFigures(finding, PlainVoice)

        assertEquals(PlainVoice.trendRecurrenceDetailLabel("3 days"), figures.detail)
    }
}
