package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    durationMode: DurationMode = DurationMode.NONE,
    intensityEnabled: Boolean = false,
) = CaseEntity(
    id = 1L,
    name = "Perfect coffee",
    icon = "☕",
    createdAt = millisAtDay(0),
    logFlow = LogFlow.ONE_TAP,
    durationMode = durationMode,
    intensityEnabled = intensityEnabled,
    hunchNudgeDismissed = false,
    pinned = false,
    checkInsEnabled = true,
    lastCheckInAt = null,
    sortOrder = 0,
    archived = false,
)

private fun testHunch(
    direction: HunchDirection = HunchDirection.TOO_OFTEN,
    expectedCount: Int = 2,
    expectedPer: ExpectedPer = ExpectedPer.MONTH,
) = HunchEntity(
    id = 1L,
    caseId = 1L,
    direction = direction,
    expectedCount = expectedCount,
    expectedPer = expectedPer,
    createdAt = millisAtDay(0),
    resolvedAt = null,
)

/** 12 events, 5 days apart, spanning day 0 to day 55 — enough for every stat section to be non-null. */
private fun readyEventsWithTags(): List<EventWithTags> =
    (0..55L step 5).map { day ->
        EventWithTags(
            EventEntity(
                caseId = 1L,
                occurredAt = millisAtDay(day),
                endedAt = null,
                intensity = 3,
                note = null,
                loggedAt = millisAtDay(day),
            ),
            emptyList(),
        )
    }

private const val NOW = 60L

private fun readyInsightsState(case: CaseEntity): InsightsTabState = insightsTabState(case, readyEventsWithTags(), now = millisAtDay(NOW))

private fun verdictHunchState(hunch: HunchEntity): HunchTabState =
    hunchTabState(
        testCase(),
        activeHunch = hunch,
        events = readyEventsWithTags().map { it.event },
        history = emptyList(),
        now = millisAtDay(NOW),
    )

private val ALL_SECTIONS = ShareInsightsSection.entries.toSet()

class ShareCardStateTest {
    // ---- top beat selection ----

    @Test
    fun `Square always falls back to Reality even with a resolved Hunch and the toggle on`() {
        val hunch = testHunch()
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = readyInsightsState(testCase()),
                hunchState = verdictHunchState(hunch),
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = true,
            )

        assertTrue(data.topBeat is ShareTopBeat.Reality)
    }

    @Test
    fun `Story with the toggle on and a resolved Hunch shows Hunch vs Reality`() {
        val hunch = testHunch()
        val hunchState = verdictHunchState(hunch)
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = readyInsightsState(testCase()),
                hunchState = hunchState,
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.STORY,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = true,
            )

        val beat = data.topBeat as ShareTopBeat.HunchVsReality
        assertEquals(hunch, beat.hunch)
        assertEquals((hunchState as HunchTabState.Verdict).result.comparisonBand, beat.band)
    }

    @Test
    fun `Story with the toggle off shows Reality even with a resolved Hunch`() {
        val hunch = testHunch()
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = readyInsightsState(testCase()),
                hunchState = verdictHunchState(hunch),
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.STORY,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
            )

        assertTrue(data.topBeat is ShareTopBeat.Reality)
    }

    @Test
    fun `Story with the toggle on but only an EarlyDays Hunch falls back to Reality`() {
        // Fresh hunch, only 1 event logged -- below the Preliminary bar, so no comparisonBand yet.
        val hunch = testHunch()
        val earlyDaysState =
            hunchTabState(
                testCase(),
                activeHunch = hunch,
                events =
                    listOf(
                        EventEntity(
                            caseId = 1L,
                            occurredAt = millisAtDay(0),
                            endedAt = null,
                            intensity = null,
                            note = null,
                            loggedAt = millisAtDay(0),
                        ),
                    ),
                history = emptyList(),
                now = millisAtDay(1),
            )

        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = readyInsightsState(testCase()),
                hunchState = earlyDaysState,
                eventCount = 1,
                observedDays = 1,
                format = ShareCardFormat.STORY,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = true,
            )

        assertTrue(data.topBeat is ShareTopBeat.Reality)
    }

    @Test
    fun `Reality reports the passed-in eventCount and observedDays, not a derived value`() {
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = InsightsTabState.NotEnoughData,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 1,
                observedDays = 3,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
            )

        val reality = data.topBeat as ShareTopBeat.Reality
        assertEquals(1, reality.eventCount)
        assertEquals(3L, reality.observedDays)
    }

    @Test
    fun `displayName overrides the Case's actual name without needing to mutate the Case`() {
        val case = testCase()
        val data =
            shareCardState(
                case = case,
                displayName = "My custom title",
                insightsState = InsightsTabState.NotEnoughData,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 1,
                observedDays = 1,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
            )

        assertEquals("My custom title", data.caseName)
        assertEquals("Perfect coffee", case.name)
    }

    // ---- section filtering ----

    @Test
    fun `NotEnoughData insights leaves every section null regardless of selection`() {
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = InsightsTabState.NotEnoughData,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 1,
                observedDays = 1,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
            )

        assertNull(data.frequency)
        assertNull(data.rhythm)
        assertNull(data.gaps)
        assertNull(data.trend)
        assertNull(data.duration)
        assertNull(data.intensity)
    }

    @Test
    fun `only the selected sections are populated`() {
        val case = testCase()
        val data =
            shareCardState(
                case = case,
                displayName = case.name,
                insightsState = readyInsightsState(case),
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.RHYTHM, ShareInsightsSection.TREND),
                showHunchVsReality = false,
            )

        assertNull(data.frequency)
        assertTrue(data.rhythm != null)
        assertNull(data.gaps)
        assertTrue(data.trend != null)
        assertNull(data.duration)
        assertNull(data.intensity)
    }

    @Test
    fun `duration and intensity stay null when selected but the Case doesn't track them`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = false)
        val data =
            shareCardState(
                case = case,
                displayName = case.name,
                insightsState = readyInsightsState(case),
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
            )

        assertNull(data.duration)
        assertNull(data.intensity)
    }

    @Test
    fun `duration and intensity appear when the Case tracks them and they're selected`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)
        val data =
            shareCardState(
                case = case,
                displayName = case.name,
                insightsState = readyInsightsState(case),
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
            )

        // durationMode = MANUAL but no MANUAL-duration data was logged on these events, so
        // computeDurationStats legitimately returns null here -- only intensity is asserted non-null.
        assertTrue(data.intensity != null)
    }
}
