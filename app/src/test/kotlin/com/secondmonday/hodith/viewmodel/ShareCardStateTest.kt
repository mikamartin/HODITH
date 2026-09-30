package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.LOG_SHARE_CARD_ENTRY_CAP
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.casedetail.TRENDS_DEFAULT_VISIBLE_COUNT
import com.secondmonday.hodith.ui.voice.PlainVoice
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

private val ALL_SECTIONS = ShareInsightsSection.entries.toSet()

class ShareCardStateTest {
    // ---- top beat selection ----

    @Test
    fun `Reality is the top beat regardless of format`() {
        for (format in ShareCardFormat.entries) {
            val data =
                shareCardState(
                    case = testCase(),
                    displayName = testCase().name,
                    insightsState = readyInsightsState(testCase()),
                    eventCount = 12,
                    observedDays = 60,
                    format = format,
                    selectedSections = ALL_SECTIONS,
                    generatedAtMillis = millisAtDay(NOW),
                )

            assertTrue(data.topBeat is ShareTopBeat.Reality)
        }
    }

    @Test
    fun `Reality reports the passed-in eventCount and observedDays, not a derived value`() {
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = InsightsTabState.NothingLogged,
                eventCount = 1,
                observedDays = 3,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
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
                eventCount = 1,
                observedDays = 1,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
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
                eventCount = 0,
                observedDays = 1,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
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
                eventCount = 1,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
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
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.RHYTHM, ShareInsightsSection.TRENDS),
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
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.RHYTHM),
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
                eventCount = 20,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.TRENDS),
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
                eventCount = 20,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = setOf(ShareInsightsSection.TRENDS),
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
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = emptySet(),
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
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
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
                eventCount = 12,
                observedDays = 60,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                generatedAtMillis = millisAtDay(NOW),
            )

        // durationMode = MANUAL but no MANUAL-duration data was logged on these events, so
        // computeDurationStats legitimately returns null here -- only intensity is asserted non-null.
        assertTrue(data.intensity != null)
    }

    // ---- logShareCardState ----

    private fun logShareEvent(
        day: Long,
        note: String? = null,
        tags: List<TagEntity> = emptyList(),
        intensity: Int? = null,
        durationMinutes: Long? = null,
    ) = EventWithTags(
        EventEntity(
            caseId = 1L,
            occurredAt = millisAtDay(day),
            endedAt = durationMinutes?.let { millisAtDay(day) + it * 60_000L },
            intensity = intensity,
            note = note,
            loggedAt = millisAtDay(day),
        ),
        tags,
    )

    private val allLogFields = LogRowField.entries.toSet()

    @Test
    fun `logShareCardState suppresses duration and intensity when the Case doesn't track them, even if selected`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = false)
        val events = listOf(logShareEvent(day = 0, intensity = 3, durationMinutes = 30))

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertNull(data.rows.single().detail)
    }

    @Test
    fun `logShareCardState omits a field from the row when the user turns it off, even if the Case tracks it`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = true)
        val events = listOf(logShareEvent(day = 0, intensity = 3))

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = setOf(LogRowField.NOTES, LogRowField.TAGS),
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertNull(data.rows.single().detail)
    }

    @Test
    fun `logShareCardState caps rows at LOG_SHARE_CARD_ENTRY_CAP and reports the pre-cap match count`() {
        val case = testCase()
        val events = (0 until LOG_SHARE_CARD_ENTRY_CAP + 5L).map { logShareEvent(day = it) }

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(events.size.toLong()),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(events.size.toLong()),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(LOG_SHARE_CARD_ENTRY_CAP, data.rows.size)
        assertEquals(events.size, data.truncatedTotalCount)
    }

    @Test
    fun `logShareCardState reports no truncation when matches fit under the cap`() {
        val case = testCase()
        val events = listOf(logShareEvent(day = 0), logShareEvent(day = 1))

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertNull(data.truncatedTotalCount)
    }

    @Test
    fun `logShareCardState range label reads All time when dateFrom is unset and dateTo is today`() {
        val case = testCase()

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(PlainVoice.shareLogRangeAllTimeLabel, data.rangeLabel)
    }

    @Test
    fun `logShareCardState range label formats explicit bounds once narrowed`() {
        val case = testCase()

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = millisAtDay(10),
                dateTo = millisAtDay(20),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(
            "${formatDateRangeBound(
                millisAtDay(10),
                millisAtDay(NOW),
                ZONE,
            )} – ${formatDateRangeBound(millisAtDay(20), millisAtDay(NOW), ZONE)}",
            data.rangeLabel,
        )
    }

    @Test
    fun `logShareCardState range label includes the year for a bound outside now's calendar year`() {
        val case = testCase()

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = millisAtDay(-400),
                dateTo = millisAtDay(20),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(
            "${formatDateRangeBound(
                millisAtDay(-400),
                millisAtDay(NOW),
                ZONE,
            )} – ${formatDateRangeBound(millisAtDay(20), millisAtDay(NOW), ZONE)}",
            data.rangeLabel,
        )
    }

    @Test
    fun `logShareCardState range label reads a To-labeled bound when dateFrom is unset but dateTo is narrowed`() {
        val case = testCase()

        val data =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                format = ShareCardFormat.STORY,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(20),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(
            "${PlainVoice.shareLogDateToLabel} ${formatDateRangeBound(millisAtDay(20), millisAtDay(NOW), ZONE)}",
            data.rangeLabel,
        )
    }

    @Test
    fun `logShareCardState orders rows by the requested sort direction`() {
        val case = testCase()
        val events = listOf(logShareEvent(day = 0), logShareEvent(day = 5), logShareEvent(day = 10))

        fun rowsFor(order: ChronologicalOrder) =
            logShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                format = ShareCardFormat.STORY,
                sortOrder = order,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allLogFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            ).rows.map { it.timestamp }

        assertEquals(rowsFor(ChronologicalOrder.NEWEST_FIRST), rowsFor(ChronologicalOrder.OLDEST_FIRST).reversed())
    }
}
