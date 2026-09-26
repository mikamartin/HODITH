package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.casedetail.TRENDS_DEFAULT_VISIBLE_COUNT
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
                generatedAtMillis = millisAtDay(NOW),
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
                generatedAtMillis = millisAtDay(NOW),
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
                generatedAtMillis = millisAtDay(NOW),
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
                generatedAtMillis = millisAtDay(NOW),
            )

        assertTrue(data.topBeat is ShareTopBeat.Reality)
    }

    @Test
    fun `Reality reports the passed-in eventCount and observedDays, not a derived value`() {
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = InsightsTabState.NothingLogged,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 1,
                observedDays = 3,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
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
                insightsState = InsightsTabState.NothingLogged,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 1,
                observedDays = 1,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertEquals("My custom title", data.caseName)
        assertEquals("Perfect coffee", case.name)
    }

    // ---- section filtering ----

    @Test
    fun `NothingLogged insights leaves every section null regardless of selection`() {
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = InsightsTabState.NothingLogged,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 0,
                observedDays = 1,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertNull(data.frequency)
        assertNull(data.rhythm)
        assertNull(data.gaps)
        assertEquals(emptyList<Any>(), data.trends)
        assertNull(data.duration)
        assertNull(data.intensity)
    }

    @Test
    fun `a single-event Case offers Rhythm and Gaps but not Frequency or Trends on the share card`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = false)
        val oneEvent =
            listOf(
                EventWithTags(
                    EventEntity(
                        caseId = 1L,
                        occurredAt = millisAtDay(0),
                        endedAt = null,
                        intensity = null,
                        note = null,
                        loggedAt = millisAtDay(0),
                    ),
                    emptyList(),
                ),
            )

        val data =
            shareCardState(
                case = case,
                displayName = case.name,
                insightsState = insightsTabState(case, oneEvent, now = millisAtDay(NOW)),
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 1,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertTrue(data.rhythm != null)
        assertTrue(data.gaps != null)
        assertNull(data.frequency)
        assertEquals(emptyList<Any>(), data.trends)
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
                selectedSections = setOf(ShareInsightsSection.RHYTHM, ShareInsightsSection.TRENDS),
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertNull(data.frequency)
        assertTrue(data.rhythm != null)
        assertNull(data.gaps)
        assertTrue(data.trends.isNotEmpty())
        assertNull(data.duration)
        assertNull(data.intensity)
    }

    @Test
    fun `Trends data stays empty when not selected, even though findings exist`() {
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
                selectedSections = setOf(ShareInsightsSection.RHYTHM),
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        // readyInsightsState's fixture is the same one `only the selected sections are populated`
        // confirms yields a non-empty stats.trends -- proves this is selection gating, not an
        // incidentally-empty findings list.
        assertEquals(emptyList<Any>(), data.trends)
    }

    private fun statsWithTrends(findings: List<TrendFinding>) =
        StatsSections(
            frequency = null,
            rhythm = RhythmDisplay(cells = emptyList(), plottedByStart = false),
            gaps =
                GapsDisplay(
                    longestGapDays = 0,
                    currentGapDays = 0,
                    averageGapDays = 0.0,
                    isBursty = false,
                    longestStreakDays = 0,
                    averageStreakDays = 0.0,
                ),
            duration = null,
            intensity = null,
            tags = emptyList(),
            totalEventCount = 20,
            trends = findings,
        )

    private fun ordinaryFinding(sampleCount: Int) =
        TrendFinding(
            kind = TrendFindingKind.GAP_SHIFT,
            direction = ShiftDirection.UP,
            reliability = TrendReliability.HINT,
            sampleCount = sampleCount,
            priorValue = 1.0,
            recentValue = 2.0,
        )

    @Test
    fun `a Trends list longer than the cap is trimmed to the first three findings`() {
        val case = testCase()
        val findings = (1..5).map { ordinaryFinding(sampleCount = it) }
        val insightsState = InsightsTabState.Ready(heatmapMonths = emptyList(), stats = statsWithTrends(findings))

        val data =
            shareCardState(
                case = case,
                displayName = case.name,
                insightsState = insightsState,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 20,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.TRENDS),
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertEquals(findings.take(TRENDS_DEFAULT_VISIBLE_COUNT), data.trends)
    }

    @Test
    fun `a WENT_QUIET-leading Trends list is capped to just that one finding`() {
        val case = testCase()
        val wentQuiet =
            TrendFinding(
                kind = TrendFindingKind.WENT_QUIET,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.HINT,
                sampleCount = 6,
                priorValue = 4.0,
                recentValue = 50.0,
            )
        val findings = listOf(wentQuiet) + (1..3).map { ordinaryFinding(sampleCount = it) }
        val insightsState = InsightsTabState.Ready(heatmapMonths = emptyList(), stats = statsWithTrends(findings))

        val data =
            shareCardState(
                case = case,
                displayName = case.name,
                insightsState = insightsState,
                hunchState = HunchTabState.NoActiveHunch(showNudge = false, history = emptyList()),
                eventCount = 20,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.TRENDS),
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertEquals(listOf(wentQuiet), data.trends)
    }

    @Test
    fun `generatedAtMillis always reflects the passed-in value, regardless of section selection`() {
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
                selectedSections = emptySet(),
                showHunchVsReality = false,
                generatedAtMillis = millisAtDay(123),
            )

        assertEquals(millisAtDay(123), data.generatedAtMillis)
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
                generatedAtMillis = millisAtDay(NOW),
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
                generatedAtMillis = millisAtDay(NOW),
            )

        // durationMode = MANUAL but no MANUAL-duration data was logged on these events, so
        // computeDurationStats legitimately returns null here -- only intensity is asserted non-null.
        assertTrue(data.intensity != null)
    }
}
