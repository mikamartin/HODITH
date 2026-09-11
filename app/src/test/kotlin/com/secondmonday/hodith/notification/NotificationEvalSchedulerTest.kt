package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.NotificationEvalScheduler.Companion.EVAL_DEBOUNCE_MILLIS
import com.secondmonday.hodith.testsupport.Fixtures
import com.secondmonday.hodith.testsupport.millisAtDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

/**
 * The debounce contract: a burst of [NotificationEvalScheduler.schedule] calls for one Case
 * collapses to a single [NotificationEvaluator.evaluateCase], one edit still evaluates (after the
 * window), and distinct Cases are debounced independently. Evaluations are counted by how many
 * times the scheduler pulls a [NotificationEvaluator] from its [Provider] — one per fired
 * evaluation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationEvalSchedulerTest {
    private val repository = FakeHodithRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val clock = FakeClock(millisAtDay(30))
    private val notifier = FakeNotifier()

    private var evaluations = 0
    private val evaluatorProvider =
        Provider {
            evaluations++
            NotificationEvaluator(Provider { repository }, settingsRepository, clock, notifier)
        }

    @Before
    fun setUp() {
        // Both Cases were created at day 0 with check-ins on and nothing logged, so an evaluation
        // at day 30 posts a due check-in — a visible side effect to assert the work actually ran.
        repository.cases.value = listOf(Fixtures.case(id = 1L), Fixtures.case(id = 2L))
    }

    @Test
    fun `a burst of requests for one Case collapses to a single evaluation`() =
        runTest {
            val scheduler = NotificationEvalScheduler(backgroundScope, evaluatorProvider)

            repeat(20) { scheduler.schedule(1L) }
            advanceTimeBy(EVAL_DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertEquals(1, evaluations)
            assertEquals(listOf(1L), notifier.dueCheckIns.map { it.first.id })
        }

    @Test
    fun `interleaved requests for different Cases each evaluate once`() =
        runTest {
            val scheduler = NotificationEvalScheduler(backgroundScope, evaluatorProvider)

            repeat(10) {
                scheduler.schedule(1L)
                scheduler.schedule(2L)
            }
            advanceTimeBy(EVAL_DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertEquals(2, evaluations)
            assertEquals(setOf(1L, 2L), notifier.dueCheckIns.map { it.first.id }.toSet())
        }

    @Test
    fun `a single request evaluates only after the debounce window elapses`() =
        runTest {
            val scheduler = NotificationEvalScheduler(backgroundScope, evaluatorProvider)

            scheduler.schedule(1L)
            advanceTimeBy(EVAL_DEBOUNCE_MILLIS - 1)
            runCurrent()
            assertEquals(0, evaluations)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, evaluations)
        }
}
