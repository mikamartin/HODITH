package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.LogFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListWidgetConfigureViewModelTest {
    private val repository = FakeHodithRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testCase(
        id: Long,
        name: String = "Coffee",
        pinned: Boolean = false,
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        pinned = pinned,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = false,
    )

    @Test
    fun `shows the picker with every active case when nothing is pinned yet`() =
        runTest {
            repository.cases.value =
                listOf(testCase(id = 1L), testCase(id = 2L, name = "Tea"), testCase(id = 3L, name = "Gone").copy(archived = true))
            val viewModel = ListWidgetConfigureViewModel(repository)

            val state = viewModel.uiState.value as ListWidgetConfigureUiState.Picker
            assertEquals(listOf("Coffee", "Tea"), state.cases.map { it.name })
            assertTrue(state.selectedCaseIds.isEmpty())
        }

    @Test
    fun `skips straight to AlreadyConfigured when a case is already pinned`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L, pinned = true), testCase(id = 2L, name = "Tea"))
            val viewModel = ListWidgetConfigureViewModel(repository)

            assertEquals(ListWidgetConfigureUiState.AlreadyConfigured, viewModel.uiState.value)
        }

    @Test
    fun `toggle adds and then removes a case from the selection`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L))
            val viewModel = ListWidgetConfigureViewModel(repository)

            viewModel.toggle(1L)
            var state = viewModel.uiState.value as ListWidgetConfigureUiState.Picker
            assertEquals(setOf(1L), state.selectedCaseIds)

            viewModel.toggle(1L)
            state = viewModel.uiState.value as ListWidgetConfigureUiState.Picker
            assertTrue(state.selectedCaseIds.isEmpty())
        }

    @Test
    fun `toggle is a no-op once already configured`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L, pinned = true))
            val viewModel = ListWidgetConfigureViewModel(repository)

            viewModel.toggle(1L)

            assertEquals(ListWidgetConfigureUiState.AlreadyConfigured, viewModel.uiState.value)
        }

    @Test
    fun `confirmSelection pins every selected case and invokes onDone`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L), testCase(id = 2L, name = "Tea"))
            val viewModel = ListWidgetConfigureViewModel(repository)
            viewModel.toggle(2L)
            var done = false

            viewModel.confirmSelection { done = true }

            assertTrue(done)
            assertTrue(
                repository.cases.value
                    .single { it.id == 2L }
                    .pinned,
            )
            assertTrue(
                !repository.cases.value
                    .single { it.id == 1L }
                    .pinned,
            )
        }

    @Test
    fun `confirmSelection invokes onDone immediately when already configured`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L, pinned = true))
            val viewModel = ListWidgetConfigureViewModel(repository)
            var done = false

            viewModel.confirmSelection { done = true }

            assertTrue(done)
        }
}
