package com.secondmonday.hodith.data.demo

import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.domain.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MILLIS = 24L * 60 * 60 * 1000L
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

            val spanStart = NOW_MILLIS - SEED_SPAN_DAYS * DAY_MILLIS
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
}
