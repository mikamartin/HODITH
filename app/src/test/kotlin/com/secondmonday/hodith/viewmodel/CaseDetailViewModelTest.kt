package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.HistorySortOrder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CaseDetailViewModelTest {
    private val repository = FakeHodithRepository()
    private val settings = FakeSettingsRepository()
    private val clock = FakeClock(1_000_000L)
    private val caseId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CaseDetailViewModel(repository, settings, clock, SavedStateHandle(mapOf("caseId" to caseId)))

    private fun testCase(durationMode: DurationMode = DurationMode.NONE) =
        Fixtures.case(id = caseId, name = "Coffee", icon = "☕️", logFlow = LogFlow.DETAIL_SHEET, durationMode = durationMode)

    private fun testEvent(
        occurredAt: Long = clock.nowMillis(),
        endedAt: Long? = clock.nowMillis(),
    ) = Fixtures.event(caseId = caseId, occurredAt = occurredAt, endedAt = endedAt)

    @Test
    fun `uiState reflects the case, its events and tag suggestions`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val eventId = repository.insertEvent(testEvent())
            repository.tags.value = listOf(TagEntity(id = 1L, name = "focus"))
            repository.eventTags.value = listOf(EventTagCrossRef(eventId = eventId, tagId = 1L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals("Coffee", state.case?.name)
                assertEquals(1, state.events.size)
                assertEquals(listOf("focus"), state.tagSuggestions.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState mostRecentActivityAcrossCasesAt reflects the latest logged event across active cases`() =
        runTest {
            val otherCaseId = 2L
            repository.cases.value =
                listOf(testCase(), Fixtures.case(id = otherCaseId, name = "Workout", icon = "🏋️", logFlow = LogFlow.DETAIL_SHEET))
            repository.insertEvent(testEvent(occurredAt = 100L, endedAt = 100L))
            repository.insertEvent(Fixtures.event(caseId = otherCaseId, occurredAt = 500L, endedAt = 500L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(500L, state.mostRecentActivityAcrossCasesAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState mostRecentActivityAcrossCasesAt ignores events on an archived case`() =
        runTest {
            val archivedCaseId = 2L
            repository.cases.value =
                listOf(
                    testCase(),
                    Fixtures.case(id = archivedCaseId, name = "Old thing", icon = "🗄️", logFlow = LogFlow.DETAIL_SHEET, archived = true),
                )
            repository.insertEvent(testEvent(occurredAt = 100L, endedAt = 100L))
            repository.insertEvent(Fixtures.event(caseId = archivedCaseId, occurredAt = 500L, endedAt = 500L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(100L, state.mostRecentActivityAcrossCasesAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState historyEvents caps at the initial 30-event window`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repeat(35) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(35, state.events.size)
                assertEquals(30, state.historyEvents.size)
                assertTrue(state.historyHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState historyHasMore is false once every event is loaded`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repeat(10) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(10, state.historyEvents.size)
                assertFalse(state.historyHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadMoreHistoryEvents grows the loaded window by 50`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repeat(100) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }
            val vm = viewModel()
            vm.loadMoreHistoryEvents()

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(80, state.historyEvents.size)
                assertTrue(state.historyHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistorySortOrder changes the sort order and resets the window back to 30`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            repeat(100) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }
            val vm = viewModel()
            vm.loadMoreHistoryEvents()
            vm.setHistorySortOrder(HistorySortOrder.BY_END)

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(HistorySortOrder.BY_END, state.historySortOrder)
                assertEquals(30, state.historyEvents.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistorySortOrder persists across a fresh ViewModel instance`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            viewModel().setHistorySortOrder(HistorySortOrder.BY_END)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(HistorySortOrder.BY_END, state.historySortOrder)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryDateFrom stores the picked date's local start-of-day millis and resets the window back to 30`() =
        runTest {
            val zone = ZoneId.systemDefault()
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            val today = clock.nowMillis().toLocalDateIn(zone)
            repeat(100) { i -> repository.insertEvent(testEvent(occurredAt = clock.nowMillis() + i)) }
            val vm = viewModel()
            vm.loadMoreHistoryEvents()

            vm.setHistoryDateFrom(today)

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(zone.startOfDayMillis(today), state.historyDateFrom)
                assertEquals(30, state.historyEvents.size)
                assertTrue(state.historyHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryDateFrom persists across a fresh ViewModel instance`() =
        runTest {
            val zone = ZoneId.systemDefault()
            val picked = LocalDate.of(2026, 7, 3)
            repository.cases.value = listOf(testCase())
            viewModel().setHistoryDateFrom(picked)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(zone.startOfDayMillis(picked), state.historyDateFrom)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryDateFrom of null clears the bound back to since-the-beginning`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()
            vm.setHistoryDateFrom(LocalDate.of(2026, 7, 3))

            vm.setHistoryDateFrom(null)

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertNull(state.historyDateFrom)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryDateTo stores the picked date's local end-of-day millis and resets the window back to 30`() =
        runTest {
            val zone = ZoneId.systemDefault()
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            val today = clock.nowMillis().toLocalDateIn(zone)
            repeat(100) { i -> repository.insertEvent(testEvent(occurredAt = clock.nowMillis() + i)) }
            val vm = viewModel()
            vm.loadMoreHistoryEvents()

            vm.setHistoryDateTo(today)

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(zone.endOfDayMillis(today), state.historyDateTo)
                assertEquals(30, state.historyEvents.size)
                assertTrue(state.historyHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryDateTo persists across a fresh ViewModel instance`() =
        runTest {
            val zone = ZoneId.systemDefault()
            val picked = LocalDate.of(2026, 8, 15)
            repository.cases.value = listOf(testCase())
            viewModel().setHistoryDateTo(picked)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(zone.endOfDayMillis(picked), state.historyDateTo)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryFieldVisible toggles a single field without disturbing the rest`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }

                vm.setHistoryFieldVisible(HistoryRowField.NOTES, visible = false)
                val afterRemoval = awaitItem().historyVisibleFields
                assertEquals(false, HistoryRowField.NOTES in afterRemoval)
                assertTrue(HistoryRowField.TAGS in afterRemoval)

                vm.setHistoryFieldVisible(HistoryRowField.NOTES, visible = true)
                assertTrue(HistoryRowField.NOTES in awaitItem().historyVisibleFields)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setHistoryFieldVisible persists across a fresh ViewModel instance`() =
        runTest {
            repository.cases.value = listOf(testCase())
            viewModel().setHistoryFieldVisible(HistoryRowField.TAGS, visible = false)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertFalse(HistoryRowField.TAGS in state.historyVisibleFields)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stopEvent sets endedAt to now`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            repository.insertEvent(testEvent(endedAt = null))
            val vm = viewModel()

            clock.advanceBy(60_000L)
            vm.stopEvent(repository.events.value.single())

            assertEquals(
                clock.nowMillis(),
                repository.events.value
                    .single()
                    .endedAt,
            )
        }

    @Test
    fun `saveNewEvent inserts an event with its tags`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()
            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                val draft = vm.newEventDraft().copy(note = "first time", tags = listOf("focus"))

                vm.saveNewEvent(draft)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, repository.events.value.size)
            assertEquals(
                "first time",
                repository.events.value
                    .single()
                    .note,
            )
            assertEquals(listOf("focus"), repository.tags.value.map { it.name })
        }
}
