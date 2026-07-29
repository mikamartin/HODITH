package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ConfidenceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

private fun testCase(
    createdAt: Long = millisAtDay(0),
    hunchNudgeDismissed: Boolean = false,
) = CaseEntity(
    id = 1L,
    name = "Test Case",
    icon = "🐛",
    createdAt = createdAt,
    logFlow = LogFlow.ONE_TAP,
    durationMode = DurationMode.NONE,
    intensityEnabled = false,
    hunchNudgeDismissed = hunchNudgeDismissed,
    pinned = false,
    checkInsEnabled = true,
    lastCheckInAt = null,
    sortOrder = 0,
    archived = false,
)

private fun testHunch(
    id: Long = 1L,
    expectedCount: Int = 5,
    expectedPer: ExpectedPer = ExpectedPer.WEEK,
    direction: HunchDirection = HunchDirection.TOO_OFTEN,
    createdAt: Long = millisAtDay(0),
    resolvedAt: Long? = null,
) = HunchEntity(
    id = id,
    caseId = 1L,
    direction = direction,
    expectedCount = expectedCount,
    expectedPer = expectedPer,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
)

private fun eventsAt(
    count: Int,
    occurredAt: Long,
): List<EventEntity> =
    List(count) {
        EventEntity(caseId = 1L, occurredAt = occurredAt, endedAt = null, intensity = null, note = null, loggedAt = occurredAt)
    }

class HunchTabStateTest {
    // ---- no active hunch: nudge gating ----

    @Test
    fun `no nudge below the event threshold`() {
        val case = testCase()
        val state =
            hunchTabState(case, activeHunch = null, events = eventsAt(4, millisAtDay(0)), history = emptyList(), now = millisAtDay(1))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertFalse(noActiveHunch.showNudge)
    }

    @Test
    fun `nudge shows at exactly the event threshold`() {
        val case = testCase()
        val state =
            hunchTabState(case, activeHunch = null, events = eventsAt(5, millisAtDay(0)), history = emptyList(), now = millisAtDay(1))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertTrue(noActiveHunch.showNudge)
    }

    @Test
    fun `nudge is suppressed once dismissed even past the threshold`() {
        val case = testCase(hunchNudgeDismissed = true)
        val state =
            hunchTabState(case, activeHunch = null, events = eventsAt(10, millisAtDay(0)), history = emptyList(), now = millisAtDay(1))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertFalse(noActiveHunch.showNudge)
    }

    // ---- active hunch: early days vs verdict ----

    @Test
    fun `active hunch with too little data is EarlyDays`() {
        val case = testCase()
        val hunch = testHunch()
        val state =
            hunchTabState(case, activeHunch = hunch, events = eventsAt(2, millisAtDay(0)), history = emptyList(), now = millisAtDay(5))

        val earlyDays = state as HunchTabState.EarlyDays
        assertEquals(ConfidenceTier.NO_VERDICT, earlyDays.result.tier)
    }

    @Test
    fun `active hunch past the Preliminary bar is a Verdict`() {
        val case = testCase()
        val hunch = testHunch(expectedCount = 5, expectedPer = ExpectedPer.WEEK)
        val state =
            hunchTabState(case, activeHunch = hunch, events = eventsAt(6, millisAtDay(8)), history = emptyList(), now = millisAtDay(16))

        val verdict = state as HunchTabState.Verdict
        assertEquals(ConfidenceTier.PRELIMINARY, verdict.result.tier)
    }

    @Test
    fun `active hunch past the Confident bar is a Verdict`() {
        val case = testCase()
        val hunch = testHunch(expectedCount = 5, expectedPer = ExpectedPer.WEEK)
        val state =
            hunchTabState(case, activeHunch = hunch, events = eventsAt(15, millisAtDay(25)), history = emptyList(), now = millisAtDay(50))

        val verdict = state as HunchTabState.Verdict
        assertEquals(ConfidenceTier.CONFIDENT, verdict.result.tier)
    }

    // ---- history: frozen at resolvedAt, not recomputed against today ----

    @Test
    fun `history entry freezes the verdict at resolvedAt, ignoring later events`() {
        val case = testCase()
        val hunch = testHunch(expectedCount = 1, expectedPer = ExpectedPer.DAY, resolvedAt = millisAtDay(14))
        // 14 events by day 14 (matches expectedRate of 1/day -> ABOUT_RIGHT), then a burst of
        // 50 more events afterward that would blow the ratio far past MUCH_MORE if counted.
        val eventsBeforeResolution = eventsAt(14, millisAtDay(7))
        val eventsAfterResolution = eventsAt(50, millisAtDay(20))
        val events = eventsBeforeResolution + eventsAfterResolution

        val state = hunchTabState(case, activeHunch = null, events = events, history = listOf(hunch), now = millisAtDay(100))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertEquals(1, noActiveHunch.history.size)
        val entry = noActiveHunch.history.single()
        assertEquals(ComparisonBand.ABOUT_RIGHT, entry.result.comparisonBand)
    }

    @Test
    fun `history omits a hunch that was never resolved`() {
        val case = testCase()
        val unresolved = testHunch(resolvedAt = null)

        val state = hunchTabState(case, activeHunch = null, events = emptyList(), history = listOf(unresolved), now = millisAtDay(30))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertTrue(noActiveHunch.history.isEmpty())
    }

    @Test
    fun `history can mix a held-up outcome and an off outcome`() {
        val case = testCase()
        // Same 14-events-over-14-days reality (1x/day observed) judged against two different
        // hunches: one that expected exactly that rate, one that expected ten times as much.
        val heldUp = testHunch(id = 1L, expectedCount = 1, expectedPer = ExpectedPer.DAY, resolvedAt = millisAtDay(14))
        val off = testHunch(id = 2L, expectedCount = 10, expectedPer = ExpectedPer.DAY, resolvedAt = millisAtDay(14))
        val events = eventsAt(14, millisAtDay(7))

        val state = hunchTabState(case, activeHunch = null, events = events, history = listOf(heldUp, off), now = millisAtDay(30))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertEquals(2, noActiveHunch.history.size)
        assertEquals(ComparisonBand.ABOUT_RIGHT, noActiveHunch.history[0].result.comparisonBand)
        assertEquals(ComparisonBand.MUCH_LESS, noActiveHunch.history[1].result.comparisonBand)
    }

    @Test
    fun `history omits a hunch resolved before it ever reached a verdict`() {
        val case = testCase()
        val resolvedTooEarly = testHunch(resolvedAt = millisAtDay(1))

        val state = hunchTabState(case, activeHunch = null, events = emptyList(), history = listOf(resolvedTooEarly), now = millisAtDay(30))

        val noActiveHunch = state as HunchTabState.NoActiveHunch
        assertTrue(noActiveHunch.history.isEmpty())
    }

    // ---- hunchProgressFraction ----

    @Test
    fun `hunchProgressFraction is bottlenecked by whichever requirement is furthest behind`() {
        // 3 of 5 events (0.6) vs 9 of 14 days (~0.64) -> events is the bottleneck.
        assertEquals(0.6f, hunchProgressFraction(eventCount = 3, windowDays = 9), 0.001f)
    }

    @Test
    fun `hunchProgressFraction is coerced to 1 once both requirements clear`() {
        assertEquals(1f, hunchProgressFraction(eventCount = 20, windowDays = 40), 0.001f)
    }

    @Test
    fun `hunchProgressFraction is zero with nothing logged yet`() {
        assertEquals(0f, hunchProgressFraction(eventCount = 0, windowDays = 0), 0.001f)
    }
}
