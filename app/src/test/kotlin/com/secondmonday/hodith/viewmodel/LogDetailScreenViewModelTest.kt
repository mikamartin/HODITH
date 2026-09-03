package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.testsupport.testCase
import com.secondmonday.hodith.testsupport.testEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class LogDetailScreenViewModelTest {
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

    private fun viewModel(eventId: Long) =
        LogDetailScreenViewModel(
            repository,
            clock,
            SavedStateHandle(mapOf("caseId" to caseId, "eventId" to eventId)),
        )

    @Test
    fun `loads the event's fields and its Case's config into the draft`() =
        runTest {
            repository.cases.value =
                listOf(testCase(id = caseId, durationMode = DurationMode.MANUAL, intensityEnabled = true))
            val eventId =
                repository.insertEvent(
                    testEvent(caseId = caseId, occurredAt = 0L, endedAt = 45 * 60_000L, intensity = 3, note = "kept"),
                )
            repository.addTagToEvent(eventId, "focus")

            viewModel(eventId).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(DurationMode.MANUAL, state.durationMode)
                assertTrue(state.intensityEnabled)
                assertEquals("kept", state.initialDraft?.note)
                assertEquals(3, state.initialDraft?.intensity)
                assertEquals("45", state.initialDraft?.durationAmount)
                assertEquals(listOf("focus"), state.initialDraft?.tags)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save updates the event and diffs its tags`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId))
            val eventId = repository.insertEvent(testEvent(caseId = caseId, occurredAt = 500L))
            repository.addTagToEvent(eventId, "old")
            val vm = viewModel(eventId)

            vm.uiState.test {
                val draft = awaitLoadedItem { it.isLoading }.initialDraft!!
                vm.save(draft.copy(note = "updated", tags = listOf("new")))
                assertTrue(awaitItem().isFinished)
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
    fun `delete removes the event`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId))
            val eventId = repository.insertEvent(testEvent(caseId = caseId))
            val vm = viewModel(eventId)

            vm.uiState.test {
                awaitLoadedItem { it.isLoading }
                vm.delete()
                assertTrue(awaitItem().isFinished)
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(repository.events.value.isEmpty())
        }

    @Test
    fun `a missing event finishes immediately with nothing to edit`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId))

            viewModel(eventId = 999L).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertTrue(state.isFinished)
                assertNull(state.initialDraft)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
