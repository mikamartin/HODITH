package com.secondmonday.hodith.domain.timeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class TimelineAxisTest {
    @Test
    fun `axisTickMillis puts the first tick at the start and the last at the end`() {
        val window = TimeWindow(startMillis = 1_000L, endMillis = 1_000L + ZoomLevel.MONTH.durationMillis)

        val ticks = axisTickMillis(window)

        assertEquals(5, ticks.size)
        assertEquals(window.startMillis, ticks.first())
        assertEquals(window.endMillis, ticks.last())
    }

    @Test
    fun `axisTickMillis is evenly spaced`() {
        val window = TimeWindow(startMillis = 0L, endMillis = 4_000L)

        val ticks = axisTickMillis(window)

        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L, 4_000L), ticks)
    }

    @Test
    fun `axisTickLabel picks precision from zoom level`() {
        // 2024-06-12T00:00:00Z — a Wednesday.
        val millis = 1_718_150_400_000L

        assertEquals("Wed 12", axisTickLabel(millis, ZoneOffset.UTC, ZoomLevel.WEEK))
        assertEquals("Jun 12", axisTickLabel(millis, ZoneOffset.UTC, ZoomLevel.MONTH))
        assertEquals("Jun 12", axisTickLabel(millis, ZoneOffset.UTC, ZoomLevel.THREE_MONTH))
        assertEquals("Jun", axisTickLabel(millis, ZoneOffset.UTC, ZoomLevel.YEAR))
    }
}
