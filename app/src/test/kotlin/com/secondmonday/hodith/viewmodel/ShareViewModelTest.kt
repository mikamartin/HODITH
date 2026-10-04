package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.data.share.FakeShareImageExporter
import com.secondmonday.hodith.domain.FakeClock
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
class ShareViewModelTest {
    private val repository = FakeHodithRepository()
    private val clock = FakeClock(1_000_000L)
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

    private val settingsRepository = FakeSettingsRepository()

    private fun viewModel() =
        ShareViewModel(repository, clock, shareImageExporter, settingsRepository, SavedStateHandle(mapOf("caseId" to caseId)))

    private fun testCase() =
        CaseEntity(
            id = caseId,
            name = "Coffee",
            icon = "☕️",
            createdAt = 0L,
            logFlow = LogFlow.ONE_TAP,
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            checkInsEnabled = true,
            lastCheckInAt = null,
            sortOrder = 0,
            archived = false,
        )

    @Test
    fun `uiState reflects the case and its events, defaulting the selection`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repository.insertEvent(
                EventEntity(caseId = caseId, occurredAt = 0L, endedAt = null, intensity = null, note = null, loggedAt = 0L),
            )

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals("Coffee", state.case?.name)
                assertEquals(1, state.events.size)
                assertEquals(ShareCardFormat.SQUARE, state.selection.format)
                assertNull(state.selection.displayNameOverride)
                assertEquals(ShareInsightsSection.entries.toSet(), state.selection.selectedSections)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setFormat updates the selection`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                vm.setFormat(ShareCardFormat.STORY)
                assertEquals(ShareCardFormat.STORY, awaitItem().selection.format)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDisplayNameOverride blanks out to null so the Case's own name is used`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.setDisplayNameOverride("Custom title")
                assertEquals("Custom title", awaitItem().selection.displayNameOverride)

                vm.setDisplayNameOverride("   ")
                assertNull(awaitItem().selection.displayNameOverride)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDisplayNameOverride truncates a name beyond the max length`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.setDisplayNameOverride("a".repeat(CASE_NAME_MAX_LENGTH + 10))
                assertEquals(CASE_NAME_MAX_LENGTH, awaitItem().selection.displayNameOverride?.length)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `switching format back and forth keeps the Story section choices`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.setSectionSelected(ShareInsightsSection.RHYTHM, selected = false)
                awaitItem()
                vm.setFormat(ShareCardFormat.STORY)
                assertEquals(ShareCardFormat.STORY, awaitItem().selection.format)
                vm.setFormat(ShareCardFormat.SQUARE)
                val backOnSquare = awaitItem().selection

                assertEquals(ShareCardFormat.SQUARE, backOnSquare.format)
                assertFalse(ShareInsightsSection.RHYTHM in backOnSquare.selectedSections)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setSectionSelected toggles a single section without disturbing the rest`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.setSectionSelected(ShareInsightsSection.DURATION, selected = false)
                val afterRemoval = awaitItem().selection.selectedSections
                assertFalse(ShareInsightsSection.DURATION in afterRemoval)
                assertTrue(ShareInsightsSection.RHYTHM in afterRemoval)

                vm.setSectionSelected(ShareInsightsSection.DURATION, selected = true)
                assertTrue(ShareInsightsSection.DURATION in awaitItem().selection.selectedSections)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `moveSection saves the new order device-wide and the uiState follows it`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.moveSection(available = ShareInsightsSection.entries, from = 0, to = 2)

                val expected =
                    listOf(ShareInsightsSection.STREAKS, ShareInsightsSection.DURATION, ShareInsightsSection.GAPS) +
                        ShareInsightsSection.entries.drop(3)
                assertEquals(expected, settingsRepository.shareSectionOrder.value)
                assertEquals(expected, awaitItem().sectionOrder)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a saved order from an earlier session is what a new session opens with`() =
        runTest {
            repository.cases.value = listOf(testCase())
            settingsRepository.shareSectionOrder.value = listOf(ShareInsightsSection.TAGS, ShareInsightsSection.GAPS)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }

                assertEquals(ShareInsightsSection.TAGS, state.sectionOrder.first())
                assertEquals(ShareInsightsSection.GAPS, state.sectionOrder[1])
                assertEquals(ShareInsightsSection.entries.size, state.sectionOrder.size)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
