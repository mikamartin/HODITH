package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.share.FakeShareImageExporter
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LogShareViewModelTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 27)
    private val repository = FakeHodithRepository()
    private val clock = FakeClock(zone.startOfDayMillis(today) + 12 * 60 * 60 * 1000L) // noon today
    private val shareImageExporter = FakeShareImageExporter()
    private val caseId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LogShareViewModel(repository, clock, shareImageExporter, SavedStateHandle(mapOf("caseId" to caseId)))

    private fun testCase() =
        CaseEntity(
            id = caseId,
            name = "Migraine",
            icon = "🤕",
            createdAt = 0L,
            logFlow = LogFlow.ONE_TAP,
            durationMode = DurationMode.START_STOP,
            intensityEnabled = true,
            checkInsEnabled = true,
            lastCheckInAt = null,
            sortOrder = 0,
            archived = false,
        )

    @Test
    fun `uiState defaults to newest-first, full history, every field on`() =
        runTest {
            repository.cases.value = listOf(testCase())

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals("Migraine", state.case?.name)
                assertEquals(ShareCardFormat.STORY, state.selection.format)
                assertEquals(ChronologicalOrder.NEWEST_FIRST, state.selection.sortOrder)
                assertNull(state.selection.dateFrom)
                assertEquals(zone.endOfDayMillis(today), state.selection.dateTo)
                assertEquals(LogRowField.entries.toSet(), state.selection.fields)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSortOrder updates the selection`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                vm.setSortOrder(ChronologicalOrder.OLDEST_FIRST)
                assertEquals(ChronologicalOrder.OLDEST_FIRST, awaitItem().selection.sortOrder)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDateFrom stores the picked date's local start-of-day millis`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()
            val picked = LocalDate.of(2026, 7, 3)

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                vm.setDateFrom(picked)
                assertEquals(zone.startOfDayMillis(picked), awaitItem().selection.dateFrom)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDateFrom of null clears the bound back to since-the-beginning`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                vm.setDateFrom(LocalDate.of(2026, 7, 3))
                awaitItem()
                vm.setDateFrom(null)
                assertNull(awaitItem().selection.dateFrom)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDateTo stores the picked date's local end-of-day millis`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()
            val picked = LocalDate.of(2026, 8, 15)

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                vm.setDateTo(picked)
                assertEquals(zone.endOfDayMillis(picked), awaitItem().selection.dateTo)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setFieldSelected toggles a single field without disturbing the rest`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.setFieldSelected(LogRowField.NOTES, selected = false)
                val afterRemoval = awaitItem().selection.fields
                assertEquals(false, LogRowField.NOTES in afterRemoval)
                assertTrue(LogRowField.TAGS in afterRemoval)

                vm.setFieldSelected(LogRowField.NOTES, selected = true)
                assertTrue(LogRowField.NOTES in awaitItem().selection.fields)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
