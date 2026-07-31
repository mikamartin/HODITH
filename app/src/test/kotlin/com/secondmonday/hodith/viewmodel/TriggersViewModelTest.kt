package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
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

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalCoroutinesApi::class)
class TriggersViewModelTest {
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

    private fun viewModel() = TriggersViewModel(repository, clock, SavedStateHandle(mapOf("caseId" to caseId)))

    private fun atLeastTrigger(
        id: Long = 1L,
        caseId: Long = this.caseId,
        threshold: Int = 5,
        windowDays: Int? = 7,
        enabled: Boolean = true,
        lastFiredAt: Long? = null,
    ) = TriggerEntity(
        id = id,
        caseId = caseId,
        kind = TriggerKind.AT_LEAST,
        threshold = threshold,
        windowDays = windowDays,
        enabled = enabled,
        lastFiredAt = lastFiredAt,
    )

    @Test
    fun `uiState only includes triggers for this case`() =
        runTest {
            repository.triggers.value =
                listOf(atLeastTrigger(id = 1L, caseId = caseId), atLeastTrigger(id = 2L, caseId = 99L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(1, state.triggers.size)
                assertEquals(1L, state.triggers.single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState computes firedDaysAgo from lastFiredAt, null when never fired`() =
        runTest {
            val fired = clock.nowMillis() - 3 * MILLIS_PER_DAY
            repository.triggers.value =
                listOf(
                    atLeastTrigger(id = 1L, lastFiredAt = fired),
                    atLeastTrigger(id = 2L, lastFiredAt = null),
                )

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(3L, state.triggers.single { it.id == 1L }.firedDaysAgo)
                assertNull(state.triggers.single { it.id == 2L }.firedDaysAgo)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createTrigger inserts an AT_LEAST trigger with its window`() =
        runTest {
            viewModel().createTrigger(kind = TriggerKind.AT_LEAST, threshold = 5, windowDays = 7)

            val inserted = repository.triggers.value.single()
            assertEquals(caseId, inserted.caseId)
            assertEquals(TriggerKind.AT_LEAST, inserted.kind)
            assertEquals(5, inserted.threshold)
            assertEquals(7, inserted.windowDays)
            assertTrue(inserted.enabled)
        }

    @Test
    fun `createTrigger drops windowDays for SILENT_FOR even if one is passed`() =
        runTest {
            viewModel().createTrigger(kind = TriggerKind.SILENT_FOR, threshold = 14, windowDays = 30)

            val inserted = repository.triggers.value.single()
            assertEquals(TriggerKind.SILENT_FOR, inserted.kind)
            assertEquals(14, inserted.threshold)
            assertNull(inserted.windowDays)
        }

    @Test
    fun `setEnabled updates only the enabled flag`() =
        runTest {
            repository.triggers.value = listOf(atLeastTrigger(id = 1L, enabled = true))

            viewModel().setEnabled(triggerId = 1L, enabled = false)

            val updated = repository.triggers.value.single()
            assertFalse(updated.enabled)
            assertEquals(5, updated.threshold)
        }

    @Test
    fun `deleteTrigger removes it`() =
        runTest {
            repository.triggers.value = listOf(atLeastTrigger(id = 1L), atLeastTrigger(id = 2L))

            viewModel().deleteTrigger(triggerId = 1L)

            assertEquals(listOf(2L), repository.triggers.value.map { it.id })
        }

    @Test
    fun `triggerRows maps entities to rows with calendar-day-aware firedDaysAgo`() {
        val now = clock.nowMillis()
        val rows =
            triggerRows(
                triggers = listOf(atLeastTrigger(id = 1L, lastFiredAt = now - 2 * MILLIS_PER_DAY)),
                nowMillis = now,
            )

        assertEquals(2L, rows.single().firedDaysAgo)
    }
}
