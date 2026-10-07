package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val UTC = ZoneId.of("UTC")

private fun millisOn(date: LocalDate): Long = date.atStartOfDay(UTC).toInstant().toEpochMilli()

private val NOW = millisOn(LocalDate.of(2026, 10, 5))
private val CREATED = millisOn(LocalDate.of(2026, 3, 2))

class HistoryRangeBoundsTest {
    @Test
    fun `historyRangeBounds resolves an unset range to the creation date through now`() {
        assertEquals(Pair("Mar 2", "Oct 5"), historyRangeBounds(CREATED, null, null, NOW, UTC))
    }

    @Test
    fun `historyRangeBounds keeps a set start and shows now for an unset end`() {
        val start = millisOn(LocalDate.of(2026, 6, 1))

        assertEquals(Pair("Jun 1", "Oct 5"), historyRangeBounds(CREATED, start, null, NOW, UTC))
    }

    @Test
    fun `historyRangeBounds shows the creation date for an unset start and keeps a set end`() {
        val end = millisOn(LocalDate.of(2026, 7, 4))

        assertEquals(Pair("Mar 2", "Jul 4"), historyRangeBounds(CREATED, null, end, NOW, UTC))
    }

    @Test
    fun `historyRangeBounds includes the year when the creation date falls in an earlier year`() {
        val created = millisOn(LocalDate.of(2024, 11, 20))

        assertEquals(Pair("Nov 20, 2024", "Oct 5"), historyRangeBounds(created, null, null, NOW, UTC))
    }

    @Test
    fun `isUnsetHistoryRange is true only with no start bound and an end bound of today`() {
        val yesterday = millisOn(LocalDate.of(2026, 10, 4))

        assertTrue(isUnsetHistoryRange(null, NOW, NOW, UTC))
        assertFalse(isUnsetHistoryRange(null, yesterday, NOW, UTC))
        assertFalse(isUnsetHistoryRange(CREATED, NOW, NOW, UTC))
    }

    @Test
    fun `historyShareSelectorValue reads All time while the range is unset`() {
        assertEquals(
            PlainVoice.shareHistoryRangeAllTimeLabel,
            historyShareSelectorValue(CREATED, null, NOW, NOW, UTC, PlainVoice),
        )
    }

    @Test
    fun `historyShareSelectorValue shows the resolved bounds once a start is set`() {
        val start = millisOn(LocalDate.of(2026, 6, 1))

        assertEquals("Jun 1 – Oct 5", historyShareSelectorValue(CREATED, start, NOW, NOW, UTC, PlainVoice))
    }

    @Test
    fun `historyShareSelectorValue shows the creation date as the start once only the end is narrowed`() {
        val end = millisOn(LocalDate.of(2026, 7, 4))

        assertEquals("Mar 2 – Jul 4", historyShareSelectorValue(CREATED, null, end, NOW, UTC, PlainVoice))
    }

    @Test
    fun `historyShareSelectorValue does not read All time when the start is set to today`() {
        assertEquals("Oct 5 – Oct 5", historyShareSelectorValue(CREATED, NOW, NOW, NOW, UTC, PlainVoice))
    }
}
