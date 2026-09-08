package com.secondmonday.hodith.ui.casedetail

import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.VerdictMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class HunchCreationSheetLogicTest {
    @Test
    fun `periodOptionsFor offers Day-Week-Month for occurrence count`() {
        assertEquals(
            listOf(ExpectedPer.DAY, ExpectedPer.WEEK, ExpectedPer.MONTH),
            periodOptionsFor(VerdictMetric.OCCURRENCE_COUNT),
        )
    }

    @Test
    fun `periodOptionsFor drops Day and adds Quarter for days-active`() {
        assertEquals(
            listOf(ExpectedPer.WEEK, ExpectedPer.MONTH, ExpectedPer.QUARTER),
            periodOptionsFor(VerdictMetric.DAYS_ACTIVE),
        )
    }

    @Test
    fun `coerceExpectedPer keeps a period the metric still offers`() {
        assertEquals(ExpectedPer.WEEK, coerceExpectedPer(VerdictMetric.DAYS_ACTIVE, ExpectedPer.WEEK))
        assertEquals(ExpectedPer.MONTH, coerceExpectedPer(VerdictMetric.OCCURRENCE_COUNT, ExpectedPer.MONTH))
    }

    @Test
    fun `coerceExpectedPer steps Day up to Week when switching to days-active`() {
        assertEquals(ExpectedPer.WEEK, coerceExpectedPer(VerdictMetric.DAYS_ACTIVE, ExpectedPer.DAY))
    }

    @Test
    fun `coerceExpectedPer steps Quarter down to Month when switching to occurrence count`() {
        assertEquals(ExpectedPer.MONTH, coerceExpectedPer(VerdictMetric.OCCURRENCE_COUNT, ExpectedPer.QUARTER))
    }
}
