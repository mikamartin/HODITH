package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class CaseDetailViewModelTest {
    private val repository = FakeHodithRepository()
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

    private fun viewModel() = CaseDetailViewModel(repository, clock, SavedStateHandle(mapOf("caseId" to caseId)))

    private fun testCase(durationMode: DurationMode = DurationMode.NONE) =
        CaseEntity(
            id = caseId,
            name = "Coffee",
            icon = "☕️",
            createdAt = 0L,
            logFlow = LogFlow.DETAIL_SHEET,
            durationMode = durationMode,
            intensityEnabled = false,
            hunchNudgeDismissed = false,
            pinned = false,
            checkInDays = null,
            lastCheckInAt = null,
            sortOrder = 0,
            archived = false,
        )

    private fun testEvent(
        occurredAt: Long = clock.nowMillis(),
        endedAt: Long? = clock.nowMillis(),
    ) = EventEntity(
        caseId = caseId,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = null,
        note = null,
        loggedAt = occurredAt,
    )

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
    fun `deleteEvent removes the event`() =
        runTest {
            repository.cases.value = listOf(testCase())
            repository.insertEvent(testEvent())

            viewModel().deleteEvent(repository.events.value.single())

            assertTrue(repository.events.value.isEmpty())
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
    fun `dismissStalePrompt records the dismissal time`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.START_STOP))
            repository.insertEvent(testEvent(endedAt = null))
            val vm = viewModel()

            clock.advanceBy(60_000L)
            vm.dismissStalePrompt(repository.events.value.single())

            assertEquals(
                clock.nowMillis(),
                repository.events.value
                    .single()
                    .staleNudgeDismissedAt,
            )
        }

    @Test
    fun `saveEvent inserts a new event when there is no existing event`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()
            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                val draft = vm.newEventDraft().copy(note = "first time", tags = listOf("focus"))

                vm.saveEvent(draft, existingEvent = null, originalTags = emptyList())
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
    fun `saveEvent updates an existing event and diffs tags`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val eventId = repository.insertEvent(testEvent(occurredAt = 500L, endedAt = null))
            repository.addTagToEvent(eventId, "old")
            val existingEvent = repository.events.value.single()
            val originalTags = repository.observeTagsForEvent(eventId).first()
            val vm = viewModel()

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                val draft = vm.newEventDraft().copy(occurredAt = 500L, note = "updated", tags = listOf("new"))

                vm.saveEvent(draft, existingEvent = existingEvent, originalTags = originalTags)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, repository.events.value.size)
            assertEquals(
                "updated",
                repository.events.value
                    .single()
                    .note,
            )
            assertEquals(listOf("new"), repository.observeTagsForEvent(eventId).first().map { it.name })
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

            vm.addHunch(HunchDirection.TOO_OFTEN, expectedCount = 5, expectedPer = ExpectedPer.WEEK)

            val hunch = repository.hunches.value.single()
            assertEquals(caseId, hunch.caseId)
            assertEquals(HunchDirection.TOO_OFTEN, hunch.direction)
            assertEquals(5, hunch.expectedCount)
            assertEquals(ExpectedPer.WEEK, hunch.expectedPer)
            assertEquals(clock.nowMillis(), hunch.createdAt)
            assertEquals(null, hunch.resolvedAt)
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
    fun `dismissHunchNudge sets hunchNudgeDismissed on the case`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val vm = viewModel()
            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissHunchNudge()

            assertTrue(
                repository.cases.value
                    .single()
                    .hunchNudgeDismissed,
            )
        }
}
