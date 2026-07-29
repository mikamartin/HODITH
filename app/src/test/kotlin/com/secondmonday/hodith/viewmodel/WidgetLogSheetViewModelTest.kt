package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.FakeClock
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetLogSheetViewModelTest {
    private val repository = FakeHodithRepository()
    private val clock = FakeClock(1_000_000L)
    private val widgetRefresher = FakeWidgetRefresher()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = WidgetLogSheetViewModel(repository, clock, widgetRefresher)

    private fun testCase(
        id: Long = 1L,
        name: String = "Coffee",
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.DETAIL_SHEET,
        durationMode = durationMode,
        intensityEnabled = intensityEnabled,
        hunchNudgeDismissed = false,
        pinned = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = false,
    )

    @Test
    fun `nowMillis reads through to the injected clock`() =
        runTest {
            val viewModel = newViewModel()

            assertEquals(clock.nowMillis(), viewModel.nowMillis())
        }

    @Test
    fun `load populates state from the case and a fresh draft`() =
        runTest {
            repository.cases.value = listOf(testCase(durationMode = DurationMode.MANUAL, intensityEnabled = true))

            val viewModel = newViewModel()
            viewModel.load(caseId = 1L)

            val state = viewModel.uiState.value
            assertEquals("Coffee", state?.caseName)
            assertEquals(DurationMode.MANUAL, state?.durationMode)
            assertTrue(state?.intensityEnabled == true)
            assertEquals(clock.nowMillis(), state?.draft?.occurredAt)
        }

    @Test
    fun `load includes tag suggestions already used on the case`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val eventId = repository.insertEvent(testEvent(caseId = 1L))
            repository.addTagToEvent(eventId, "focus")

            val viewModel = newViewModel()
            viewModel.load(caseId = 1L)

            assertEquals(
                listOf("focus"),
                viewModel.uiState.value
                    ?.tagSuggestions
                    ?.map { it.name },
            )
        }

    @Test
    fun `load leaves state null for a case that no longer exists`() =
        runTest {
            val viewModel = newViewModel()

            viewModel.load(caseId = 404L)

            assertNull(viewModel.uiState.value)
        }

    @Test
    fun `save inserts an event, applies tags, refreshes the widget, and signals onSaved`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val viewModel = newViewModel()
            viewModel.load(caseId = 1L)
            val draft =
                viewModel.uiState.value!!
                    .draft
                    .copy(note = "Ouch", tags = listOf("focus"))
            var saved = false

            viewModel.save(draft) { saved = true }

            assertTrue(saved)
            val event = repository.events.value.single()
            assertEquals(1L, event.caseId)
            assertEquals("Ouch", event.note)
            assertEquals(listOf("focus"), repository.tags.value.map { it.name })
            assertEquals(1, widgetRefresher.refreshCount)
        }

    @Test
    fun `save before load does nothing`() =
        runTest {
            val viewModel = newViewModel()
            var saved = false

            viewModel.save(draftFrom(event = null, now = clock.nowMillis())) { saved = true }

            assertFalse(saved)
            assertTrue(repository.events.value.isEmpty())
            assertEquals(0, widgetRefresher.refreshCount)
        }

    private fun testEvent(caseId: Long) =
        EventEntity(
            caseId = caseId,
            occurredAt = 0L,
            endedAt = null,
            intensity = null,
            note = null,
            loggedAt = 0L,
        )
}
