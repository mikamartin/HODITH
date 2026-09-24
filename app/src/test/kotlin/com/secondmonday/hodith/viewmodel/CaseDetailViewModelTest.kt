package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.LogSortOrder
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.domain.HUNCH_HISTORY_RETENTION_LIMIT
import com.secondmonday.hodith.domain.computeVerdict
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun `uiState logEvents caps at the initial 30-event window`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repeat(35) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(35, state.events.size)
                assertEquals(30, state.logEvents.size)
                assertTrue(state.logHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState logHasMore is false once every event is loaded`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repeat(10) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(10, state.logEvents.size)
                assertFalse(state.logHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadMoreLogEvents grows the loaded window by 50`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repeat(100) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }
            val vm = viewModel()
            vm.loadMoreLogEvents()

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(80, state.logEvents.size)
                assertTrue(state.logHasMore)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setLogSortOrder changes the sort order and resets the window back to 30`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            repeat(100) { i -> repository.insertEvent(testEvent(occurredAt = i.toLong())) }
            val vm = viewModel()
            vm.loadMoreLogEvents()
            vm.setLogSortOrder(LogSortOrder.BY_END)

            vm.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(LogSortOrder.BY_END, state.logSortOrder)
                assertEquals(30, state.logEvents.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setLogSortOrder persists across a fresh ViewModel instance`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            viewModel().setLogSortOrder(LogSortOrder.BY_END)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(LogSortOrder.BY_END, state.logSortOrder)
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

    @Test
    fun `uiState reflects the active hunch and history`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val active =
                HunchEntity(
                    id = 1L,
                    caseId = caseId,
                    direction = HunchDirection.TOO_OFTEN,
                    expectedCount = 5,
                    expectedPer = ExpectedPer.WEEK,
                    createdAt = 0L,
                    resolvedAt = null,
                )
            val resolved =
                HunchEntity(
                    id = 2L,
                    caseId = caseId,
                    direction = HunchDirection.NOT_ENOUGH,
                    expectedCount = 1,
                    expectedPer = ExpectedPer.MONTH,
                    createdAt = -100L,
                    resolvedAt = -50L,
                )
            repository.hunches.value = listOf(active, resolved)

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(active, state.activeHunch)
                assertEquals(listOf(active, resolved), state.hunchHistory)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `addHunch inserts a Hunch for this case`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()

            vm.addHunch(
                HunchDirection.TOO_OFTEN,
                expectedCount = 5,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.DAYS_ACTIVE,
                observationWindow = ObservationWindow.CUSTOM,
                windowStartDate = 1_234L,
            )

            val hunch = repository.hunches.value.single()
            assertEquals(caseId, hunch.caseId)
            assertEquals(HunchDirection.TOO_OFTEN, hunch.direction)
            assertEquals(5, hunch.expectedCount)
            assertEquals(ExpectedPer.WEEK, hunch.expectedPer)
            assertEquals(clock.nowMillis(), hunch.createdAt)
            assertEquals(null, hunch.resolvedAt)
            assertEquals(VerdictMetric.DAYS_ACTIVE, hunch.metric)
            assertEquals(ObservationWindow.CUSTOM, hunch.observationWindow)
            assertEquals(1_234L, hunch.windowStartDate)
        }

    @Test
    fun `resolveHunch stamps resolvedAt with now`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val hunch =
                HunchEntity(
                    id = 1L,
                    caseId = caseId,
                    direction = HunchDirection.TOO_OFTEN,
                    expectedCount = 5,
                    expectedPer = ExpectedPer.WEEK,
                    createdAt = 0L,
                    resolvedAt = null,
                )
            repository.hunches.value = listOf(hunch)
            val vm = viewModel()

            clock.advanceBy(60_000L)
            vm.resolveHunch(hunch)

            assertEquals(
                clock.nowMillis(),
                repository.hunches.value
                    .single()
                    .resolvedAt,
            )
        }

    @Test
    fun `resolveHunch does nothing when the case is missing`() =
        runTest {
            // No repository.cases seeded — mirrors a case deleted out from under an in-flight
            // resolve (e.g. a rapid delete right after tapping Resolve).
            val hunch =
                HunchEntity(
                    id = 1L,
                    caseId = caseId,
                    direction = HunchDirection.TOO_OFTEN,
                    expectedCount = 5,
                    expectedPer = ExpectedPer.WEEK,
                    createdAt = 0L,
                    resolvedAt = null,
                )
            repository.hunches.value = listOf(hunch)
            val vm = viewModel()

            vm.resolveHunch(hunch)

            assertEquals(hunch, repository.hunches.value.single())
        }

    @Test
    fun `resolveHunch persists the verdict snapshot alongside resolvedAt`() =
        runTest {
            val case = testCase()
            repository.cases.value = listOf(case)
            repeat(5) { i -> repository.insertEvent(testEvent(occurredAt = i * 1_000L)) }
            val hunch =
                HunchEntity(
                    id = 1L,
                    caseId = caseId,
                    direction = HunchDirection.TOO_OFTEN,
                    expectedCount = 5,
                    expectedPer = ExpectedPer.WEEK,
                    createdAt = 0L,
                    resolvedAt = null,
                )
            repository.hunches.value = listOf(hunch)
            val vm = viewModel()

            clock.advanceBy(60_000L)
            vm.resolveHunch(hunch)

            val resolved = repository.hunches.value.single()
            val expected =
                computeVerdict(hunch, repository.events.value, case.createdAt, clock.nowMillis(), case.durationMode)
            assertTrue(resolved.resolvedVerdictSnapshotTaken)
            assertEquals(expected.tier, resolved.resolvedTier)
            assertEquals(expected.comparisonBand, resolved.resolvedComparisonBand)
            assertEquals(expected.eventCount, resolved.resolvedEventCount)
            assertEquals(expected.activeDayCount, resolved.resolvedActiveDayCount)
            assertEquals(expected.windowDays, resolved.resolvedWindowDays)
            assertEquals(expected.observedRate, resolved.resolvedObservedRate)
            assertEquals(expected.expectedRate, resolved.resolvedExpectedRate)
        }

    @Test
    fun `resolveHunch prunes resolved history beyond the retention limit`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val alreadyResolved =
                (1..HUNCH_HISTORY_RETENTION_LIMIT).map { i ->
                    HunchEntity(
                        id = i.toLong(),
                        caseId = caseId,
                        direction = HunchDirection.TOO_OFTEN,
                        expectedCount = 5,
                        expectedPer = ExpectedPer.WEEK,
                        createdAt = 0L,
                        resolvedAt = i.toLong(),
                    )
                }
            val hunchBeingResolved =
                HunchEntity(
                    id = HUNCH_HISTORY_RETENTION_LIMIT + 1L,
                    caseId = caseId,
                    direction = HunchDirection.TOO_OFTEN,
                    expectedCount = 5,
                    expectedPer = ExpectedPer.WEEK,
                    createdAt = 0L,
                    resolvedAt = null,
                )
            repository.hunches.value = alreadyResolved + hunchBeingResolved
            val vm = viewModel()

            clock.advanceBy(60_000L)
            vm.resolveHunch(hunchBeingResolved)

            val resolvedIds =
                repository.hunches.value
                    .filter { it.resolvedAt != null }
                    .map { it.id }
            assertEquals(HUNCH_HISTORY_RETENTION_LIMIT, resolvedIds.size)
            // The oldest-resolved of the pre-existing hunches (id 1, resolvedAt 1L) is the one
            // pruned to make room for the newly resolved hunch.
            assertFalse(1L in resolvedIds)
            assertTrue(hunchBeingResolved.id in resolvedIds)
        }
}
