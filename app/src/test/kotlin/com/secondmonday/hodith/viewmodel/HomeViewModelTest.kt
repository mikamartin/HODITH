package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.testsupport.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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

    private fun testCase(
        id: Long = 1L,
        name: String = "Coffee",
        logFlow: LogFlow = LogFlow.ONE_TAP,
        durationMode: DurationMode = DurationMode.NONE,
    ) = Fixtures.case(id = id, name = name, icon = "☕️", logFlow = logFlow, durationMode = durationMode)

    private fun testEvent(
        caseId: Long = 1L,
        occurredAt: Long = clock.nowMillis(),
        endedAt: Long? = clock.nowMillis(),
    ) = Fixtures.event(caseId = caseId, occurredAt = occurredAt, endedAt = endedAt)

    @Test
    fun `uiState reflects seeded active case and archived count`() =
        runTest {
            repository.cases.value = listOf(testCase(), testCase(id = 2L, name = "Archived").copy(archived = true))
            repository.events.value = listOf(testEvent())
            val viewModel = HomeViewModel(repository, settingsRepository, clock)

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(1, state.cases.size)
                assertEquals("Coffee", state.cases.single().name)
                assertEquals(1, state.cases.single().todayCount)
                assertEquals(1, state.archivedCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onQuickLogTap on a ONE_TAP case inserts an event and signals undo`() =
        runTest {
            repository.cases.value = listOf(testCase(logFlow = LogFlow.ONE_TAP))
            val viewModel = HomeViewModel(repository, settingsRepository, clock)

            viewModel.uiState.test {
                val row = awaitLoadedItem { it.isLoading }.cases.single()
                viewModel.quickLogUndo.test {
                    viewModel.onQuickLogTap(row)
                    val undo = awaitItem()
                    assertEquals("Coffee", undo.caseName)
                }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repository.events.value.size)
            assertEquals(
                clock.nowMillis(),
                repository.events.value
                    .single()
                    .occurredAt,
            )
        }

    @Test
    fun `onQuickLogTap on an ongoing START_STOP case starts a second concurrent event`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(testEvent(endedAt = null))
            val viewModel = HomeViewModel(repository, settingsRepository, clock)

            viewModel.uiState.test {
                val row = awaitLoadedItem { it.isLoading }.cases.single()
                assertNotNull(row.ongoingEvent)
                clock.advanceBy(60_000L)
                viewModel.onQuickLogTap(row)
                val afterStart = awaitItem()
                assertEquals(2, afterStart.cases.single().runningCount)
                cancelAndIgnoreRemainingEvents()
            }
            // Both events still open — the first was not stopped.
            assertTrue(repository.events.value.all { it.endedAt == null })
            assertEquals(2, repository.events.value.size)
        }

    @Test
    fun `onQuickLogTap on a DETAIL_SHEET case opens the log sheet with tag suggestions`() =
        runTest {
            repository.cases.value = listOf(testCase(logFlow = LogFlow.DETAIL_SHEET))
            val eventId = repository.insertEvent(testEvent())
            repository.tags.value = listOf(TagEntity(id = 1L, name = "focus"))
            repository.eventTags.value = listOf(EventTagCrossRef(eventId = eventId, tagId = 1L))
            val viewModel = HomeViewModel(repository, settingsRepository, clock)

            viewModel.uiState.test {
                val row = awaitLoadedItem { it.isLoading }.cases.single()
                viewModel.onQuickLogTap(row)
                cancelAndIgnoreRemainingEvents()
            }

            val sheet = viewModel.logSheet.value
            assertNotNull(sheet)
            assertEquals(1L, sheet!!.caseId)
            assertEquals(listOf("focus"), sheet.tagSuggestions.map { it.name })
        }

    @Test
    fun `dismissLogSheet clears the open sheet`() =
        runTest {
            repository.cases.value = listOf(testCase(logFlow = LogFlow.DETAIL_SHEET))
            val viewModel = HomeViewModel(repository, settingsRepository, clock)
            viewModel.uiState.test {
                val row = awaitLoadedItem { it.isLoading }.cases.single()
                viewModel.onQuickLogTap(row)
                cancelAndIgnoreRemainingEvents()
            }
            assertNotNull(viewModel.logSheet.value)

            viewModel.dismissLogSheet()

            assertNull(viewModel.logSheet.value)
        }

    @Test
    fun `saveLogSheetEvent inserts a new event with tags and clears the sheet`() =
        runTest {
            repository.cases.value = listOf(testCase(logFlow = LogFlow.DETAIL_SHEET, durationMode = DurationMode.NONE))
            val viewModel = HomeViewModel(repository, settingsRepository, clock)
            viewModel.uiState.test {
                val row = awaitLoadedItem { it.isLoading }.cases.single()
                viewModel.onQuickLogTap(row)
                cancelAndIgnoreRemainingEvents()
            }
            val draft =
                viewModel.logSheet.value!!
                    .draft
                    .copy(note = "logged via sheet", tags = listOf("focus"))

            viewModel.saveLogSheetEvent(draft)

            assertNull(viewModel.logSheet.value)
            assertEquals(1, repository.events.value.size)
            assertEquals(
                "logged via sheet",
                repository.events.value
                    .single()
                    .note,
            )
            assertEquals(listOf("focus"), repository.tags.value.map { it.name })
        }

    @Test
    fun `saveLogSheetEvent on a running START_STOP case starts a second concurrent event`() =
        runTest {
            repository.cases.value =
                listOf(testCase(logFlow = LogFlow.DETAIL_SHEET, durationMode = DurationMode.START_STOP))
            repository.events.value = listOf(testEvent(endedAt = null))
            val viewModel = HomeViewModel(repository, settingsRepository, clock)
            viewModel.uiState.test {
                val row = awaitLoadedItem { it.isLoading }.cases.single()
                viewModel.onQuickLogTap(row)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.saveLogSheetEvent(viewModel.logSheet.value!!.draft)

            // The first event was not stopped — the sheet save opens a second concurrent one (spec §6).
            assertEquals(2, repository.events.value.size)
            assertTrue(repository.events.value.all { it.endedAt == null })
        }

    @Test
    fun `undoQuickLog deletes the just-inserted event`() =
        runTest {
            val viewModel = HomeViewModel(repository, settingsRepository, clock)
            val eventId = repository.insertEvent(testEvent())
            assertTrue(repository.events.value.isNotEmpty())

            viewModel.undoQuickLog(eventId)

            assertTrue(repository.events.value.isEmpty())
        }
}
