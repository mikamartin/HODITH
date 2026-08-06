package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
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

    private fun existingCase(id: Long = 1L) =
        CaseEntity(
            id = id,
            name = "Coffee",
            description = "Track cups",
            icon = "☕️",
            createdAt = 0L,
            logFlow = LogFlow.DETAIL_SHEET,
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            hunchNudgeDismissed = false,
            checkInsEnabled = true,
            lastCheckInAt = null,
            sortOrder = 0,
            archived = false,
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
