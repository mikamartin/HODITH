package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.LogFlow
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
class ArchivedCasesViewModelTest {
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
        sortOrder: Int = 0,
        archived: Boolean = true,
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.DETAIL_SHEET,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = sortOrder,
        archived = archived,
    )

    private fun testEvent(caseId: Long) =
        EventEntity(caseId = caseId, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L)

    @Test
    fun `uiState reflects archived cases with their event counts`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L), testCase(id = 2L, name = "Tea", archived = false))
            repository.events.value = listOf(testEvent(caseId = 1L), testEvent(caseId = 1L))
            val viewModel = ArchivedCasesViewModel(repository, FakeWidgetRefresher())

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(1, state.cases.size)
                assertEquals("Coffee", state.cases.single().name)
                assertEquals(2, state.cases.single().eventCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unarchive moves the case back to active with a fresh sortOrder`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L, archived = false), testCase(id = 2L))
            val viewModel = ArchivedCasesViewModel(repository, FakeWidgetRefresher())

            viewModel.unarchive(caseId = 2L)

            val unarchived = repository.cases.value.single { it.id == 2L }
            assertFalse(unarchived.archived)
            assertEquals(1, unarchived.sortOrder)
        }

    @Test
    fun `deleteForever removes the case and its events`() =
        runTest {
            repository.cases.value = listOf(testCase(id = 1L))
            repository.events.value = listOf(testEvent(caseId = 1L))
            val viewModel = ArchivedCasesViewModel(repository, FakeWidgetRefresher())

            viewModel.deleteForever(caseId = 1L)

            assertTrue(repository.cases.value.isEmpty())
            assertTrue(repository.events.value.isEmpty())
        }
}
