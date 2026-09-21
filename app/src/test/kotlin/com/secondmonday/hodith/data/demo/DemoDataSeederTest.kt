package com.secondmonday.hodith.data.demo

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.testsupport.withoutTags
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.insightsTabState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val SEED_SPAN_DAYS = 380
private const val NOW_MILLIS = 1_700_000_000_000L

class DemoDataSeederTest {
    private val repository = FakeHodithRepository()
    private val clock = FakeClock(NOW_MILLIS)
    private val seeder = DemoDataSeeder(repository, clock)

    @Test
    fun `seed inserts seven cases with distinct names and events within the seed span`() =
        runTest {
            seeder.seed()

            val cases = repository.cases.value
            assertEquals(7, cases.size)
            assertEquals(cases.size, cases.map { it.name }.toSet().size)

            val spanStart = NOW_MILLIS - SEED_SPAN_DAYS * MILLIS_PER_DAY
            assertTrue(repository.events.value.isNotEmpty())
            repository.events.value.forEach { event ->
                assertTrue(event.occurredAt in spanStart..NOW_MILLIS)
            }
        }

    @Test
    fun `seed called twice adds a second full set rather than replacing the first`() =
        runTest {
            seeder.seed()
            seeder.seed()

            assertEquals(14, repository.cases.value.size)
        }

    @Test
    fun `seed gives Coffee a recent surge dense enough to show a clear upward trend`() =
        runTest {
            seeder.seed()

            val coffee = repository.cases.value.single { it.name == "Coffee" }
            val windowStart = NOW_MILLIS - 35 * MILLIS_PER_DAY
            val recentCount = repository.events.value.count { it.caseId == coffee.id && it.occurredAt >= windowStart }
            assertTrue(recentCount > 24)
        }

    @Test
    fun `seed gives Nosebleed a quiet spell long enough to set a new longest-gap record`() =
        runTest {
            seeder.seed()

            val nosebleed = repository.cases.value.single { it.name == "Nosebleed" }
            val lastEventAt =
                repository.events.value
                    .filter { it.caseId == nosebleed.id }
                    .maxOf { it.occurredAt }
            val currentGapDays = (NOW_MILLIS - lastEventAt) / MILLIS_PER_DAY
            // SPARSE's own maxGapDays is 45 — a gap safely past that can only be the quiet spell, not luck.
            assertTrue(currentGapDays > 45)
        }

    @Test
    fun `seed gives Nosebleed the Trends went-quiet finding too, not just the Gaps card record`() =
        runTest {
            seeder.seed()

            val nosebleed = repository.cases.value.single { it.name == "Nosebleed" }
            val events = repository.events.value.filter { it.caseId == nosebleed.id }
            // Matches what EventDao.observeMostRecentLoggedAtAcrossActiveCases would return for the
            // full seeded set -- other Cases (Coffee's recent surge, Migraine/Noisy neighbours'
            // ongoing events) keep this well within QUIET_SIGNAL_RECENT_ACTIVITY_WINDOW_DAYS of now.
            val mostRecentActivityAcrossCasesAt = repository.events.value.maxOf { it.loggedAt }
            val state =
                insightsTabState(
                    nosebleed,
                    events.withoutTags(),
                    NOW_MILLIS,
                    mostRecentActivityAcrossCasesAt = mostRecentActivityAcrossCasesAt,
                ) as InsightsTabState.Ready

            assertTrue(state.stats.trends.any { it.kind == TrendFindingKind.WENT_QUIET })
        }

    @Test
    fun `seed gives Lost my keys all three Trends findings at once`() =
        runTest {
            seeder.seed()

            val lostKeys = repository.cases.value.single { it.name == "Lost my keys" }
            val events = repository.events.value.filter { it.caseId == lostKeys.id }
            val state = insightsTabState(lostKeys, events.withoutTags(), NOW_MILLIS) as InsightsTabState.Ready

            // trendingOccurrences (DemoDataSeeder.kt) is deliberately shaped so the isolated,
            // widely-spaced historic era gives way to tight recent clusters — gap shift (shrinking),
            // streak shift (lengthening), and frequency shift (more recently) should all clear their
            // thresholds together, exercising the Trends section's multi-finding/show-more path with
            // real seed data rather than only synthetic fixtures.
            val kinds =
                state.stats.trends
                    .map { it.kind }
                    .toSet()
            assertEquals(setOf(TrendFindingKind.GAP_SHIFT, TrendFindingKind.STREAK_SHIFT, TrendFindingKind.FREQUENCY_SHIFT), kinds)
        }

    @Test
    fun `seed gives Noisy neighbours the Trends recurrence-shape early-spike finding`() =
        runTest {
            seeder.seed()

            // BURSTY density (tight clusters separated by long quiet stretches) already produces an
            // early-spike recurrence shape on its own -- Story C T3 needed no new demo Case, unlike
            // T1's dedicated trendingShift Case. Noisy neighbours shows it as its one and only Trends
            // finding, the cleanest single-finding showcase among the three BURSTY Cases that qualify.
            val neighbours = repository.cases.value.single { it.name == "Noisy neighbours" }
            val events = repository.events.value.filter { it.caseId == neighbours.id }
            val state = insightsTabState(neighbours, events.withoutTags(), NOW_MILLIS) as InsightsTabState.Ready
            val finding = state.stats.trends.single()

            assertEquals(TrendFindingKind.RECURRENCE_SHAPE, finding.kind)
            assertEquals(ShiftDirection.UP, finding.direction)
        }

    @Test
    fun `seed gives Migraine at least the minimum aura-tagged and non-aura sample sizes`() =
        runTest {
            seeder.seed()

            val migraine = repository.cases.value.single { it.name == "Migraine" }
            val eventsWithTags = repository.observeEventsWithTagsForCase(migraine.id).first()
            val withDuration = eventsWithTags.filter { it.event.endedAt != null }
            val auraTagged = withDuration.count { entry -> entry.tags.any { it.name == "aura" } }

            // TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT/...MIN_UNTAGGED_SAMPLE_COUNT (domain, internal) are
            // 15/30 -- asserted as literals here since this test lives outside the domain module's
            // own package and shouldn't need to import detector internals to state its own contract.
            assertTrue(auraTagged >= 15)
            assertTrue(withDuration.size - auraTagged >= 30)
        }

    @Test
    fun `seed gives Migraine the Trends tag-outcome finding for aura`() =
        runTest {
            seeder.seed()

            val migraine = repository.cases.value.single { it.name == "Migraine" }
            val eventsWithTags = repository.observeEventsWithTagsForCase(migraine.id).first()
            val state = insightsTabState(migraine, eventsWithTags, NOW_MILLIS) as InsightsTabState.Ready
            val finding = state.stats.trends.single { it.kind == TrendFindingKind.TAG_OUTCOME && it.tagName == "aura" }

            assertEquals(TagOutcome.DURATION, finding.outcome)
            assertEquals(ShiftDirection.UP, finding.direction)
            assertEquals(TrendReliability.PATTERN, finding.reliability)
        }

    @Test
    fun `seed leaves one Migraine event running now`() =
        runTest {
            seeder.seed()

            val migraine = repository.cases.value.single { it.name == "Migraine" }
            val running = repository.events.value.filter { it.caseId == migraine.id && it.endedAt == null }
            assertEquals(1, running.size)
        }

    @Test
    fun `seed leaves Noisy neighbours with more than one event running at once`() =
        runTest {
            seeder.seed()

            val neighbours = repository.cases.value.single { it.name == "Noisy neighbours" }
            val running = repository.events.value.filter { it.caseId == neighbours.id && it.endedAt == null }
            assertEquals(2, running.size)
        }

    @Test
    fun `seed closes every Workout event since that case has no ongoing seed`() =
        runTest {
            seeder.seed()

            // Workout is START_STOP with ongoingEventCount 0, so nothing on it should be left open
            // — a guard that regular START_STOP events still get a real endedAt.
            val workout = repository.cases.value.single { it.name == "Workout" }
            assertTrue(repository.events.value.none { it.caseId == workout.id && it.endedAt == null })
        }

    @Test
    fun `seed leaves one running event old enough to read as stale`() =
        runTest {
            seeder.seed()

            val startStopCaseIds =
                repository.cases.value
                    .filter { it.durationMode == DurationMode.START_STOP }
                    .map { it.id }
                    .toSet()
            val stale =
                repository.events.value.any {
                    it.caseId in startStopCaseIds && it.endedAt == null && NOW_MILLIS - it.occurredAt >= MILLIS_PER_DAY
                }
            assertTrue(stale)
        }

    @Test
    fun `seed gives Coffee and Migraine a description, and leaves the rest without one`() =
        runTest {
            seeder.seed()

            val cases = repository.cases.value
            val described = setOf("Coffee", "Migraine")
            cases.filter { it.name in described }.forEach { assertTrue(!it.description.isNullOrBlank()) }
            cases.filterNot { it.name in described }.forEach { assertTrue(it.description == null) }
        }

    @Test
    fun `seed gives Coffee's recent surge a genuine multi-day streak`() =
        runTest {
            seeder.seed()

            val coffee = repository.cases.value.single { it.name == "Coffee" }
            val zone = ZoneId.systemDefault()
            val activeDates =
                repository.events.value
                    .filter { it.caseId == coffee.id }
                    .map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
                    .distinct()
                    .sorted()

            // RECENT_SURGE_DAYS (DemoDataSeeder.kt) is 12 — one event lands on every one of those
            // consecutive days, so the surge alone guarantees a streak at least that long.
            assertTrue(longestConsecutiveRun(activeDates) >= 12)
        }
}

private fun longestConsecutiveRun(dates: List<LocalDate>): Int {
    if (dates.isEmpty()) return 0
    var longest = 1
    var current = 1
    for (i in 1 until dates.size) {
        current = if (dates[i].toEpochDay() == dates[i - 1].toEpochDay() + 1) current + 1 else 1
        longest = maxOf(longest, current)
    }
    return longest
}
