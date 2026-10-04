package com.secondmonday.hodith.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareInsightsSectionTest {
    private val gaps = ShareInsightsSection.GAPS
    private val streaks = ShareInsightsSection.STREAKS
    private val duration = ShareInsightsSection.DURATION
    private val rhythm = ShareInsightsSection.RHYTHM
    private val intensity = ShareInsightsSection.INTENSITY
    private val trends = ShareInsightsSection.TRENDS
    private val tags = ShareInsightsSection.TAGS

    @Test
    fun `an empty saved order falls back to declaration order`() {
        assertEquals(ShareInsightsSection.entries, orderedShareSections(emptyList()))
    }

    @Test
    fun `a saved order is kept, with missing sections appended in declaration order`() {
        val saved = listOf(tags, gaps, rhythm)

        assertEquals(listOf(tags, gaps, rhythm, streaks, duration, intensity, trends), orderedShareSections(saved))
    }

    @Test
    fun `repeated saved entries are dropped so each section appears once`() {
        val saved = listOf(tags, tags, gaps)

        assertEquals(ShareInsightsSection.entries.size, orderedShareSections(saved).size)
        assertEquals(listOf(tags, gaps), orderedShareSections(saved).take(2))
    }

    @Test
    fun `moving a visible row reorders only the visible sections and keeps hidden slots`() {
        // Duration is hidden (no data), so it stays in its slot while the visible rows reflow.
        val order = listOf(gaps, duration, rhythm, tags)
        val available = listOf(gaps, rhythm, tags)

        assertEquals(listOf(tags, duration, gaps, rhythm), reorderVisibleSections(order, available, from = 2, to = 0))
    }

    @Test
    fun `moving a row down shifts the rows it passes up one visible slot`() {
        val order = listOf(gaps, rhythm, tags)

        assertEquals(listOf(rhythm, tags, gaps), reorderVisibleSections(order, order, from = 0, to = 2))
    }

    @Test
    fun `moving a row to its own slot leaves the order unchanged`() {
        val order = listOf(gaps, rhythm, tags)

        assertEquals(order, reorderVisibleSections(order, order, from = 1, to = 1))
    }
}
