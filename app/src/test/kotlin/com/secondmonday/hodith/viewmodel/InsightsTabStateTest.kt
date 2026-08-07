package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagBreakdownEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

private fun testCase(createdAt: Long) =
    CaseEntity(
        id = 1L,
        name = "Test Case",
        icon = "🐛",
        createdAt = createdAt,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = false,
    )

private fun eventAtDay(epochDay: Long) =
    EventEntity(
        id = 0,
        caseId = 1,
        occurredAt = millisAtDay(epochDay),
        endedAt = null,
        intensity = null,
        note = null,
        loggedAt = millisAtDay(epochDay),
    )

private fun List<EventEntity>.withoutTags(): List<EventWithTags> = map { EventWithTags(it, emptyList()) }

class InsightsTabStateTest {
    @Test
    fun `insightsTabState is NotEnoughData below the minimum event count`() {
        val case = testCase(createdAt = millisAtDay(0))

        val state = insightsTabState(case, eventsWithTags = listOf(eventAtDay(0)).withoutTags(), now = millisAtDay(5))

        assertEquals(InsightsTabState.NotEnoughData, state)
    }

    @Test
    fun `insightsTabState is Ready once the minimum event count is met`() {
        val case = testCase(createdAt = millisAtDay(0))

        val state = insightsTabState(case, eventsWithTags = listOf(eventAtDay(0), eventAtDay(3)).withoutTags(), now = millisAtDay(5))

        assertTrue(state is InsightsTabState.Ready)
    }

    @Test
    fun `heatmap spans from the case's earliest month through the current month`() {
        // Case created in Jan 2026, "now" lands in March 2026 — three months of grids expected.
        val createdAt =
            LocalDate
                .of(2026, 1, 15)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val now =
            LocalDate
                .of(2026, 3, 10)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val events = listOf(EventEntity(0, 1, createdAt, null, null, null, createdAt), EventEntity(0, 1, now, null, null, null, now))

        val state = insightsTabState(testCase(createdAt), events.withoutTags(), now) as InsightsTabState.Ready

        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            state.heatmapMonths.map { it.month },
        )
    }

    @Test
    fun `heatmap shades a day with the case's busiest count at the top level`() {
        val createdAt =
            LocalDate
                .of(2026, 3, 1)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val eventDate = LocalDate.of(2026, 3, 15)
        val now =
            LocalDate
                .of(2026, 3, 20)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val events = listOf(eventAtDay(eventDate.toEpochDay()), EventEntity(0, 1, now, null, null, null, now))

        val state = insightsTabState(testCase(createdAt), events.withoutTags(), now) as InsightsTabState.Ready

        val shadedDay =
            state.heatmapMonths
                .single()
                .weeks
                .flatten()
                .filterNotNull()
                .single { it.date == eventDate }
        assertEquals(HeatmapLevel.L10, shadedDay.level)
    }

    @Test
    fun `heatmap for the current month has no trailing week-row that's entirely in the future`() {
        // "Now" is the 2nd of the month, so only the first week-row has any real days in it —
        // the remaining 4-5 week-rows of the month grid would otherwise be entirely blank.
        val createdAt =
            LocalDate
                .of(2026, 3, 1)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val now =
            LocalDate
                .of(2026, 3, 2)
                .atStartOfDay(ZONE)
                .toInstant()
                .toEpochMilli()
        val events = listOf(EventEntity(0, 1, createdAt, null, null, null, createdAt), EventEntity(0, 1, now, null, null, null, now))

        val state = insightsTabState(testCase(createdAt), events.withoutTags(), now) as InsightsTabState.Ready

        val currentMonth = state.heatmapMonths.single { it.month == YearMonth.of(2026, 3) }
        assertTrue(currentMonth.weeks.size <= 2)
        assertTrue(currentMonth.weeks.last().any { it != null })
    }

    // ---- stats.totalEventCount / stats.tags ----

    @Test
    fun `totalEventCount reflects every logged event, tagged or not`() {
        val case = testCase(createdAt = millisAtDay(0))
        val tag = TagEntity(id = 1, name = "standup")
        val eventsWithTags =
            listOf(
                EventWithTags(eventAtDay(0), listOf(tag)),
                EventWithTags(eventAtDay(1), emptyList()),
                EventWithTags(eventAtDay(2), emptyList()),
            )

        val state = insightsTabState(case, eventsWithTags, now = millisAtDay(5)) as InsightsTabState.Ready

        assertEquals(3, state.stats.totalEventCount)
    }

    @Test
    fun `tags breakdown counts only tagged events, busiest first, independent of the untagged total`() {
        val case = testCase(createdAt = millisAtDay(0))
        val standup = TagEntity(id = 1, name = "standup")
        val weekend = TagEntity(id = 2, name = "weekend")
        val eventsWithTags =
            listOf(
                EventWithTags(eventAtDay(0), listOf(standup)),
                EventWithTags(eventAtDay(1), listOf(standup, weekend)),
                EventWithTags(eventAtDay(2), emptyList()),
                EventWithTags(eventAtDay(3), emptyList()),
            )

        val state = insightsTabState(case, eventsWithTags, now = millisAtDay(5)) as InsightsTabState.Ready

        assertEquals(4, state.stats.totalEventCount)
        assertEquals(listOf(TagBreakdownEntry("standup", 2), TagBreakdownEntry("weekend", 1)), state.stats.tags)
    }

    // ---- stats.gaps streak fields / stats.trend shift fields ----

    @Test
    fun `gaps display reports the longest and average streak of consecutive active days`() {
        val case = testCase(createdAt = millisAtDay(0))
        // Runs: [0,1,2] (3 days), [10] (1 day) -> longest 3, average 2.
        val events = listOf(eventAtDay(0), eventAtDay(1), eventAtDay(2), eventAtDay(10))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(15)) as InsightsTabState.Ready

        assertEquals(3, state.stats.gaps.longestStreakDays)
        assertEquals(2.0, state.stats.gaps.averageStreakDays, 0.0001)
    }

    @Test
    fun `trend flags a noticeable widening of the average gap once enough history exists`() {
        val case = testCase(createdAt = millisAtDay(0))
        // Past gaps: 4, 4, 4, 20, 20, 20 -> clearly widening in the second half.
        val events =
            listOf(0L, 4L, 8L, 12L, 32L, 52L, 72L).map { eventAtDay(it) }

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(90)) as InsightsTabState.Ready

        assertEquals(ShiftDirection.UP, state.stats.trend?.gapShiftDirection)
    }

    @Test
    fun `trend omits gap and streak shift notes below the trend card's own minimum span`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(4))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        assertEquals(null, state.stats.trend)
    }
}
