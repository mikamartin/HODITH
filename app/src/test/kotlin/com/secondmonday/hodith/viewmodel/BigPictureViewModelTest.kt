package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.CaseWithEventsAndTags
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BigPictureViewModelTest {
    private val repository = FakeHodithRepository()
    private val clock = FakeClock(1_000_000L)
    private val zoneId = ZoneId.systemDefault()

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
        icon: String = "☕️",
        createdAt: Long = 0L,
        archived: Boolean = false,
    ) = CaseEntity(
        id = id,
        name = name,
        icon = icon,
        createdAt = createdAt,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = 0,
        archived = archived,
    )

    private fun testEvent(
        caseId: Long = 1L,
        occurredAt: Long = clock.nowMillis(),
        note: String? = null,
    ) = EventEntity(
        caseId = caseId,
        occurredAt = occurredAt,
        endedAt = occurredAt,
        intensity = null,
        note = note,
        loggedAt = occurredAt,
    )

    @Test
    fun `uiState reflects seeded active cases and events, excluding archived`() =
        runTest {
            repository.cases.value = listOf(testCase(), testCase(id = 2L, name = "Archived").copy(archived = true))
            repository.events.value = listOf(testEvent(note = "felt fine"))
            val viewModel = BigPictureViewModel(repository, clock)

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(1, state.cases.size)
                assertEquals("Coffee", state.cases.single().name)
                assertEquals(1, state.events.size)
                assertEquals("felt fine", state.events.single().note)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState maps each event's id and tag names`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val event = testEvent(note = "felt fine")
            repository.events.value = listOf(event)
            repository.tags.value = listOf(TagEntity(id = 1L, name = "late night"))
            repository.eventTags.value = listOf(EventTagCrossRef(eventId = event.id, tagId = 1L))
            val viewModel = BigPictureViewModel(repository, clock)

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                val mappedEvent = state.events.single()
                assertEquals(event.id, mappedEvent.id)
                assertEquals(listOf("late night"), mappedEvent.tags)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `bigPictureUiState derives earliestMonth from the oldest case createdAt`() {
        val marchCreatedAt = Instant.parse("2026-03-15T00:00:00Z").toEpochMilli()
        val aprilCreatedAt = Instant.parse("2026-04-01T00:00:00Z").toEpochMilli()
        val nowMillis = Instant.parse("2026-05-20T00:00:00Z").toEpochMilli()
        val casesWithEvents =
            listOf(
                withNoEvents(testCase(id = 1L, createdAt = aprilCreatedAt)),
                withNoEvents(testCase(id = 2L, createdAt = marchCreatedAt)),
            )

        val state = bigPictureUiState(casesWithEvents, nowMillis, zoneId)

        assertEquals(YearMonth.from(Instant.ofEpochMilli(marchCreatedAt).atZone(zoneId)), state.earliestMonth)
    }

    @Test
    fun `bigPictureUiState carries endedAt and marks only a START_STOP open event as ongoing`() {
        val nowMillis = Instant.parse("2026-05-20T12:00:00Z").toEpochMilli()
        val startMillis = Instant.parse("2026-05-18T09:00:00Z").toEpochMilli()
        val endMillis = Instant.parse("2026-05-19T09:00:00Z").toEpochMilli()

        fun event(
            caseId: Long,
            endedAt: Long?,
        ) = EventEntity(
            caseId = caseId,
            occurredAt = startMillis,
            endedAt = endedAt,
            intensity = null,
            note = null,
            loggedAt = startMillis,
        )

        val casesWithEvents =
            listOf(
                CaseWithEventsAndTags(
                    case = testCase(id = 1L).copy(durationMode = DurationMode.START_STOP),
                    events =
                        listOf(
                            EventWithTags(event(1L, endedAt = null), emptyList()),
                            EventWithTags(event(1L, endedAt = endMillis), emptyList()),
                        ),
                ),
                CaseWithEventsAndTags(
                    case = testCase(id = 2L).copy(durationMode = DurationMode.MANUAL),
                    events = listOf(EventWithTags(event(2L, endedAt = null), emptyList())),
                ),
            )

        val events = bigPictureUiState(casesWithEvents, nowMillis, zoneId).events

        val open = events.single { it.caseId == 1L && it.endedAt == null }
        val finished = events.single { it.caseId == 1L && it.endedAt != null }
        val manual = events.single { it.caseId == 2L }
        assertTrue(open.isOngoing)
        assertEquals(endMillis, finished.endedAt)
        assertEquals(false, finished.isOngoing)
        // Only START_STOP can be ongoing — a MANUAL event with a null endedAt is a data quirk, not a running event.
        assertEquals(false, manual.isOngoing)
    }

    @Test
    fun `bigPictureUiState falls back to currentMonth when there are no cases`() {
        val state = bigPictureUiState(emptyList(), clock.nowMillis(), zoneId)

        assertEquals(state.currentMonth, state.earliestMonth)
        assertTrue(state.cases.isEmpty())
        assertTrue(state.events.isEmpty())
    }

    @Test
    fun `bigPictureUiState drops a stored endedAt for a Case that no longer tracks duration`() {
        val nowMillis = Instant.parse("2026-05-20T12:00:00Z").toEpochMilli()
        val startMillis = Instant.parse("2026-05-16T09:00:00Z").toEpochMilli()
        val endMillis = Instant.parse("2026-05-19T09:00:00Z").toEpochMilli()
        val event =
            EventEntity(caseId = 1L, occurredAt = startMillis, endedAt = endMillis, intensity = null, note = null, loggedAt = startMillis)
        val casesWithEvents =
            listOf(
                CaseWithEventsAndTags(
                    case = testCase(id = 1L).copy(durationMode = DurationMode.NONE),
                    events = listOf(EventWithTags(event, emptyList())),
                ),
            )

        val mapped = bigPictureUiState(casesWithEvents, nowMillis, zoneId).events.single()

        assertNull(mapped.endedAt)
        assertEquals(false, mapped.isOngoing)
    }

    @Test
    fun `bigPictureUiState keeps a stored endedAt for a MANUAL Case`() {
        val nowMillis = Instant.parse("2026-05-20T12:00:00Z").toEpochMilli()
        val startMillis = Instant.parse("2026-05-16T09:00:00Z").toEpochMilli()
        val endMillis = Instant.parse("2026-05-19T09:00:00Z").toEpochMilli()
        val event =
            EventEntity(caseId = 1L, occurredAt = startMillis, endedAt = endMillis, intensity = null, note = null, loggedAt = startMillis)
        val casesWithEvents =
            listOf(
                CaseWithEventsAndTags(
                    case = testCase(id = 1L).copy(durationMode = DurationMode.MANUAL),
                    events = listOf(EventWithTags(event, emptyList())),
                ),
            )

        val mapped = bigPictureUiState(casesWithEvents, nowMillis, zoneId).events.single()

        assertEquals(endMillis, mapped.endedAt)
        assertEquals(false, mapped.isOngoing)
    }

    private fun withNoEvents(case: CaseEntity) = CaseWithEventsAndTags(case = case, events = emptyList())
}
