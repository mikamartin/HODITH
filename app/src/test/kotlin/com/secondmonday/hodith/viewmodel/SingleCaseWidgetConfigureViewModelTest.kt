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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleCaseWidgetConfigureViewModelTest {
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
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        pinned = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = false,
    )

    @Test
    fun `shows the picker with every active case and nothing selected`() =
        runTest {
            repository.cases.value =
                listOf(testCase(id = 1L), testCase(id = 2L, name = "Tea"), testCase(id = 3L, name = "Gone").copy(archived = true))
            val viewModel = SingleCaseWidgetConfigureViewModel(repository)

            val state = viewModel.uiState.value as SingleCaseWidgetConfigureUiState.Picker
            assertEquals(listOf("Coffee", "Tea"), state.cases.map { it.name })
            assertNull(state.selectedCaseId)
        }

    @Test
    fun `select replaces the current selection`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L), testCase(id = 2L, name = "Tea"))
            val viewModel = SingleCaseWidgetConfigureViewModel(repository)

            viewModel.select(1L)
            var state = viewModel.uiState.value as SingleCaseWidgetConfigureUiState.Picker
            assertEquals(1L, state.selectedCaseId)

            viewModel.select(2L)
            state = viewModel.uiState.value as SingleCaseWidgetConfigureUiState.Picker
            assertEquals(2L, state.selectedCaseId)
        }

    @Test
    fun `confirmSelection invokes onDone with the selected case id`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L))
            val viewModel = SingleCaseWidgetConfigureViewModel(repository)
            viewModel.select(1L)
            var confirmedCaseId: Long? = null

            viewModel.confirmSelection { confirmedCaseId = it }

            assertEquals(1L, confirmedCaseId)
        }

    @Test
    fun `confirmSelection is a no-op when nothing is selected`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L))
            val viewModel = SingleCaseWidgetConfigureViewModel(repository)
            var invoked = false

            viewModel.confirmSelection { invoked = true }

            assertEquals(false, invoked)
        }
}
