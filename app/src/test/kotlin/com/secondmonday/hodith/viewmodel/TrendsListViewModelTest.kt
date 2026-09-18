package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.testsupport.eventAtDay
import com.secondmonday.hodith.testsupport.millisAtDay
import com.secondmonday.hodith.testsupport.testCase
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
class TrendsListViewModelTest {
    private val repository = FakeHodithRepository()
    private val caseId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clock: FakeClock) = TrendsListViewModel(repository, clock, SavedStateHandle(mapOf("caseId" to caseId)))

    @Test
    fun `uiState carries the case icon and name`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId, name = "Coffee", icon = "☕", createdAt = millisAtDay(0)))
            repository.events.value = listOf(eventAtDay(0), eventAtDay(4))

            viewModel(FakeClock(millisAtDay(10))).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals("☕", state.caseIcon)
                assertEquals("Coffee", state.caseName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState findings mirror stats trends from insightsTabState`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId, createdAt = millisAtDay(0)))
            // Same widening shape as InsightsTabStateTest's gap-shift fixture: past gaps 4, 4, 4, 20, 20, 20.
            repository.events.value = listOf(0L, 4L, 8L, 12L, 32L, 52L, 72L).map { eventAtDay(it) }

            viewModel(FakeClock(millisAtDay(90))).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                val finding = state.findings.single { it.kind == TrendFindingKind.GAP_SHIFT }
                assertEquals(ShiftDirection.UP, finding.direction)
                assertEquals(4.0, finding.priorValue, 0.0001)
                assertEquals(20.0, finding.recentValue, 0.0001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState is empty and not loading when the case does not exist`() =
        runTest {
            viewModel(FakeClock(millisAtDay(0))).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals("", state.caseIcon)
                assertEquals("", state.caseName)
                assertTrue(state.findings.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
