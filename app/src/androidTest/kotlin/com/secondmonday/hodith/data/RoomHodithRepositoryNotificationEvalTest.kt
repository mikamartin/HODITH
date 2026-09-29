package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.FakeNotifier
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import com.secondmonday.hodith.notification.NotificationEvaluator
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

private const val DAY_MILLIS = 86_400_000L
private const val AWAIT_TIMEOUT_MILLIS = 5_000L
private const val AWAIT_POLL_MILLIS = 20L

/**
 * [RoomHodithRepository]'s notification-eval side effect (spec §11: every event mutation
 * schedules an evaluation, not just the periodic sweep) against a real [HodithDatabase] and a
 * real [NotificationEvalScheduler]/[NotificationEvaluator] — only the Notifier is faked, to make
 * an evaluation's outcome observable. `NotificationEvalSchedulerTest` (JVM) proves the
 * scheduler/evaluator debounce contract in isolation against `FakeHodithRepository`; this class
 * proves the repository wrapper actually invokes that chain from each of its five event-mutation
 * methods, and in particular that [RoomHodithRepository.deleteEventsOlderThan] fetches affected
 * Case ids *before* deleting — fetching after would find the rows already gone.
 *
 * Real wall-clock waiting, not `kotlinx-coroutines-test` virtual time: the scheduler's debounced
 * evaluation crosses into Room's own real query-executor threads, which a `TestDispatcher`'s
 * virtual clock doesn't control — `advanceTimeBy`/`runCurrent` would return before that real,
 * cross-thread work actually finishes. [awaitNotification] instead polls for the real side effect.
 */
@RunWith(AndroidJUnit4::class)
class RoomHodithRepositoryNotificationEvalTest {
    private lateinit var db: HodithDatabase
    private lateinit var schedulerScope: CoroutineScope

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        schedulerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun tearDown() {
        schedulerScope.cancel()
        db.close()
    }

    private class EvaluationCount {
        var value = 0
    }

    /** Every check-in-enabled Case's evaluation deterministically fires exactly one of these. */
    private fun FakeNotifier.notifiedCaseIds(): List<Long> = dueCheckIns.map { it.first.id } + cancelledCheckIns

    /** Polls for the debounced evaluation's real side effect instead of controlling virtual time (see class doc). */
    private suspend fun awaitNotification(notifier: FakeNotifier) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS
        while (notifier.notifiedCaseIds().isEmpty()) {
            if (System.currentTimeMillis() >= deadline) fail("Timed out waiting for a notification-eval side effect")
            delay(AWAIT_POLL_MILLIS)
        }
    }

    /**
     * Wires a real [RoomHodithRepository] to a real [NotificationEvalScheduler] + real
     * [NotificationEvaluator], counting evaluator invocations in [evaluationCount]. The
     * [Provider]<[HodithRepository]> cycle is resolved the same way Dagger resolves it in
     * production: `repository` is captured by reference inside the lambda, not read until
     * [NotificationEvaluator.evaluateCase] actually runs, by which point it's assigned.
     */
    private fun buildRepository(
        notifier: FakeNotifier,
        clock: FakeClock,
        evaluationCount: EvaluationCount,
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ): RoomHodithRepository {
        lateinit var repository: RoomHodithRepository
        val evaluatorProvider =
            Provider {
                evaluationCount.value++
                NotificationEvaluator(Provider { repository }, settingsRepository, clock, notifier)
            }
        repository =
            RoomHodithRepository(
                database = db,
                caseDao = db.caseDao(),
                eventDao = db.eventDao(),
                tagDao = db.tagDao(),
                hunchDao = db.hunchDao(),
                triggerDao = db.triggerDao(),
                notificationEvalScheduler = NotificationEvalScheduler(schedulerScope, evaluatorProvider),
            )
        return repository
    }

    private suspend fun insertCheckInEligibleCase(): Long = db.caseDao().insert(testCase(checkInsEnabled = true, createdAt = 0L))

    @Smoke
    @Test
    fun insertEvent_evaluatesNotificationsForItsCase() =
        runBlocking {
            val notifier = FakeNotifier()
            val evaluationCount = EvaluationCount()
            val repository = buildRepository(notifier, FakeClock(0L), evaluationCount)
            val caseId = insertCheckInEligibleCase()

            repository.insertEvent(testEvent(caseId = caseId, occurredAt = 0L))
            awaitNotification(notifier)

            assertEquals(1, evaluationCount.value)
            assertEquals(listOf(caseId), notifier.notifiedCaseIds())
        }

    @Test
    fun updateEvent_evaluatesNotificationsForItsCase() =
        runBlocking {
            val notifier = FakeNotifier()
            val evaluationCount = EvaluationCount()
            val repository = buildRepository(notifier, FakeClock(0L), evaluationCount)
            val caseId = insertCheckInEligibleCase()
            // Via the raw DAO, not repository.insertEvent, so evaluationCount starts at 0 and this
            // test proves only updateEvent's own side effect.
            val eventId = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 0L))

            repository.updateEvent(testEvent(id = eventId, caseId = caseId, occurredAt = 0L, note = "updated"))
            awaitNotification(notifier)

            assertEquals(1, evaluationCount.value)
            assertEquals(listOf(caseId), notifier.notifiedCaseIds())
        }

    @Test
    fun deleteEvent_evaluatesNotificationsForItsCase() =
        runBlocking {
            val notifier = FakeNotifier()
            val evaluationCount = EvaluationCount()
            val repository = buildRepository(notifier, FakeClock(0L), evaluationCount)
            val caseId = insertCheckInEligibleCase()
            val eventId = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 0L))
            val event = requireNotNull(db.eventDao().getById(eventId))

            repository.deleteEvent(event)
            awaitNotification(notifier)

            assertEquals(1, evaluationCount.value)
            assertEquals(listOf(caseId), notifier.notifiedCaseIds())
        }

    @Test
    fun deleteEventById_evaluatesNotificationsForItsCase() =
        runBlocking {
            val notifier = FakeNotifier()
            val evaluationCount = EvaluationCount()
            val repository = buildRepository(notifier, FakeClock(0L), evaluationCount)
            val caseId = insertCheckInEligibleCase()
            val eventId = db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 0L))

            repository.deleteEventById(eventId)
            awaitNotification(notifier)

            assertEquals(1, evaluationCount.value)
            assertEquals(listOf(caseId), notifier.notifiedCaseIds())
        }

    @Test
    fun deleteEventsOlderThan_evaluatesOnlyTheCaseWhoseEventsWereActuallyDeleted() =
        runBlocking {
            // Regression pin for deleteEventsOlderThan's fetch-then-delete order: it reads
            // getCaseIdsWithEventsOlderThan(cutoff) BEFORE deleting. If that order were ever
            // reversed, Case A's only qualifying event would already be gone by the time the
            // affected-ids query ran, that query would return empty, and evaluationCount/
            // notifiedCaseIds below would both be empty too — silently dropping the eval.
            val notifier = FakeNotifier()
            val evaluationCount = EvaluationCount()
            val repository = buildRepository(notifier, FakeClock(100 * DAY_MILLIS), evaluationCount)
            val caseIdA = insertCheckInEligibleCase()
            db.eventDao().insert(testEvent(caseId = caseIdA, occurredAt = 10 * DAY_MILLIS))
            // Case B is a control: its event survives the cutoff, so it must NOT be evaluated —
            // proving the assertion below is "exactly A", not merely "not empty".
            val caseIdB = insertCheckInEligibleCase()
            db.eventDao().insert(testEvent(caseId = caseIdB, occurredAt = 80 * DAY_MILLIS))

            repository.deleteEventsOlderThan(cutoff = 50 * DAY_MILLIS)
            awaitNotification(notifier)

            assertEquals(1, evaluationCount.value)
            assertEquals(listOf(caseIdA), notifier.notifiedCaseIds())
        }
}
