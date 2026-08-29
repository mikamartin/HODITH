package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.domain.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Provider

private val ZONE = ZoneId.systemDefault()

/** Calendar-day timestamp, same helper as [com.secondmonday.hodith.domain.TriggerEngineTest] — avoids DST/zone-offset flakiness near epoch that raw millis multiplication risks. */
private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

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
        hunchNudgeDismissed = false,
        checkInsEnabled = checkInsEnabled,
        lastCheckInAt = lastCheckInAt,
        sortOrder = 0,
        archived = archived,
    )

    private fun trigger(
        id: Long = 1L,
        caseId: Long = 1L,
        kind: TriggerKind = TriggerKind.AT_LEAST,
        threshold: Int = 3,
        windowDays: Int? = 7,
        enabled: Boolean = true,
        armed: Boolean = true,
        lastFiredAt: Long? = null,
    ) = TriggerEntity(
        id = id,
        caseId = caseId,
        kind = kind,
        threshold = threshold,
        windowDays = windowDays,
        enabled = enabled,
        armed = armed,
        lastFiredAt = lastFiredAt,
    )

    private fun event(
        id: Long = 0L,
        caseId: Long = 1L,
        occurredAt: Long,
        endedAt: Long? = null,
    ) = EventEntity(
        id = id,
        caseId = caseId,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = null,
        note = null,
        loggedAt = occurredAt,
    )

    @Test
    fun `evaluateCase fires an AT_LEAST trigger once its window count reaches threshold`() =
        runTest {
            repository.cases.value = listOf(case())
            repository.triggers.value = listOf(trigger(threshold = 3, windowDays = 7))
            repository.events.value = (1..3).map { event(id = it.toLong(), occurredAt = clock.nowMillis()) }

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.firedTriggers.size)
            val updated = repository.triggers.value.single()
            assertTrue(!updated.armed)
            assertEquals(clock.nowMillis(), updated.lastFiredAt)
        }

    @Test
    fun `evaluateCase does not fire an AT_LEAST trigger below threshold`() =
        runTest {
            repository.cases.value = listOf(case())
            repository.triggers.value = listOf(trigger(threshold = 3, windowDays = 7))
            repository.events.value = listOf(event(occurredAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedTriggers.isEmpty())
        }

    @Test
    fun `evaluateCase ignores a disabled trigger`() =
        runTest {
            repository.cases.value = listOf(case())
            repository.triggers.value = listOf(trigger(threshold = 1, windowDays = 7, enabled = false))
            repository.events.value = listOf(event(occurredAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedTriggers.isEmpty())
        }

    @Test
    fun `evaluateCase fires a SILENT_FOR trigger based on the most recent event`() =
        runTest {
            repository.cases.value = listOf(case(createdAt = 0L))
            repository.triggers.value = listOf(trigger(kind = TriggerKind.SILENT_FOR, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(16)))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.firedTriggers.size)
        }

    @Test
    fun `evaluateCase counts SILENT_FOR silence from when a duration event ended, not when it started`() =
        runTest {
            // Event ran days 2..20 and stopped; now is day 30, so 10 quiet days — under the 14-day threshold.
            // Measured from the day-2 start it would be 28 days and would fire.
            repository.cases.value = listOf(case(createdAt = 0L, durationMode = DurationMode.MANUAL))
            repository.triggers.value = listOf(trigger(kind = TriggerKind.SILENT_FOR, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(2), endedAt = millisAtDay(20)))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedTriggers.isEmpty())
        }

    @Test
    fun `evaluateCase does not fire SILENT_FOR while an event is still running on the Case`() =
        runTest {
            // Started day 2, never stopped; now is day 30. A running event is not silence.
            repository.cases.value = listOf(case(createdAt = 0L, durationMode = DurationMode.START_STOP))
            repository.triggers.value = listOf(trigger(kind = TriggerKind.SILENT_FOR, threshold = 14, windowDays = null))
            repository.events.value = listOf(event(occurredAt = millisAtDay(2), endedAt = null))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedTriggers.isEmpty())
        }

    @Test
    fun `evaluateCase does nothing for an unknown case`() =
        runTest {
            evaluator.evaluateCase(404L)

            assertTrue(notifier.firedTriggers.isEmpty())
            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase skips an archived case entirely`() =
        runTest {
            repository.cases.value = listOf(case(archived = true, checkInsEnabled = true))
            repository.triggers.value = listOf(trigger(threshold = 1, windowDays = 7))
            repository.events.value = listOf(event(occurredAt = clock.nowMillis()))

            evaluator.evaluateCase(1L)

            assertTrue(notifier.firedTriggers.isEmpty())
            assertTrue(notifier.dueCheckIns.isEmpty())
        }

    @Test
    fun `evaluateCase fires a due check-in without auto-rearming it`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value = listOf(case(createdAt = 0L, checkInsEnabled = true))

            evaluator.evaluateCase(1L)

            assertEquals(1, notifier.dueCheckIns.size)
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
            assertNull(
                repository.cases.value
                    .single()
                    .lastCheckInAt,
            )
        }

    @Test
    fun `evaluateAll evaluates every enabled trigger and every active case's check-in`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(
                    case(id = 1L, createdAt = 0L, checkInsEnabled = true),
                    case(id = 2L, createdAt = 0L, checkInsEnabled = false),
                )
            repository.triggers.value = listOf(trigger(id = 1L, caseId = 2L, threshold = 1, windowDays = 7))
            repository.events.value = listOf(event(id = 1L, caseId = 2L, occurredAt = clock.nowMillis()))

            evaluator.evaluateAll()

            assertEquals(1, notifier.firedTriggers.size)
            assertEquals(1, notifier.dueCheckIns.size)
            assertEquals(
                1L,
                notifier.dueCheckIns
                    .single()
                    .first.id,
            )
        }

    @Test
    fun `evaluateAll collapses 2 or more due check-ins into a single summary notification`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(
                    case(id = 1L, createdAt = 0L, checkInsEnabled = true),
                    case(id = 2L, createdAt = 0L, checkInsEnabled = true),
                )

            evaluator.evaluateAll()

            assertTrue(notifier.dueCheckIns.isEmpty())
            assertEquals(1, notifier.checkInSummaries.size)
            assertEquals(
                setOf(1L, 2L),
                notifier.checkInSummaries
                    .single()
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun `evaluateAll posts an individual notification when exactly one check-in is due`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.SEVEN
            repository.cases.value =
                listOf(
                    case(id = 1L, createdAt = 0L, checkInsEnabled = true),
                    case(id = 2L, createdAt = 0L, checkInsEnabled = false),
                )

            evaluator.evaluateAll()

            assertEquals(1, notifier.dueCheckIns.size)
            assertTrue(notifier.checkInSummaries.isEmpty())
        }
}
