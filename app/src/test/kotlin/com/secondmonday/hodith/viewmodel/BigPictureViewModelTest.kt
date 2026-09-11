package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.BigPictureDetail
import com.secondmonday.hodith.data.BigPictureDetailField
import com.secondmonday.hodith.data.CaseEventDetail
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
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
    private val settings = FakeSettingsRepository()
    private val clock = FakeClock(1_000_000L)
    private val zoneId = ZoneId.systemDefault()

    private fun viewModel() = BigPictureViewModel(repository, settings, clock)

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
    ) = Fixtures.case(id = id, name = name, icon = icon, createdAt = createdAt, archived = archived)

    private fun testEvent(
        caseId: Long = 1L,
        occurredAt: Long = clock.nowMillis(),
        note: String? = null,
    ) = Fixtures.event(caseId = caseId, occurredAt = occurredAt, endedAt = occurredAt, note = note)

    /** [bigPictureUiState] now takes the flat [CaseEventDetail] projection, not a nested relation. */
    private fun detailOf(event: EventEntity) =
        CaseEventDetail(
            id = event.id,
            caseId = event.caseId,
            occurredAt = event.occurredAt,
            endedAt = event.endedAt,
            intensity = event.intensity,
            note = event.note,
        )

    @Test
    fun `uiState reflects seeded active cases and events, excluding archived`() =
        runTest {
            repository.cases.value = listOf(testCase(), testCase(id = 2L, name = "Archived").copy(archived = true))
            repository.events.value = listOf(testEvent(note = "felt fine"))
            val viewModel = viewModel()

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
    fun `uiState maps each event's id and its tag names, sorted alphabetically`() =
        runTest {
            repository.cases.value = listOf(testCase())
            val event = testEvent(note = "felt fine")
            repository.events.value = listOf(event)
            repository.tags.value =
                listOf(
                    TagEntity(id = 1L, name = "work"),
                    TagEntity(id = 2L, name = "admin"),
                    TagEntity(id = 3L, name = "late night"),
                )
            repository.eventTags.value = (1L..3L).map { EventTagCrossRef(eventId = event.id, tagId = it) }
            val viewModel = viewModel()

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                val mappedEvent = state.events.single()
                assertEquals(event.id, mappedEvent.id)
                assertEquals(listOf("admin", "late night", "work"), mappedEvent.tags)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `bigPictureUiState derives earliestMonth from the oldest case createdAt`() {
        val marchCreatedAt = Instant.parse("2026-03-15T00:00:00Z").toEpochMilli()
        val aprilCreatedAt = Instant.parse("2026-04-01T00:00:00Z").toEpochMilli()
        val nowMillis = Instant.parse("2026-05-20T00:00:00Z").toEpochMilli()
        val cases =
            listOf(
                testCase(id = 1L, createdAt = aprilCreatedAt),
                testCase(id = 2L, createdAt = marchCreatedAt),
            )

        val state = bigPictureUiState(cases, emptyList(), emptyList(), nowMillis, zoneId)

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
        ) = Fixtures.event(
            caseId = caseId,
            occurredAt = startMillis,
            endedAt = endedAt,
            loggedAt = startMillis,
        )

        val cases =
            listOf(
                testCase(id = 1L).copy(durationMode = DurationMode.START_STOP),
                testCase(id = 2L).copy(durationMode = DurationMode.MANUAL),
            )
        val eventDetails =
            listOf(
                detailOf(event(1L, endedAt = null)),
                detailOf(event(1L, endedAt = endMillis)),
                detailOf(event(2L, endedAt = null)),
            )

        val events = bigPictureUiState(cases, eventDetails, emptyList(), nowMillis, zoneId).events

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
        val state = bigPictureUiState(emptyList(), emptyList(), emptyList(), clock.nowMillis(), zoneId)

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
            Fixtures.event(caseId = 1L, occurredAt = startMillis, endedAt = endMillis, loggedAt = startMillis)
        val cases = listOf(testCase(id = 1L).copy(durationMode = DurationMode.NONE))

        val mapped = bigPictureUiState(cases, listOf(detailOf(event)), emptyList(), nowMillis, zoneId).events.single()

        assertNull(mapped.endedAt)
        assertEquals(false, mapped.isOngoing)
    }

    @Test
    fun `bigPictureUiState keeps a stored endedAt for a MANUAL Case`() {
        val nowMillis = Instant.parse("2026-05-20T12:00:00Z").toEpochMilli()
        val startMillis = Instant.parse("2026-05-16T09:00:00Z").toEpochMilli()
        val endMillis = Instant.parse("2026-05-19T09:00:00Z").toEpochMilli()
        val event =
            Fixtures.event(caseId = 1L, occurredAt = startMillis, endedAt = endMillis, loggedAt = startMillis)
        val cases = listOf(testCase(id = 1L).copy(durationMode = DurationMode.MANUAL))

        val mapped = bigPictureUiState(cases, listOf(detailOf(event)), emptyList(), nowMillis, zoneId).events.single()

        assertEquals(endMillis, mapped.endedAt)
        assertEquals(false, mapped.isOngoing)
    }

    // ---- intensity projection (spec §9: detail rows only, never the grid) ----

    @Test
    fun `bigPictureUiState carries an event's intensity when the Case has intensity enabled`() {
        val cases = listOf(testCase(id = 1L).copy(intensityEnabled = true))
        val eventDetails = listOf(detailOf(Fixtures.event(caseId = 1L, intensity = 4)))

        assertEquals(4, bigPictureUiState(cases, eventDetails, emptyList(), clock.nowMillis(), zoneId).events.single().intensity)
    }

    @Test
    fun `bigPictureUiState drops a stored intensity when the Case has intensity disabled`() {
        val cases = listOf(testCase(id = 1L).copy(intensityEnabled = false))
        val eventDetails = listOf(detailOf(Fixtures.event(caseId = 1L, intensity = 4)))

        assertNull(bigPictureUiState(cases, eventDetails, emptyList(), clock.nowMillis(), zoneId).events.single().intensity)
    }

    @Test
    fun `bigPictureUiState leaves intensity null when the event has none`() {
        val cases = listOf(testCase(id = 1L).copy(intensityEnabled = true))
        val eventDetails = listOf(detailOf(Fixtures.event(caseId = 1L, intensity = null)))

        assertNull(bigPictureUiState(cases, eventDetails, emptyList(), clock.nowMillis(), zoneId).events.single().intensity)
    }

    @Test
    fun `bigPictureUiState keeps a same-day endedAt on a duration-tracking Case so the row can show a duration`() {
        val occurredAt = Instant.parse("2026-05-16T09:10:00Z").toEpochMilli()
        val endedAt = Instant.parse("2026-05-16T09:50:00Z").toEpochMilli()
        val cases = listOf(testCase(id = 1L).copy(durationMode = DurationMode.MANUAL))
        val eventDetails = listOf(detailOf(Fixtures.event(caseId = 1L, occurredAt = occurredAt, endedAt = endedAt)))

        assertEquals(
            endedAt,
            bigPictureUiState(cases, eventDetails, emptyList(), clock.nowMillis(), zoneId).events.single().endedAt,
        )
    }

    // ---- overview-detail preference wiring ----

    @Test
    fun `bigPictureUiState carries the detail argument, defaulting to DEFAULT`() {
        assertEquals(BigPictureDetail.DEFAULT, bigPictureUiState(emptyList(), emptyList(), emptyList(), clock.nowMillis()).detail)
        assertEquals(
            BigPictureDetail.ALL_OFF,
            bigPictureUiState(emptyList(), emptyList(), emptyList(), clock.nowMillis(), detail = BigPictureDetail.ALL_OFF).detail,
        )
    }

    @Test
    fun `uiState detail defaults to DEFAULT with an untouched settings repository`() =
        runTest {
            repository.cases.value = listOf(testCase())
            viewModel().uiState.test {
                assertEquals(BigPictureDetail.DEFAULT, awaitLoadedItem { it.isLoading }.detail)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState reflects a detail value emitted by the settings repository`() =
        runTest {
            repository.cases.value = listOf(testCase())
            viewModel().uiState.test {
                awaitLoadedItem { it.isLoading }
                settings.bigPictureDetail.value = BigPictureDetail.ALL_OFF
                assertEquals(BigPictureDetail.ALL_OFF, awaitItem().detail)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState keeps the current detail across a repository change`() =
        runTest {
            settings.bigPictureDetail.value = BigPictureDetail.DEFAULT.with(BigPictureDetailField.INTENSITY, true)
            viewModel().uiState.test {
                awaitLoadedItem { it.isLoading }
                repository.cases.value = listOf(testCase())
                val state = awaitItem()
                assertEquals(1, state.cases.size)
                assertTrue(state.detail.intensity)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDetail flips one field and persists the rest unchanged`() =
        runTest {
            val vm = viewModel()

            vm.setDetail(BigPictureDetailField.INTENSITY, true)
            assertEquals(BigPictureDetail.DEFAULT.copy(intensity = true), settings.bigPictureDetail.value)

            vm.setDetail(BigPictureDetailField.NOTES, false)
            assertEquals(
                BigPictureDetail.DEFAULT.copy(intensity = true, notes = false),
                settings.bigPictureDetail.value,
            )
        }

    @Test
    fun `setDetail off then on round-trips to the starting value`() =
        runTest {
            val vm = viewModel()
            vm.setDetail(BigPictureDetailField.DURATION, false)
            vm.setDetail(BigPictureDetailField.DURATION, true)

            assertEquals(BigPictureDetail.DEFAULT, settings.bigPictureDetail.value)
        }

    @Test
    fun `setDetail from a non-default starting point preserves the fields it is not touching`() =
        runTest {
            settings.bigPictureDetail.value = BigPictureDetail.ALL_OFF
            val vm = viewModel()

            vm.setDetail(BigPictureDetailField.TAGS, true)

            assertEquals(BigPictureDetail.ALL_OFF.copy(tags = true), settings.bigPictureDetail.value)
        }
}
