package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.HeatmapLevel
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
        pinned = false,
        checkInDays = null,
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
    fun `timeline tokens alternate gap and dot, one dot per windowed event, ending on a trailing gap`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events = listOf(eventAtDay(0), eventAtDay(3), eventAtDay(6))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        // leading gap, dot, gap, dot, gap, dot, trailing gap = 3 dots + 4 gaps
        val dotCount = state.timeline.tokens.count { it is TimelineToken.Dot }
        val gapCount = state.timeline.tokens.count { it is TimelineToken.Gap }
        assertEquals(3, dotCount)
        assertEquals(4, gapCount)
        assertTrue(state.timeline.tokens[1] is TimelineToken.Dot)
    }

    @Test
    fun `several events on the same day collapse into one more heavily shaded dot instead of separate dots`() {
        val case = testCase(createdAt = millisAtDay(0))
        // day 5 has 3 events, the busiest day — must merge into a single dot, not three.
        val events = listOf(eventAtDay(0), eventAtDay(5), eventAtDay(5), eventAtDay(5), eventAtDay(8))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(10)) as InsightsTabState.Ready

        val dots = state.timeline.tokens.filterIsInstance<TimelineToken.Dot>()
        assertEquals(3, dots.size)
        assertEquals(HeatmapLevel.L10, dots[1].level)
    }

    @Test
    fun `gap note flags a new record when the current gap ties or beats every past gap`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events = listOf(eventAtDay(0), eventAtDay(2), eventAtDay(4))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(20)) as InsightsTabState.Ready

        assertEquals(16L, state.timeline.currentGapDays)
        assertTrue(state.timeline.isCurrentGapLongest)
    }

    @Test
    fun `gap note does not flag a record when a past gap was bigger than the current one`() {
        val case = testCase(createdAt = millisAtDay(0))
        val events = listOf(eventAtDay(0), eventAtDay(20), eventAtDay(22))

        val state = insightsTabState(case, events.withoutTags(), now = millisAtDay(24)) as InsightsTabState.Ready

        assertEquals(false, state.timeline.isCurrentGapLongest)
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
}
