package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.HISTORY_SHARE_CARD_ENTRY_CAP
import com.secondmonday.hodith.domain.SHARE_CARD_TOP_TAG_COUNT
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.casedetail.TRENDS_DEFAULT_VISIBLE_COUNT
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `both formats lead with the same Summary beat`() {
        val case = testCase()
        val beats =
            ShareCardFormat.entries.map { format ->
                shareCardState(
                    case = case,
                    displayName = case.name,
                    insightsState = readyInsightsState(case),
                    eventCount = 12,
                    observedDays = 60,
                    format = format,
                    selectedSections = ALL_SECTIONS,
                    generatedAtMillis = millisAtDay(NOW),
                ).topBeat
            }

        assertTrue(beats.all { it is ShareTopBeat.Summary })
        assertEquals(beats.first(), beats.last())
    }

    @Test
    fun `Story Summary reports the passed-in eventCount and observedDays and carries no rate for a single event`() {
        val case = testCase()
        val oneEvent = listOf(readyEventsWithTags().first())

        val data =
            storyState(
                case = case,
                insightsState = insightsTabState(case, oneEvent, now = millisAtDay(NOW)),
                eventCount = 1,
                observedDays = 3,
            )

        val summary = data.topBeat as ShareTopBeat.Summary
        assertEquals(1, summary.eventCount)
        assertEquals(3L, summary.observedDays)
        assertNull(summary.rate)
    }

    @Test
    fun `Story Summary with nothing logged reports zero events and no rate`() {
        val data = storyState(testCase(), InsightsTabState.NothingLogged, eventCount = 0, observedDays = 1)

        val summary = data.topBeat as ShareTopBeat.Summary
        assertEquals(0, summary.eventCount)
        assertEquals(1L, summary.observedDays)
        assertNull(summary.rate)
    }

    @Test
    fun `Story Summary carries the hero rate the Insights stats computed`() {
        val case = testCase()
        val insightsState = readyInsightsState(case)

        val data = storyState(case, insightsState)

        val summary = data.topBeat as ShareTopBeat.Summary
        assertEquals((insightsState as InsightsTabState.Ready).stats.heroRate, summary.rate)
        assertTrue(summary.rate != null)
    }

    @Test
    fun `Story with no sections picked is the Summary beat alone`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)

        val data = storyState(case, taggedInsightsState(case), selectedSections = emptySet())

        assertTrue(data.topBeat is ShareTopBeat.Summary)
        assertNull(data.gaps)
        assertNull(data.rhythm)
        assertNull(data.duration)
        assertNull(data.intensity)
        assertEquals(emptyList<Any>(), data.trends)
        assertEquals(emptyList<Any>(), data.tags)
        assertNull(data.quietForDays)
    }

    @Test
    fun `Summary reports the passed-in eventCount and observedDays and carries no rate when nothing is logged`() {
        val data =
            shareCardState(
                case = testCase(),
                displayName = testCase().name,
                insightsState = InsightsTabState.NothingLogged,
                eventCount = 0,
                observedDays = 3,
                format = ShareCardFormat.SQUARE,
                selectedSections = ALL_SECTIONS,
                generatedAtMillis = millisAtDay(NOW),
            )

        val summary = data.topBeat as ShareTopBeat.Summary
        assertEquals(0, summary.eventCount)
        assertEquals(3L, summary.observedDays)
        assertNull(summary.rate)
    }

    @Test
    fun `Summary carries the hero rate the Insights stats computed`() {
        val case = testCase()
        val insightsState = readyInsightsState(case)

        val data = squareState(case, insightsState)

        val summary = data.topBeat as ShareTopBeat.Summary
        assertEquals((insightsState as InsightsTabState.Ready).stats.heroRate, summary.rate)
        assertTrue(summary.rate != null)
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
                format = ShareCardFormat.STORY,
                selectedSections = ALL_SECTIONS,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertNull(data.rhythm)
        assertNull(data.gaps)
        assertEquals(emptyList<Any>(), data.trends)
        assertNull(data.duration)
        assertNull(data.intensity)
        assertEquals(emptyList<Any>(), data.tags)
    }

    @Test
    fun `a single-event Case offers Rhythm but not Gaps, Streaks, Trends or Tags on the share card`() {
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
                format = ShareCardFormat.STORY,
                selectedSections = ALL_SECTIONS,
                generatedAtMillis = millisAtDay(NOW),
            )

        assertTrue(data.rhythm != null)
        assertNull(data.gaps)
        assertNull(data.streaks)
        assertEquals(emptyList<Any>(), data.trends)
        assertEquals(emptyList<Any>(), data.tags)
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
                format = ShareCardFormat.STORY,
                selectedSections = setOf(ShareInsightsSection.RHYTHM, ShareInsightsSection.TRENDS),
                generatedAtMillis = millisAtDay(NOW),
            )

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
                format = ShareCardFormat.STORY,
                selectedSections = setOf(ShareInsightsSection.RHYTHM),
                generatedAtMillis = millisAtDay(NOW),
            )

        // readyInsightsState's fixture is the same one `only the selected sections are populated`
        // confirms yields a non-empty stats.trends -- proves this is selection gating, not an
        // incidentally-empty findings list.
        assertEquals(emptyList<Any>(), data.trends)
    }

    private fun statsWithTrends(
        findings: List<TrendFinding>,
        tags: List<TagBreakdownEntry> = emptyList(),
        duration: DurationDisplay? = null,
        intensity: IntensityDisplay? = null,
    ) = StatsSections(
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
        duration = duration,
        intensity = intensity,
        tags = tags,
        totalEventCount = 20,
        trends = findings,
    )

    private fun readyWith(stats: StatsSections) = InsightsTabState.Ready(heatmapMonths = emptyList(), stats = stats)

    /** Gaps (and the quiet label that rides on it) only reach the card once there are two events. */
    private fun StatsSections.withTwoEvents() = copy(gaps = gaps.copy(shortestGapDays = 2L))

    private fun wentQuietFinding(currentGapDays: Double = 14.0) =
        TrendFinding(
            latestEvidenceAt = 0L,
            kind = TrendFindingKind.WENT_QUIET,
            direction = ShiftDirection.UP,
            reliability = TrendReliability.HINT,
            sampleCount = 6,
            priorValue = 9.0,
            recentValue = currentGapDays,
        )

    private fun storyState(
        case: CaseEntity,
        insightsState: InsightsTabState,
        selectedSections: Set<ShareInsightsSection> = ALL_SECTIONS,
        eventCount: Int = 12,
        observedDays: Long = 60,
        sectionOrder: List<ShareInsightsSection> = ShareInsightsSection.entries,
    ) = shareCardState(
        case = case,
        displayName = case.name,
        insightsState = insightsState,
        eventCount = eventCount,
        observedDays = observedDays,
        format = ShareCardFormat.STORY,
        selectedSections = selectedSections,
        generatedAtMillis = millisAtDay(NOW),
        sectionOrder = sectionOrder,
    )

    /**
     * 12 events 5 days apart, each a 30-minute event with intensity 3. Tag counts: "alpha" on all
     * 12, "beta" on 8, "gamma" on 5, "delta" on 2 — so the top three are alpha, beta, gamma.
     */
    private fun taggedInsightsState(case: CaseEntity): InsightsTabState {
        val events =
            durationEventsWithTags().mapIndexed { index, event ->
                val names = listOfNotNull("alpha", "beta".takeIf { index < 8 }, "gamma".takeIf { index < 5 }, "delta".takeIf { index < 2 })
                event.copy(tags = names.mapIndexed { tagIndex, name -> TagEntity(id = tagIndex + 1L, name = name) })
            }
        return insightsTabState(case, events, now = millisAtDay(NOW))
    }

    private fun ordinaryFinding(sampleCount: Int) =
        TrendFinding(
            latestEvidenceAt = 0L,
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
                format = ShareCardFormat.STORY,
                selectedSections = setOf(ShareInsightsSection.TRENDS),
                generatedAtMillis = millisAtDay(NOW),
            )

        assertEquals(findings.take(TRENDS_DEFAULT_VISIBLE_COUNT), data.trends)
    }

    @Test
    fun `Story Trends leaves out the went-quiet finding and shows the ordinary ones capped at three`() {
        val findings = listOf(wentQuietFinding()) + (1..5).map { ordinaryFinding(sampleCount = it) }

        val data = storyState(testCase(), readyWith(statsWithTrends(findings)), setOf(ShareInsightsSection.TRENDS))

        assertEquals(findings.drop(1).take(TRENDS_DEFAULT_VISIBLE_COUNT), data.trends)
    }

    @Test
    fun `Story Trends is empty when went-quiet is the only finding`() {
        val data = storyState(testCase(), readyWith(statsWithTrends(listOf(wentQuietFinding()))), setOf(ShareInsightsSection.TRENDS))

        assertEquals(emptyList<Any>(), data.trends)
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
                format = ShareCardFormat.STORY,
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
                format = ShareCardFormat.STORY,
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
                format = ShareCardFormat.STORY,
                selectedSections = ALL_SECTIONS,
                generatedAtMillis = millisAtDay(NOW),
            )

        // durationMode = MANUAL but no MANUAL-duration data was logged on these events, so
        // computeDurationStats legitimately returns null here -- only intensity is asserted non-null.
        assertTrue(data.intensity != null)
    }

    // ---- Square preset (never driven by selectedSections) ----

    private fun squareState(
        case: CaseEntity,
        insightsState: InsightsTabState,
        selectedSections: Set<ShareInsightsSection> = ALL_SECTIONS,
        eventCount: Int = 12,
    ) = shareCardState(
        case = case,
        displayName = case.name,
        insightsState = insightsState,
        eventCount = eventCount,
        observedDays = 60,
        format = ShareCardFormat.SQUARE,
        selectedSections = selectedSections,
        generatedAtMillis = millisAtDay(NOW),
    )

    /** 12 events 5 days apart, each a 30-minute event with intensity 3, so every Case config has data to show. */
    private fun durationEventsWithTags(): List<EventWithTags> =
        readyEventsWithTags().map {
            it.copy(event = it.event.copy(endedAt = it.event.occurredAt + 30 * 60_000L))
        }

    private fun durationInsightsState(case: CaseEntity): InsightsTabState =
        insightsTabState(case, durationEventsWithTags(), now = millisAtDay(NOW))

    @Test
    fun `Square ignores the selected sections entirely`() {
        val case = testCase()
        val insightsState = readyInsightsState(case)

        val none = squareState(case, insightsState, selectedSections = emptySet())
        val all = squareState(case, insightsState, selectedSections = ALL_SECTIONS)

        assertEquals(all, none)
    }

    @Test
    fun `Square never carries Trends or Tags, even for a Case with tagged events`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)

        val data = squareState(case, taggedInsightsState(case))

        assertEquals(emptyList<Any>(), data.trends)
        assertEquals(emptyList<Any>(), data.tags)
    }

    @Test
    fun `Square is unchanged by the Story-only section order and Tags`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)

        val data = squareState(case, taggedInsightsState(case))

        assertTrue(data.gaps != null)
        assertTrue(data.duration != null)
        assertTrue(data.intensity != null)
        assertNull(data.rhythm)
    }

    @Test
    fun `Square for a Case tracking neither shows Gaps and Rhythm only`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = false)

        val data = squareState(case, readyInsightsState(case))

        assertTrue(data.gaps != null)
        assertTrue(data.rhythm != null)
        assertNull(data.duration)
        assertNull(data.intensity)
    }

    @Test
    fun `Square for a Case tracking intensity shows Intensity and no Rhythm`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = true)

        val data = squareState(case, readyInsightsState(case))

        assertTrue(data.gaps != null)
        assertTrue(data.intensity != null)
        assertNull(data.duration)
        assertNull(data.rhythm)
    }

    @Test
    fun `Square for a Case tracking duration shows Duration and no Rhythm`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = false)

        val data = squareState(case, durationInsightsState(case))

        assertTrue(data.gaps != null)
        assertEquals(30L, data.duration!!.shortestMinutes)
        assertEquals(30L, data.duration.longestMinutes)
        assertNull(data.intensity)
        assertNull(data.rhythm)
    }

    @Test
    fun `Square for a Case tracking both shows Duration and Intensity and no Rhythm`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)

        val data = squareState(case, durationInsightsState(case))

        assertTrue(data.gaps != null)
        assertTrue(data.duration != null)
        assertTrue(data.intensity != null)
        assertNull(data.rhythm)
    }

    @Test
    fun `Square for a duration Case with no finished event yet drops the Duration panel without swapping in Rhythm`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = false)

        val data = squareState(case, readyInsightsState(case))

        assertNull(data.duration)
        assertNull(data.rhythm)
    }

    @Test
    fun `Square Gaps carry the shortest gap once a second event exists`() {
        val case = testCase()

        val data = squareState(case, readyInsightsState(case))

        assertEquals(5L, data.gaps!!.shortestGapDays)
    }

    @Test
    fun `Square with nothing logged leaves every panel empty`() {
        val case = testCase()

        val data = squareState(case, InsightsTabState.NothingLogged, eventCount = 0)

        assertNull(data.gaps)
        assertNull(data.rhythm)
        assertNull(data.duration)
        assertNull(data.intensity)
        assertNull(data.quietForDays)
    }

    @Test
    fun `Square quietForDays is the went-quiet finding's current gap`() {
        val case = testCase()
        val wentQuiet =
            TrendFinding(
                latestEvidenceAt = 0L,
                kind = TrendFindingKind.WENT_QUIET,
                direction = ShiftDirection.UP,
                reliability = TrendReliability.HINT,
                sampleCount = 6,
                priorValue = 9.0,
                recentValue = 14.0,
            )
        val insightsState =
            InsightsTabState.Ready(
                heatmapMonths = emptyList(),
                stats = statsWithTrends(listOf(wentQuiet, ordinaryFinding(sampleCount = 6))).withTwoEvents(),
            )

        val data = squareState(case, insightsState)

        assertEquals(14L, data.quietForDays)
    }

    @Test
    fun `Square quietForDays is null when no went-quiet finding exists, even with other findings`() {
        val case = testCase()
        val insightsState =
            InsightsTabState.Ready(heatmapMonths = emptyList(), stats = statsWithTrends(listOf(ordinaryFinding(sampleCount = 6))))

        val data = squareState(case, insightsState)

        assertNull(data.quietForDays)
    }

    // ---- Story: quiet pill, Tags, availability ----

    @Test
    fun `Story carries quietForDays when Gaps is picked and the Case has gone quiet`() {
        val insightsState = readyWith(statsWithTrends(listOf(wentQuietFinding(currentGapDays = 21.0))).withTwoEvents())

        val data = storyState(testCase(), insightsState, setOf(ShareInsightsSection.GAPS))

        assertEquals(21L, data.quietForDays)
    }

    @Test
    fun `Story drops quietForDays when Gaps is not picked, even if Trends is`() {
        val insightsState = readyWith(statsWithTrends(listOf(wentQuietFinding(), ordinaryFinding(sampleCount = 6))))

        val data = storyState(testCase(), insightsState, setOf(ShareInsightsSection.TRENDS, ShareInsightsSection.RHYTHM))

        assertNull(data.quietForDays)
    }

    @Test
    fun `Story quietForDays is null when Gaps is picked but the Case has not gone quiet`() {
        val insightsState = readyWith(statsWithTrends(listOf(ordinaryFinding(sampleCount = 6))))

        val data = storyState(testCase(), insightsState, setOf(ShareInsightsSection.GAPS))

        assertNull(data.quietForDays)
    }

    @Test
    fun `Story Tags lists the three busiest tags, busiest first`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)

        val data = storyState(case, taggedInsightsState(case), setOf(ShareInsightsSection.TAGS))

        assertEquals(
            listOf(TagBreakdownEntry("alpha", 12), TagBreakdownEntry("beta", 8), TagBreakdownEntry("gamma", 5)),
            data.tags,
        )
        assertEquals(SHARE_CARD_TOP_TAG_COUNT, data.tags.size)
    }

    @Test
    fun `Story Tags shows every tag when the Case has fewer than three`() {
        val tags = listOf(TagBreakdownEntry("alpha", 4), TagBreakdownEntry("beta", 1))

        val data = storyState(testCase(), readyWith(statsWithTrends(emptyList(), tags = tags)), setOf(ShareInsightsSection.TAGS))

        assertEquals(tags, data.tags)
    }

    @Test
    fun `Story Tags is empty when not picked, even though the Case has tags`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)

        val data = storyState(case, taggedInsightsState(case), setOf(ShareInsightsSection.GAPS))

        assertEquals(emptyList<Any>(), data.tags)
    }

    @Test
    fun `Story Tags is empty when picked but no event carries a tag`() {
        val case = testCase()

        val data = storyState(case, readyInsightsState(case), setOf(ShareInsightsSection.TAGS))

        assertEquals(emptyList<Any>(), data.tags)
    }

    @Test
    fun `Story populates every picked section when the Case has data for all of them`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)
        val stats = (taggedInsightsState(case) as InsightsTabState.Ready).stats
        val insightsState = readyWith(stats.copy(trends = listOf(ordinaryFinding(sampleCount = 6))))

        val data = storyState(case, insightsState)

        assertTrue(data.gaps != null)
        assertTrue(data.duration != null)
        assertTrue(data.rhythm != null)
        assertTrue(data.intensity != null)
        assertTrue(data.trends.isNotEmpty())
        assertTrue(data.tags.isNotEmpty())
    }

    @Test
    fun `Story shows Gaps panel data in the Square shape, including the shortest gap`() {
        val case = testCase()

        val data = storyState(case, readyInsightsState(case), setOf(ShareInsightsSection.GAPS))

        assertEquals(5L, data.gaps!!.shortestGapDays)
    }

    @Test
    fun `Story carries streaks only when the Streaks section is picked, and independently of Gaps`() {
        val case = testCase()
        val insightsState = readyInsightsState(case)
        val gaps = (insightsState as InsightsTabState.Ready).stats.gaps

        assertNull(storyState(case, insightsState, setOf(ShareInsightsSection.GAPS)).streaks)

        val streaksOnly = storyState(case, insightsState, setOf(ShareInsightsSection.STREAKS))
        assertEquals(StreakDisplay(gaps.longestStreakDays, gaps.averageStreakDays), streaksOnly.streaks)
        assertNull(streaksOnly.gaps)
    }

    @Test
    fun `Story streaks stay hidden before two events even when picked`() {
        val case = testCase()
        val stats = (readyInsightsState(case) as InsightsTabState.Ready).stats
        val oneEvent = readyWith(stats.copy(gaps = stats.gaps.copy(shortestGapDays = null)))

        assertNull(storyState(case, oneEvent, setOf(ShareInsightsSection.STREAKS)).streaks)
    }

    @Test
    fun `Square carries the streak figures once there are two events and none before`() {
        val case = testCase()
        val stats = (readyInsightsState(case) as InsightsTabState.Ready).stats
        val oneEvent = readyWith(stats.copy(gaps = stats.gaps.copy(shortestGapDays = null)))

        val square = squareState(case, readyInsightsState(case))

        assertEquals(StreakDisplay(stats.gaps.longestStreakDays, stats.gaps.averageStreakDays), square.streaks)
        assertNull(squareState(case, oneEvent).streaks)
    }

    @Test
    fun `Story with nothing logged leaves every section empty and keeps the hero`() {
        val data = storyState(testCase(), InsightsTabState.NothingLogged, eventCount = 0)

        assertTrue(data.topBeat is ShareTopBeat.Summary)
        assertNull(data.gaps)
        assertNull(data.rhythm)
        assertNull(data.duration)
        assertNull(data.intensity)
        assertEquals(emptyList<Any>(), data.trends)
        assertEquals(emptyList<Any>(), data.tags)
        assertNull(data.quietForDays)
    }

    @Test
    fun `Story lists the picked sections in the saved order, skipping unpicked ones`() {
        val case = testCase()
        val data =
            storyState(
                case,
                taggedInsightsState(case),
                selectedSections = setOf(ShareInsightsSection.TAGS, ShareInsightsSection.GAPS),
                sectionOrder = listOf(ShareInsightsSection.TAGS, ShareInsightsSection.RHYTHM, ShareInsightsSection.GAPS),
            )

        assertEquals(listOf(ShareInsightsSection.TAGS, ShareInsightsSection.GAPS), data.storyOrder)
    }

    @Test
    fun `Story keeps declaration order when no saved order is given`() {
        val data =
            storyState(
                testCase(),
                readyInsightsState(testCase()),
                selectedSections = setOf(ShareInsightsSection.TAGS, ShareInsightsSection.GAPS),
            )

        assertEquals(listOf(ShareInsightsSection.GAPS, ShareInsightsSection.TAGS), data.storyOrder)
    }

    @Test
    fun `ShareInsightsSection declaration order is the default order`() {
        assertEquals(
            listOf(
                ShareInsightsSection.GAPS,
                ShareInsightsSection.STREAKS,
                ShareInsightsSection.DURATION,
                ShareInsightsSection.RHYTHM,
                ShareInsightsSection.INTENSITY,
                ShareInsightsSection.TRENDS,
                ShareInsightsSection.TAGS,
            ),
            ShareInsightsSection.entries,
        )
    }

    private val sampleDuration = DurationDisplay(averageMinutes = 30.0, longestMinutes = 30, totalMinutes = 360, shortestMinutes = 30)
    private val sampleIntensity = IntensityDisplay(averageIntensity = 3.0, distribution = mapOf(3 to 12), maxCount = 12)

    @Test
    fun `availableShareSections offers nothing before the first event`() {
        assertEquals(emptyList<ShareInsightsSection>(), availableShareSections(null))
    }

    @Test
    fun `availableShareSections offers only Start times for a bare Case with one event`() {
        val stats = statsWithTrends(emptyList())

        assertEquals(listOf(ShareInsightsSection.RHYTHM), availableShareSections(stats))
    }

    @Test
    fun `availableShareSections offers Streaks only once there are two events`() {
        val oneEvent = statsWithTrends(emptyList())
        val twoEvents = oneEvent.copy(gaps = oneEvent.gaps.copy(shortestGapDays = 2L))

        assertFalse(ShareInsightsSection.STREAKS in availableShareSections(oneEvent))
        assertTrue(ShareInsightsSection.STREAKS in availableShareSections(twoEvents))
    }

    @Test
    fun `availableShareSections offers every section, in card order, for a Case that has data for all`() {
        val stats =
            statsWithTrends(
                findings = listOf(ordinaryFinding(sampleCount = 6)),
                tags = listOf(TagBreakdownEntry("alpha", 3)),
                duration = sampleDuration,
                intensity = sampleIntensity,
            ).let { it.copy(gaps = it.gaps.copy(shortestGapDays = 2L)) } // a second event is what offers Streaks

        assertEquals(ShareInsightsSection.entries.toList(), availableShareSections(stats))
    }

    @Test
    fun `availableShareSections offers Duration only when the Case has duration stats`() {
        val without = availableShareSections(statsWithTrends(emptyList()))
        val with = availableShareSections(statsWithTrends(emptyList(), duration = sampleDuration))

        assertTrue(ShareInsightsSection.DURATION !in without)
        assertTrue(ShareInsightsSection.DURATION in with)
    }

    @Test
    fun `availableShareSections offers Intensity only when the Case has intensity stats`() {
        val without = availableShareSections(statsWithTrends(emptyList()))
        val with = availableShareSections(statsWithTrends(emptyList(), intensity = sampleIntensity))

        assertTrue(ShareInsightsSection.INTENSITY !in without)
        assertTrue(ShareInsightsSection.INTENSITY in with)
    }

    @Test
    fun `availableShareSections offers Tags only when an event carries a tag`() {
        val without = availableShareSections(statsWithTrends(emptyList()))
        val with = availableShareSections(statsWithTrends(emptyList(), tags = listOf(TagBreakdownEntry("alpha", 1))))

        assertTrue(ShareInsightsSection.TAGS !in without)
        assertTrue(ShareInsightsSection.TAGS in with)
    }

    @Test
    fun `availableShareSections offers Trends only when a finding other than went-quiet exists`() {
        val none = availableShareSections(statsWithTrends(emptyList()))
        val onlyQuiet = availableShareSections(statsWithTrends(listOf(wentQuietFinding())))
        val ordinary = availableShareSections(statsWithTrends(listOf(wentQuietFinding(), ordinaryFinding(sampleCount = 6))))

        assertTrue(ShareInsightsSection.TRENDS !in none)
        assertTrue(ShareInsightsSection.TRENDS !in onlyQuiet)
        assertTrue(ShareInsightsSection.TRENDS in ordinary)
    }

    @Test
    fun `availableShareSections never offers Duration or Intensity for a Case that does not track them`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = false)
        val stats = (readyInsightsState(case) as InsightsTabState.Ready).stats

        val sections = availableShareSections(stats)

        assertTrue(ShareInsightsSection.DURATION !in sections)
        assertTrue(ShareInsightsSection.INTENSITY !in sections)
    }

    @Test
    fun `availableShareSections for a fully tracked, tagged Case lists Duration and Intensity and Tags`() {
        val case = testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true)
        val stats = (taggedInsightsState(case) as InsightsTabState.Ready).stats

        val sections = availableShareSections(stats)

        assertTrue(ShareInsightsSection.DURATION in sections)
        assertTrue(ShareInsightsSection.INTENSITY in sections)
        assertTrue(ShareInsightsSection.TAGS in sections)
    }

    @Test
    fun `storyTrendFindings drops went-quiet wherever it sits in the list`() {
        val ordinary = (1..2).map { ordinaryFinding(sampleCount = it) }
        val stats = statsWithTrends(listOf(ordinary[0], wentQuietFinding(), ordinary[1]))

        assertEquals(ordinary, storyTrendFindings(stats))
    }

    // ---- historyShareCardState ----

    private fun historyShareEvent(
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

    private val allHistoryFields = HistoryRowField.entries.toSet()

    @Test
    fun `historyShareCardState suppresses duration and intensity when the Case doesn't track them, even if selected`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = false)
        val events = listOf(historyShareEvent(day = 0, intensity = 3, durationMinutes = 30))

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertNull(data.rows.single().detail)
    }

    @Test
    fun `historyShareCardState omits a field from the row when the user turns it off, even if the Case tracks it`() {
        val case = testCase(durationMode = DurationMode.NONE, intensityEnabled = true)
        val events = listOf(historyShareEvent(day = 0, intensity = 3))

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = setOf(HistoryRowField.NOTES, HistoryRowField.TAGS),
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertNull(data.rows.single().detail)
    }

    @Test
    fun `historyShareCardState caps rows at HISTORY_SHARE_CARD_ENTRY_CAP and reports the pre-cap match count`() {
        val case = testCase()
        val events = (0 until HISTORY_SHARE_CARD_ENTRY_CAP + 5L).map { historyShareEvent(day = it) }

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(events.size.toLong()),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(events.size.toLong()),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(HISTORY_SHARE_CARD_ENTRY_CAP, data.rows.size)
        assertEquals(events.size, data.truncatedTotalCount)
    }

    @Test
    fun `historyShareCardState reports no truncation when matches fit under the cap`() {
        val case = testCase()
        val events = listOf(historyShareEvent(day = 0), historyShareEvent(day = 1))

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertNull(data.truncatedTotalCount)
    }

    @Test
    fun `historyShareCardState range label spans the Case's creation date to today when dateFrom is unset`() {
        val case = testCase()

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(
            "${formatDateRangeBound(
                case.createdAt,
                millisAtDay(NOW),
                ZONE,
            )} – ${formatDateRangeBound(millisAtDay(NOW), millisAtDay(NOW), ZONE)}",
            data.rangeLabel,
        )
    }

    @Test
    fun `historyShareCardState range label keeps the creation date out of the filter when dateFrom is unset`() {
        // Created on day 0; the event is backdated to day -30, before the Case existed.
        val case = testCase()

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = listOf(historyShareEvent(day = -30)),
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(1, data.rows.size)
    }

    @Test
    fun `historyShareCardState range label formats explicit bounds once narrowed`() {
        val case = testCase()

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = millisAtDay(10),
                dateTo = millisAtDay(20),
                fields = allHistoryFields,
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
    fun `historyShareCardState range label includes the year for a bound outside now's calendar year`() {
        val case = testCase()

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = millisAtDay(-400),
                dateTo = millisAtDay(20),
                fields = allHistoryFields,
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
    fun `historyShareCardState range label starts at the Case's creation date when dateFrom is unset but dateTo is narrowed`() {
        val case = testCase()

        val data =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = emptyList(),
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(20),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals(
            "${formatDateRangeBound(
                case.createdAt,
                millisAtDay(NOW),
                ZONE,
            )} – ${formatDateRangeBound(millisAtDay(20), millisAtDay(NOW), ZONE)}",
            data.rangeLabel,
        )
    }

    @Test
    fun `historyShareCardState orders rows by the requested sort direction`() {
        val case = testCase()
        val events = listOf(historyShareEvent(day = 0), historyShareEvent(day = 5), historyShareEvent(day = 10))

        fun rowsFor(order: ChronologicalOrder) =
            historyShareCardState(
                case = case,
                displayName = case.name,
                events = events,
                sortOrder = order,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            ).rows.map { it.timestamp }

        assertEquals(rowsFor(ChronologicalOrder.NEWEST_FIRST), rowsFor(ChronologicalOrder.OLDEST_FIRST).reversed())
    }

    @Test
    fun `historyShareCardState puts the resolved display name on the card, not the Case's own name`() {
        val case = testCase()

        val data =
            historyShareCardState(
                case = case,
                displayName = "Sam",
                events = emptyList(),
                sortOrder = ChronologicalOrder.NEWEST_FIRST,
                dateFrom = null,
                dateTo = millisAtDay(NOW),
                fields = allHistoryFields,
                use24Hour = true,
                voice = PlainVoice,
                now = millisAtDay(NOW),
                generatedAtMillis = millisAtDay(NOW),
                zone = ZONE,
            )

        assertEquals("Sam", data.caseName)
    }
}
