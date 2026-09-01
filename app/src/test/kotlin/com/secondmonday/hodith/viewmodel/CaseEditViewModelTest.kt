package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import com.secondmonday.hodith.testsupport.Fixtures
import com.secondmonday.hodith.testsupport.finishedEvent
import com.secondmonday.hodith.testsupport.runningEvent
import com.secondmonday.hodith.widget.FakeWidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaseEditViewModelTest {
    private val repository = FakeHodithRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val clock = FakeClock(1_000_000L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newCaseViewModel() =
        CaseEditViewModel(
            repository,
            settingsRepository,
            clock,
            FakeWidgetRefresher(),
            NotificationPermissionRequestSignal(),
            SavedStateHandle(),
        )

    private fun editViewModel(caseId: Long) =
        CaseEditViewModel(
            repository,
            settingsRepository,
            clock,
            FakeWidgetRefresher(),
            NotificationPermissionRequestSignal(),
            SavedStateHandle(mapOf("caseId" to caseId)),
        )

    private fun existingCase(
        id: Long = 1L,
        durationMode: DurationMode = DurationMode.NONE,
    ) = Fixtures.case(
        id = id,
        name = "Coffee",
        description = "Track cups",
        icon = "☕️",
        logFlow = LogFlow.DETAIL_SHEET,
        durationMode = durationMode,
    )

    @Test
    fun `a new case starts with a blank, non-editing state`() =
        runTest {
            val vm = newCaseViewModel()

            val state = vm.uiState.value
            assertFalse(state.isEditing)
            assertFalse(state.isLoading)
            assertEquals("", state.name)
        }

    @Test
    fun `editing an existing case loads its fields into state`() =
        runTest {
            repository.cases.value = listOf(existingCase())

            val state = editViewModel(caseId = 1L).uiState.value

            assertTrue(state.isEditing)
            assertFalse(state.isLoading)
            assertEquals("Coffee", state.name)
            assertEquals("Track cups", state.description)
            assertTrue(state.canArchive)
        }

    @Test
    fun `onNameChange updates name and clears a previously shown error`() =
        runTest {
            val vm = newCaseViewModel()
            vm.save() // blank name -> shows the error

            vm.onNameChange("Coffee")

            assertEquals("Coffee", vm.uiState.value.name)
            assertFalse(vm.uiState.value.showNameError)
        }

    @Test
    fun `save with a blank name shows validation errors and does not persist`() =
        runTest {
            val vm = newCaseViewModel()

            vm.save()

            assertTrue(vm.uiState.value.showNameError)
            assertTrue(vm.uiState.value.showIconError)
            assertFalse(vm.uiState.value.isSaved)
            assertTrue(repository.cases.value.isEmpty())
        }

    @Test
    fun `save with a valid new case inserts it`() =
        runTest {
            val vm = newCaseViewModel()
            vm.onNameChange("Coffee")
            vm.onIconSelect("☕️")

            vm.save()

            assertTrue(vm.uiState.value.isSaved)
            assertEquals(1, repository.cases.value.size)
            assertEquals(
                "Coffee",
                repository.cases.value
                    .single()
                    .name,
            )
            assertEquals(
                "☕️",
                repository.cases.value
                    .single()
                    .icon,
            )
        }

    @Test
    fun `save with valid edits to an existing case updates it in place`() =
        runTest {
            repository.cases.value = listOf(existingCase())
            val vm = editViewModel(caseId = 1L)

            vm.onNameChange("Espresso")
            vm.save()

            assertTrue(vm.uiState.value.isSaved)
            assertEquals(1, repository.cases.value.size)
            assertEquals(
                "Espresso",
                repository.cases.value
                    .single()
                    .name,
            )
            assertEquals(
                1L,
                repository.cases.value
                    .single()
                    .id,
            )
        }

    @Test
    fun `onCheckInToggle updates state and is persisted on save`() =
        runTest {
            repository.cases.value = listOf(existingCase())
            val vm = editViewModel(caseId = 1L)

            vm.onCheckInToggle(false)
            vm.save()

            assertFalse(vm.uiState.value.checkInsEnabled)
            assertFalse(
                repository.cases.value
                    .single()
                    .checkInsEnabled,
            )
        }

    @Test
    fun `save with a name matching another active case shows a duplicate error and does not persist`() =
        runTest {
            repository.cases.value = listOf(existingCase(id = 1L))
            val vm = newCaseViewModel()
            vm.onNameChange("Coffee")
            vm.onIconSelect("🍵")

            vm.save()

            assertTrue(vm.uiState.value.showDuplicateNameError)
            assertFalse(vm.uiState.value.isSaved)
            assertEquals(1, repository.cases.value.size)
        }

    @Test
    fun `save keeps an existing case's own unchanged name`() =
        runTest {
            repository.cases.value = listOf(existingCase(id = 1L))
            val vm = editViewModel(caseId = 1L)

            vm.save()

            assertTrue(vm.uiState.value.isSaved)
            assertFalse(vm.uiState.value.showDuplicateNameError)
        }

    @Test
    fun `onNameChange truncates to the max case name length`() =
        runTest {
            val vm = newCaseViewModel()

            vm.onNameChange("a".repeat(CASE_NAME_MAX_LENGTH + 10))

            assertEquals(CASE_NAME_MAX_LENGTH, vm.uiState.value.name.length)
        }

    @Test
    fun `onDescriptionChange truncates to the max case description length`() =
        runTest {
            val vm = newCaseViewModel()

            vm.onDescriptionChange("a".repeat(CASE_DESCRIPTION_MAX_LENGTH + 10))

            assertEquals(CASE_DESCRIPTION_MAX_LENGTH, vm.uiState.value.description.length)
        }

    @Test
    fun `leaving START_STOP while events run holds the change behind a confirm`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(runningEvent(id = 10L), runningEvent(id = 11L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.NONE)

            assertTrue(vm.uiState.value.showLeaveStartStopConfirm)
            assertEquals(2, vm.uiState.value.runningEventCount)
            assertEquals(DurationMode.START_STOP, vm.uiState.value.durationMode)
        }

    @Test
    fun `confirming the leave-START_STOP dialog applies the mode and save stops the running events`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(runningEvent(id = 10L), runningEvent(id = 11L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.NONE)

            vm.confirmLeaveStartStop()
            vm.save()

            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            assertEquals(
                DurationMode.NONE,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertTrue(repository.events.value.all { it.endedAt == clock.nowMillis() })
        }

    @Test
    fun `dismissing the leave-START_STOP dialog keeps the case in START_STOP`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(runningEvent(id = 10L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.MANUAL)

            vm.dismissLeaveStartStop()

            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            assertEquals(DurationMode.START_STOP, vm.uiState.value.durationMode)
        }

    @Test
    fun `leaving START_STOP with nothing running switches with no dialog`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.START_STOP))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.MANUAL)

            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            assertEquals(DurationMode.MANUAL, vm.uiState.value.durationMode)
        }

    @Test
    fun `changing mode on a non-START_STOP case never triggers the guard`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(runningEvent(id = 10L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.NONE)

            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            assertEquals(DurationMode.NONE, vm.uiState.value.durationMode)
        }

    @Test
    fun `MANUAL to NONE switches with no dialog and keeps every stored endedAt (spec section 6)`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(runningEvent(id = 10L), finishedEvent(id = 11L, endedAt = 888L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.NONE)
            vm.save()

            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            assertFalse(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(
                DurationMode.NONE,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertNull(
                repository.events.value
                    .single { it.id == 10L }
                    .endedAt,
            )
            assertEquals(
                888L,
                repository.events.value
                    .single { it.id == 11L }
                    .endedAt,
            )
        }

    @Test
    fun `NONE to MANUAL switches with no dialog and touches no events (spec section 6)`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            repository.events.value = listOf(runningEvent(id = 10L), finishedEvent(id = 11L, endedAt = 888L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.MANUAL)
            vm.save()

            assertFalse(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(
                DurationMode.MANUAL,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertNull(
                repository.events.value
                    .single { it.id == 10L }
                    .endedAt,
            )
            assertEquals(
                888L,
                repository.events.value
                    .single { it.id == 11L }
                    .endedAt,
            )
        }

    @Test
    fun `confirming leave-START_STOP toward MANUAL stops running events at now and leaves finished ones`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(runningEvent(id = 10L), finishedEvent(id = 11L, endedAt = 888L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.MANUAL)

            vm.confirmLeaveStartStop()
            vm.save()

            assertEquals(
                DurationMode.MANUAL,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertEquals(
                clock.nowMillis(),
                repository.events.value
                    .single { it.id == 10L }
                    .endedAt,
            )
            assertEquals(
                888L,
                repository.events.value
                    .single { it.id == 11L }
                    .endedAt,
            )
        }

    @Test
    fun `entering START_STOP with open-ended events holds the change behind a confirm`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            val seeded =
                listOf(runningEvent(id = 10L), runningEvent(id = 11L), finishedEvent(id = 12L, endedAt = 777L))
            repository.events.value = seeded
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.START_STOP)

            assertTrue(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(2, vm.uiState.value.runningEventCount)
            assertEquals(DurationMode.NONE, vm.uiState.value.durationMode)
            // Held, not applied: every event's endedAt is exactly as seeded until save() runs.
            assertEquals(
                seeded.associate { it.id to it.endedAt },
                repository.events.value.associate { it.id to it.endedAt },
            )
        }

    @Test
    fun `confirming the enter-START_STOP dialog converts open-ended events to instant on save`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            repository.events.value =
                listOf(runningEvent(id = 10L, occurredAt = 111L), runningEvent(id = 11L, occurredAt = 222L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.START_STOP)

            vm.confirmEnterStartStop()
            vm.save()

            assertFalse(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(
                DurationMode.START_STOP,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertTrue(repository.events.value.all { it.endedAt == it.occurredAt })
            assertTrue(repository.events.value.none { it.endedAt == clock.nowMillis() })
        }

    @Test
    fun `dismissing the enter-START_STOP dialog keeps the current mode and touches no events`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            val seeded = listOf(runningEvent(id = 10L, occurredAt = 111L), finishedEvent(id = 12L, endedAt = 777L))
            repository.events.value = seeded
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.START_STOP)

            vm.dismissEnterStartStop()
            vm.save()

            assertFalse(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(DurationMode.NONE, vm.uiState.value.durationMode)
            assertEquals(
                seeded.associate { it.id to it.endedAt },
                repository.events.value.associate { it.id to it.endedAt },
            )
        }

    @Test
    fun `entering START_STOP from MANUAL also converts duration-less events`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(runningEvent(id = 10L, occurredAt = 333L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.START_STOP)

            vm.confirmEnterStartStop()
            vm.save()

            assertEquals(
                333L,
                repository.events.value
                    .single()
                    .endedAt,
            )
        }

    @Test
    fun `entering START_STOP leaves already-finished events untouched`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.MANUAL))
            repository.events.value =
                listOf(runningEvent(id = 10L, occurredAt = 333L), finishedEvent(id = 11L, endedAt = 500_000L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.START_STOP)

            vm.confirmEnterStartStop()
            vm.save()

            assertEquals(
                333L,
                repository.events.value
                    .single { it.id == 10L }
                    .endedAt,
            )
            assertEquals(
                500_000L,
                repository.events.value
                    .single { it.id == 11L }
                    .endedAt,
            )
        }

    @Test
    fun `entering START_STOP with no open-ended events switches with no dialog`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(finishedEvent(id = 11L, endedAt = 500_000L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.START_STOP)
            vm.save()

            assertFalse(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(DurationMode.START_STOP, vm.uiState.value.durationMode)
            assertEquals(
                500_000L,
                repository.events.value
                    .single()
                    .endedAt,
            )
        }

    @Test
    fun `entering START_STOP with no events at all switches with no dialog`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.START_STOP)

            assertFalse(vm.uiState.value.showEnterStartStopConfirm)
            assertEquals(DurationMode.START_STOP, vm.uiState.value.durationMode)
        }

    @Test
    fun `round-trip NONE to START_STOP to NONE leaves a point event a point`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            repository.events.value = listOf(runningEvent(id = 10L, occurredAt = 444L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.START_STOP)
            vm.confirmEnterStartStop()
            vm.save()
            assertEquals(
                444L,
                repository.events.value
                    .single()
                    .endedAt,
            )

            vm.onDurationModeChange(DurationMode.NONE)
            vm.save()

            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            assertEquals(
                DurationMode.NONE,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertEquals(
                444L,
                repository.events.value
                    .single()
                    .endedAt,
            )
        }

    @Test
    fun `toggling NONE to START_STOP and back before any save leaves an open-ended point untouched`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            repository.events.value = listOf(runningEvent(id = 10L, occurredAt = 444L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.START_STOP)
            vm.confirmEnterStartStop()
            vm.onDurationModeChange(DurationMode.NONE)

            // Returning to the persisted mode is a no-op — no leave dialog, no held stamp survives.
            assertFalse(vm.uiState.value.showLeaveStartStopConfirm)
            vm.save()

            assertEquals(
                DurationMode.NONE,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertNull(
                repository.events.value
                    .single()
                    .endedAt,
            )
        }

    @Test
    fun `toggling MANUAL to START_STOP and back before any save leaves an open-ended event open`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(runningEvent(id = 10L, occurredAt = 444L))
            val vm = editViewModel(caseId = 1L)

            vm.onDurationModeChange(DurationMode.START_STOP)
            vm.confirmEnterStartStop()
            vm.onDurationModeChange(DurationMode.MANUAL)
            vm.save()

            assertEquals(
                DurationMode.MANUAL,
                repository.cases.value
                    .single()
                    .durationMode,
            )
            assertNull(
                repository.events.value
                    .single()
                    .endedAt,
            )
        }

    @Test
    fun `dismissing then re-entering START_STOP prompts again`() =
        runTest {
            repository.cases.value = listOf(existingCase(durationMode = DurationMode.NONE))
            repository.events.value = listOf(runningEvent(id = 10L))
            val vm = editViewModel(caseId = 1L)
            vm.onDurationModeChange(DurationMode.START_STOP)
            vm.dismissEnterStartStop()

            vm.onDurationModeChange(DurationMode.START_STOP)

            assertTrue(vm.uiState.value.showEnterStartStopConfirm)
        }

    @Test
    fun `archive marks the case archived`() =
        runTest {
            repository.cases.value = listOf(existingCase())
            val vm = editViewModel(caseId = 1L)

            vm.archive()

            assertTrue(vm.uiState.value.isArchived)
            assertTrue(
                repository.cases.value
                    .single()
                    .archived,
            )
        }
}
