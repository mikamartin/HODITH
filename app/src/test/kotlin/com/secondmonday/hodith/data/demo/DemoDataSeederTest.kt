package com.secondmonday.hodith.data.demo

import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
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
    fun `seed inserts six cases with distinct names and events within the seed span`() =
        runTest {
            seeder.seed()

            val cases = repository.cases.value
            assertEquals(6, cases.size)
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

            assertEquals(12, repository.cases.value.size)
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
    fun `seed gives Lost my keys a quiet spell long enough to set a new longest-gap record`() =
        runTest {
            seeder.seed()

            val lostKeys = repository.cases.value.single { it.name == "Lost my keys" }
            val lastEventAt =
                repository.events.value
                    .filter { it.caseId == lostKeys.id }
                    .maxOf { it.occurredAt }
            val currentGapDays = (NOW_MILLIS - lastEventAt) / MILLIS_PER_DAY
            // SPARSE's own maxGapDays is 45 — a gap safely past that can only be the quiet spell, not luck.
            assertTrue(currentGapDays > 45)
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
