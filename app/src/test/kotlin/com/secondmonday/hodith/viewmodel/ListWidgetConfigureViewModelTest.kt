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
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
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
            val viewModel = ListWidgetConfigureViewModel(repository)

            val state = viewModel.uiState.value as ListWidgetConfigureUiState.Picker
            assertEquals(listOf("Coffee", "Tea"), state.cases.map { it.name })
            assertTrue(state.selectedCaseIds.isEmpty())
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
    fun `confirmSelection invokes onDone with every selected case id`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L), testCase(id = 2L, name = "Tea"))
            val viewModel = ListWidgetConfigureViewModel(repository)
            viewModel.toggle(1L)
            viewModel.toggle(2L)
            var confirmedCaseIds: Set<Long>? = null

            viewModel.confirmSelection { confirmedCaseIds = it }

            assertEquals(setOf(1L, 2L), confirmedCaseIds)
        }

    @Test
    fun `confirmSelection invokes onDone with an empty set when nothing is selected`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L))
            val viewModel = ListWidgetConfigureViewModel(repository)
            var confirmedCaseIds: Set<Long>? = null

            viewModel.confirmSelection { confirmedCaseIds = it }

            assertEquals(emptySet<Long>(), confirmedCaseIds)
        }
}
