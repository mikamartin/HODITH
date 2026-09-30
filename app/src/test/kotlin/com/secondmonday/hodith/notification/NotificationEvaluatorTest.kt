package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class NotificationEvaluatorTest {
    private val repository = FakeHodithRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val clock = FakeClock(millisAtDay(30))
    private val notifier = FakeNotifier()

    private lateinit var evaluator: NotificationEvaluator

    @Before
    fun setUp() {
        evaluator = NotificationEvaluator(Provider { repository }, settingsRepository, clock, notifier)
    }

    private fun case(
        id: Long = 1L,
        createdAt: Long = 0L,
        checkInsEnabled: Boolean = false,
        lastCheckInAt: Long? = null,
        archived: Boolean = false,
        durationMode: DurationMode = DurationMode.NONE,
    ) = CaseEntity(
        id = id,
        name = "Coffee",
        icon = "☕️",
        createdAt = createdAt,
        logFlow = LogFlow.DETAIL_SHEET,
        durationMode = durationMode,
        intensityEnabled = false,
        checkInsEnabled = checkInsEnabled,
        lastCheckInAt = lastCheckInAt,
        sortOrder = 0,
        archived = archived,
    )

    private fun notification(
        id: Long = 1L,
        caseId: Long = 1L,
        kind: NotificationKind = NotificationKind.OFTEN,
        threshold: Int = 3,
        windowDays: Int? = 7,
        enabled: Boolean = true,
        armed: Boolean = true,
        lastFiredAt: Long? = null,
    ) = NotificationEntity(
        id = id,
        caseId = caseId,
        kind = kind,
        threshold = threshold,
        windowDays = windowDays,
        expectedPer = ExpectedPer.WEEK,
        metric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity = null,
        enabled = enabled,
        armed = armed,
        lastFiredAt = lastFiredAt,
    )

    private fun event(
        id: Long = 0L,
        caseId: Long = 1L,
        occurredAt: Long,
        endedAt: Long? = null,
    ) = testEvent(id = id, caseId = caseId, occurredAt = occurredAt, endedAt = endedAt)

    @Test
    fun `evaluateCase fires an OFTEN notification once its window count reaches threshold`() =
        runTest {
            repository.cases.value = listOf(case())
            repository.notifications.value = listOf(notification(threshold = 3, windowDays = 7))
            repository.events.value = (1..3).map { event(id = it.toLong(), occurredAt = clock.nowMillis()) }

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.firedNotifications.size)
            val updated = repository.notifications.value.single()
            assertTrue(!updated.armed)
            assertEquals(clock.nowMillis(), updated.lastFiredAt)
        }

    @Test
    fun `evaluateCase does not fire an OFTEN notification below threshold`() =
        runTest {
            repository.cases.value = listOf(case())
            repository.notifications.value = listOf(notification(threshold = 3, windowDays = 7))
            repository.events.value = listOf(event(occurredAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedNotifications.isEmpty())
        }

    @Test
    fun `evaluateCase counts an OFTEN duration event that started before the lookback window but is still active inside it`() =
        runTest {
            // Regression: the OFTEN fetch is now unbounded (repo.eventsInWindow(caseId, 0L, now+1)),
            // not windowed by the notification's own lookback, so computeVerdict's own span-overlap
            // logic can see an event whose span started before the window but still reaches into
            // it. Event runs days 0..40; the 7-day lookback from day 30 only opens at day 23 — an
            // occurredAt-only windowed fetch would never have handed this event to the evaluator.
            repository.cases.value = listOf(case(durationMode = DurationMode.MANUAL))
            repository.notifications.value = listOf(notification(threshold = 1, windowDays = 7))
            repository.events.value = listOf(event(occurredAt = millisAtDay(0), endedAt = millisAtDay(40)))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.firedNotifications.size)
        }

    @Test
    fun `evaluateCase ignores a disabled notification`() =
        runTest {
            repository.cases.value = listOf(case())
            repository.notifications.value = listOf(notification(threshold = 1, windowDays = 7, enabled = false))
            repository.events.value = listOf(event(occurredAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedNotifications.isEmpty())
        }

    @Test
    fun `evaluateCase fires a QUIET notification based on the most recent event`() =
        runTest {
            repository.cases.value = listOf(case(createdAt = 0L))
            repository.notifications.value = listOf(notification(kind = NotificationKind.QUIET, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(16)))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.firedNotifications.size)
        }

    @Test
    fun `evaluateCase counts QUIET silence from when a duration event ended, not when it started`() =
        runTest {
            // Event ran days 2..20 and stopped; now is day 30, so 10 quiet days — under the 14-day threshold.
            // Measured from the day-2 start it would be 28 days and would fire.
            repository.cases.value = listOf(case(createdAt = 0L, durationMode = DurationMode.MANUAL))
            repository.notifications.value = listOf(notification(kind = NotificationKind.QUIET, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(2), endedAt = millisAtDay(20)))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedNotifications.isEmpty())
        }

    @Test
    fun `evaluateCase does not fire QUIET while an event is still running on the Case`() =
        runTest {
            // Started day 2, never stopped; now is day 30. A running event is not silence.
            repository.cases.value = listOf(case(createdAt = 0L, durationMode = DurationMode.START_STOP))
            repository.notifications.value = listOf(notification(kind = NotificationKind.QUIET, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(2), endedAt = null))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedNotifications.isEmpty())
        }

    @Test
    fun `evaluateCase does not fire QUIET with several events running on the Case at once`() =
        runTest {
            // Two concurrent open events (retro-log / fast restart, spec §6). The Case is running,
            // so the silence anchor pins to now regardless of how many events are open.
            repository.cases.value = listOf(case(createdAt = 0L, durationMode = DurationMode.START_STOP))
            repository.notifications.value = listOf(notification(kind = NotificationKind.QUIET, threshold = 14, windowDays = null))
            repository.events.value =
                listOf(
                    event(id = 1L, occurredAt = millisAtDay(2), endedAt = null),
                    event(id = 2L, occurredAt = millisAtDay(5), endedAt = null),
                )

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedNotifications.isEmpty())
        }

    @Test
    fun `evaluateCase does not fire a check-in while an event is still running on the Case`() =
        runTest {
            // Spec §11: a still-running event counts as no silence for check-ins too, not just QUIET.
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(case(createdAt = 0L, checkInsEnabled = true, durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(event(occurredAt = millisAtDay(2), endedAt = null))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase counts QUIET silence from occurredAt for a Case that no longer tracks duration`() =
        runTest {
            // Same event as the MANUAL test above (ran days 2..20) but the Case is now NONE, so spec
            // §9/§10 read it as a point: silence counts from the day-2 start = 28 quiet days, which
            // clears the 14-day threshold and fires. Reading the stored day-20 endedAt would give 10.
            repository.cases.value = listOf(case(createdAt = 0L, durationMode = DurationMode.NONE))
            repository.notifications.value = listOf(notification(kind = NotificationKind.QUIET, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(2), endedAt = millisAtDay(20)))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.firedNotifications.size)
        }

    @Test
    fun `evaluateCase does nothing for an unknown case`() =
        runTest {
            evaluator.evaluateCase(404L)

            assertTrue(notifier.firedNotifications.isEmpty())
            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase skips an archived case entirely`() =
        runTest {
            repository.cases.value = listOf(case(archived = true, checkInsEnabled = true))
            repository.notifications.value = listOf(notification(threshold = 1, windowDays = 7))
            repository.events.value = listOf(event(occurredAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedNotifications.isEmpty())
            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase fires a due check-in without auto-rearming it`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value = listOf(case(createdAt = 0L, checkInsEnabled = true))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.dueCheckIns.size)
            assertTrue(notifier.cancelledCheckIns.isEmpty())
            // Re-arming is the "All quiet" action's job (or a new event), not automatic at fire
            // time — an ignored check-in must be able to fire again on the next periodic pass.
            assertNull(
                repository.cases.value
                    .single()
                    .lastCheckInAt,
            )
        }

    @Test
    fun `evaluateCase counts check-in silence from when a duration event ended`() =
        runTest {
            // Event ran days 1..28 and stopped; now is day 30, so only 2 quiet days — under the 7-day interval.
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value = listOf(case(createdAt = 0L, checkInsEnabled = true, durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(event(occurredAt = millisAtDay(1), endedAt = millisAtDay(28)))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase counts check-in silence from occurredAt for a Case that no longer tracks duration`() =
        runTest {
            // Event ran days 1..28, Case now NONE — silence counts from the day-1 start = 29 quiet
            // days, past the 7-day interval, so the check-in is due. The day-28 endedAt would give 2.
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value = listOf(case(createdAt = 0L, checkInsEnabled = true, durationMode = DurationMode.NONE))
            repository.events.value = listOf(event(occurredAt = millisAtDay(1), endedAt = millisAtDay(28)))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.dueCheckIns.size)
        }

    @Test
    fun `evaluateCase does not fire a check-in before its interval elapses`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value = listOf(case(createdAt = 0L, checkInsEnabled = true, lastCheckInAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase skips check-in evaluation when checkInsEnabled is false`() =
        runTest {
            repository.cases.value = listOf(case(createdAt = 0L, checkInsEnabled = false))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.dueCheckIns.isEmpty())
            // Not due (opted out) ⇒ any stale check-in notification for this Case is withdrawn.
            assertEquals(listOf(1L), notifier.cancelledCheckIns)
            assertNull(
                repository.cases.value
                    .single()
                    .lastCheckInAt,
            )
        }

    @Test
    fun `evaluateCase withdraws the check-in notification once the Case is no longer due`() =
        runTest {
            // checkInsEnabled but re-armed to now, so nothing is due — a previously-posted check-in
            // notification must be cancelled rather than left in the shade.
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(case(createdAt = 0L, checkInsEnabled = true, lastCheckInAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.dueCheckIns.isEmpty())
            assertEquals(listOf(1L), notifier.cancelledCheckIns)
        }

    @Test
    fun `evaluateAll evaluates every enabled notification and every active case's check-in`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(
                    case(id = 1L, createdAt = 0L, checkInsEnabled = true),
                    case(id = 2L, createdAt = 0L, checkInsEnabled = false),
                )
            repository.notifications.value = listOf(notification(id = 1L, caseId = 2L, threshold = 1, windowDays = 7))
            repository.events.value = listOf(event(id = 1L, caseId = 2L, occurredAt = clock.nowMillis()))

            evaluator.evaluateAll()

            assertEquals(1, notifier.firedNotifications.size)
            assertEquals(1, notifier.dueCheckIns.size)
            assertEquals(
                1L,
                notifier.dueCheckIns
                    .single()
                    .first.id,
            )
        }

    @Test
    fun `evaluateAll posts an actionable check-in for every due Case`() =
        runTest {
            // Aggregation is the Android notification group's job now — the evaluator just posts one
            // per due Case, each keeping its Log / All quiet actions.
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(
                    case(id = 1L, createdAt = 0L, checkInsEnabled = true),
                    case(id = 2L, createdAt = 0L, checkInsEnabled = true),
                )

            evaluator.evaluateAll()

            assertEquals(
                setOf(1L, 2L),
                notifier.dueCheckIns.map { it.first.id }.toSet(),
            )
            assertTrue(notifier.cancelledCheckIns.isEmpty())
        }

    @Test
    fun `evaluateAll withdraws the check-in for an active Case that is not due`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(
                    case(id = 1L, createdAt = 0L, checkInsEnabled = true),
                    case(id = 2L, createdAt = 0L, checkInsEnabled = false),
                )

            evaluator.evaluateAll()

            assertEquals(listOf(1L), notifier.dueCheckIns.map { it.first.id })
            assertEquals(listOf(2L), notifier.cancelledCheckIns)
        }
}
