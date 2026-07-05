package com.secondmonday.hodith.domain.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

private val MONTH_WINDOW = TimeWindow(startMillis = 10_000L, endMillis = 10_000L + ZoomLevel.MONTH.durationMillis)

class TimelineWindowMathTest {
    @Test
    fun `pure pan shifts the window without changing its duration`() {
        val result = nextWindow(MONTH_WINDOW, focalXFraction = 0.5f, panXFraction = 0.1f, zoomChange = 1f)

        assertEquals(MONTH_WINDOW.durationMillis, result.durationMillis)
        assertEquals(MONTH_WINDOW.startMillis - (0.1f * MONTH_WINDOW.durationMillis).toLong(), result.startMillis)
    }

    @Test
    fun `zooming in around a focal point keeps that instant at the same fraction`() {
        val focalXFraction = 0.25f
        val focalMillis = MONTH_WINDOW.startMillis + (focalXFraction * MONTH_WINDOW.durationMillis).toLong()

        val result = nextWindow(MONTH_WINDOW, focalXFraction = focalXFraction, panXFraction = 0f, zoomChange = 2f)

        val resultFraction = (focalMillis - result.startMillis).toDouble() / result.durationMillis
        assertEquals(focalXFraction.toDouble(), resultFraction, 0.01)
    }

    @Test
    fun `duration is clamped to the week-to-year range`() {
        val zoomedInPastWeek = nextWindow(MONTH_WINDOW, focalXFraction = 0.5f, panXFraction = 0f, zoomChange = 100f)
        assertEquals(ZoomLevel.WEEK.durationMillis, zoomedInPastWeek.durationMillis)

        val zoomedOutPastYear = nextWindow(MONTH_WINDOW, focalXFraction = 0.5f, panXFraction = 0f, zoomChange = 0.01f)
        assertEquals(ZoomLevel.YEAR.durationMillis, zoomedOutPastYear.durationMillis)
    }

    @Test
    fun `withDuration recenters on the current midpoint`() {
        val result = MONTH_WINDOW.withDuration(ZoomLevel.WEEK.durationMillis)

        val oldCenter = MONTH_WINDOW.startMillis + MONTH_WINDOW.durationMillis / 2
        val newCenter = result.startMillis + result.durationMillis / 2
        assertEquals(oldCenter, newCenter)
        assertEquals(ZoomLevel.WEEK.durationMillis, result.durationMillis)
    }
}
