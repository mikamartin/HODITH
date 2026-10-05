package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.domain.TagBreakdownEntry
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
class TagsListViewModelTest {
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

    private fun viewModel(clock: FakeClock) = TagsListViewModel(repository, clock, SavedStateHandle(mapOf("caseId" to caseId)))

    @Test
    fun `uiState carries the case icon, name, and duration mode`() =
        runTest {
            repository.cases.value =
                listOf(testCase(id = caseId, name = "Coffee", icon = "☕", createdAt = millisAtDay(0), durationMode = DurationMode.MANUAL))
            repository.events.value = listOf(eventAtDay(0))

            viewModel(FakeClock(millisAtDay(10))).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals("☕", state.caseIcon)
                assertEquals("Coffee", state.caseName)
                assertEquals(DurationMode.MANUAL, state.durationMode)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState lists every distinct tag, busiest first, with no cap`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId, createdAt = millisAtDay(0)))
            // Seven distinct tags: "a" is on three events, "b" on two, the rest on one each.
            val tagNames = listOf("a", "b", "c", "d", "e", "f", "g")
            repository.tags.value = tagNames.mapIndexed { index, name -> TagEntity(id = index + 1L, name = name) }
            repository.events.value = (1L..3L).map { eventAtDay(it).copy(id = it) }
            repository.eventTags.value =
                listOf(
                    EventTagCrossRef(eventId = 1, tagId = 1),
                    EventTagCrossRef(eventId = 1, tagId = 2),
                    EventTagCrossRef(eventId = 2, tagId = 1),
                    EventTagCrossRef(eventId = 2, tagId = 2),
                    EventTagCrossRef(eventId = 3, tagId = 1),
                    EventTagCrossRef(eventId = 3, tagId = 3),
                    EventTagCrossRef(eventId = 3, tagId = 4),
                    EventTagCrossRef(eventId = 3, tagId = 5),
                    EventTagCrossRef(eventId = 3, tagId = 6),
                    EventTagCrossRef(eventId = 3, tagId = 7),
                )

            viewModel(FakeClock(millisAtDay(10))).uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(
                    listOf(
                        TagBreakdownEntry("a", 3),
                        TagBreakdownEntry("b", 2),
                        TagBreakdownEntry("c", 1),
                        TagBreakdownEntry("d", 1),
                        TagBreakdownEntry("e", 1),
                        TagBreakdownEntry("f", 1),
                        TagBreakdownEntry("g", 1),
                    ),
                    state.tags,
                )
                assertEquals(3, state.eventsWithTags.size)
                assertEquals(3, state.totalEventCount)
                assertEquals(7, state.distinctTagCount)
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
                assertTrue(state.tags.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
