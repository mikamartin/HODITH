package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import com.secondmonday.hodith.testsupport.testCase
import com.secondmonday.hodith.testsupport.testEvent
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
class WatchesViewModelTest {
    private val repository = FakeHodithRepository()
    private val settingsRepository = FakeSettingsRepository()
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

    private fun viewModel() =
        WatchesViewModel(
            repository,
            settingsRepository,
            clock,
            NotificationPermissionRequestSignal(),
            SavedStateHandle(mapOf("caseId" to caseId)),
        )

    private fun oftenWatch(
        id: Long = 1L,
        caseId: Long = this.caseId,
        threshold: Int = 5,
        windowDays: Int? = 7,
        expectedPer: ExpectedPer = ExpectedPer.WEEK,
        metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity: Int? = null,
        enabled: Boolean = true,
        lastFiredAt: Long? = null,
    ) = WatchEntity(
        id = id,
        caseId = caseId,
        kind = WatchKind.OFTEN,
        threshold = threshold,
        windowDays = windowDays,
        expectedPer = expectedPer,
        metric = metric,
        minIntensity = minIntensity,
        enabled = enabled,
        lastFiredAt = lastFiredAt,
    )

    @Test
    fun `uiState only includes watches for this case`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId))
            repository.watches.value =
                listOf(oftenWatch(id = 1L, caseId = caseId), oftenWatch(id = 2L, caseId = 99L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(1, state.watches.size)
                assertEquals(1L, state.watches.single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState carries the Case's durationMode, intensityEnabled, checkInsEnabled and events`() =
        runTest {
            val case =
                testCase(
                    id = caseId,
                    durationMode = DurationMode.MANUAL,
                    intensityEnabled = true,
                    checkInsEnabled = false,
                    createdAt = 500L,
                )
            repository.cases.value = listOf(case)
            repository.events.value = listOf(testEvent(id = 1L, caseId = caseId, occurredAt = 1_000L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(DurationMode.MANUAL, state.durationMode)
                assertTrue(state.intensityEnabled)
                assertFalse(state.checkInsEnabled)
                assertEquals(500L, state.caseCreatedAt)
                assertEquals(1, state.events.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createWatch inserts an OFTEN watch with its window, per, metric and intensity`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId))

            viewModel().createWatch(
                kind = WatchKind.OFTEN,
                threshold = 5,
                windowDays = 7,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.DAYS_ACTIVE,
                minIntensity = 3,
            )

            val inserted = repository.watches.value.single()
            assertEquals(caseId, inserted.caseId)
            assertEquals(WatchKind.OFTEN, inserted.kind)
            assertEquals(5, inserted.threshold)
            assertEquals(7, inserted.windowDays)
            assertEquals(ExpectedPer.WEEK, inserted.expectedPer)
            assertEquals(VerdictMetric.DAYS_ACTIVE, inserted.metric)
            assertEquals(3, inserted.minIntensity)
            assertTrue(inserted.enabled)
        }

    @Test
    fun `createWatch drops windowDays, metric and intensity for QUIET`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId))

            viewModel().createWatch(
                kind = WatchKind.QUIET,
                threshold = 14,
                windowDays = 30,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.DAYS_ACTIVE,
                minIntensity = 3,
            )

            val inserted = repository.watches.value.single()
            assertEquals(WatchKind.QUIET, inserted.kind)
            assertEquals(14, inserted.threshold)
            assertNull(inserted.windowDays)
            assertEquals(VerdictMetric.OCCURRENCE_COUNT, inserted.metric)
            assertNull(inserted.minIntensity)
        }

    @Test
    fun `createWatch does not insert an OFTEN watch with a zero-day window`() =
        runTest {
            viewModel().createWatch(
                kind = WatchKind.OFTEN,
                threshold = 5,
                windowDays = 0,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.OCCURRENCE_COUNT,
                minIntensity = null,
            )

            assertTrue(repository.watches.value.isEmpty())
        }

    @Test
    fun `updateWatch edits an existing watch's fields, leaving armed and lastFiredAt alone`() =
        runTest {
            repository.watches.value = listOf(oftenWatch(id = 1L, threshold = 5, windowDays = 7, lastFiredAt = 999L))

            viewModel().updateWatch(
                watchId = 1L,
                kind = WatchKind.OFTEN,
                threshold = 10,
                windowDays = 30,
                expectedPer = ExpectedPer.MONTH,
                metric = VerdictMetric.DAYS_ACTIVE,
                minIntensity = 2,
            )

            val updated = repository.watches.value.single()
            assertEquals(10, updated.threshold)
            assertEquals(30, updated.windowDays)
            assertEquals(ExpectedPer.MONTH, updated.expectedPer)
            assertEquals(VerdictMetric.DAYS_ACTIVE, updated.metric)
            assertEquals(2, updated.minIntensity)
            assertEquals(999L, updated.lastFiredAt)
        }

    @Test
    fun `updateWatch does not persist an OFTEN edit with a zero-day window`() =
        runTest {
            repository.watches.value = listOf(oftenWatch(id = 1L, threshold = 5, windowDays = 7))

            viewModel().updateWatch(
                watchId = 1L,
                kind = WatchKind.OFTEN,
                threshold = 10,
                windowDays = 0,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.OCCURRENCE_COUNT,
                minIntensity = null,
            )

            assertEquals(
                5,
                repository.watches.value
                    .single()
                    .threshold,
            )
        }

    @Test
    fun `setEnabled updates only the enabled flag`() =
        runTest {
            repository.watches.value = listOf(oftenWatch(id = 1L, enabled = true))

            viewModel().setEnabled(watchId = 1L, enabled = false)

            val updated = repository.watches.value.single()
            assertFalse(updated.enabled)
            assertEquals(5, updated.threshold)
        }

    @Test
    fun `deleteWatch removes it`() =
        runTest {
            repository.watches.value = listOf(oftenWatch(id = 1L), oftenWatch(id = 2L))

            viewModel().deleteWatch(watchId = 1L)

            assertEquals(listOf(2L), repository.watches.value.map { it.id })
        }

    @Test
    fun `setCheckInsEnabled writes checkInsEnabled on the Case immediately`() =
        runTest {
            repository.cases.value = listOf(testCase(id = caseId, checkInsEnabled = true))

            viewModel().setCheckInsEnabled(false)

            assertFalse(
                repository.cases.value
                    .single()
                    .checkInsEnabled,
            )
        }

    @Test
    fun `watchCardState for OFTEN carries the observed rate and a verdict result`() {
        val watch = oftenWatch(threshold = 1, windowDays = 30)
        val now = clock.nowMillis()
        val events = (1..20).map { testEvent(id = it.toLong(), caseId = caseId, occurredAt = now - it * MILLIS_PER_DAY) }

        val state = watchCardState(watch, events, DurationMode.NONE, caseCreatedAt = 0L, now = now)

        assertEquals(WatchKind.OFTEN, state.kind)
        assertTrue((state.observedRate ?: 0.0) > 0.0)
        assertNull(state.silentDays)
        assertEquals(ConfidenceTier.CONFIDENT, state.verdictResult?.tier)
    }

    @Test
    fun `watchCardState for OFTEN reads NO_VERDICT below the confidence tier, gating the comparison line`() {
        val watch = oftenWatch(threshold = 1, windowDays = 30)
        val now = clock.nowMillis()
        val events = listOf(testEvent(id = 1L, caseId = caseId, occurredAt = now))

        val state = watchCardState(watch, events, DurationMode.NONE, caseCreatedAt = 0L, now = now)

        assertEquals(ConfidenceTier.NO_VERDICT, state.verdictResult?.tier)
    }

    @Test
    fun `watchCardState for QUIET carries silentDays from the latest event, not observedRate`() {
        val watch =
            WatchEntity(
                id = 1L,
                caseId = caseId,
                kind = WatchKind.QUIET,
                threshold = 14,
                windowDays = null,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.OCCURRENCE_COUNT,
                minIntensity = null,
                enabled = true,
                lastFiredAt = null,
            )
        val now = clock.nowMillis()
        val events = listOf(testEvent(id = 1L, caseId = caseId, occurredAt = now - 3 * MILLIS_PER_DAY))

        val state = watchCardState(watch, events, DurationMode.NONE, caseCreatedAt = 0L, now = now)

        assertEquals(WatchKind.QUIET, state.kind)
        assertEquals(3L, state.silentDays)
        assertNull(state.observedRate)
        assertNull(state.expectation)
        assertNull(state.verdictResult)
    }

    @Test
    fun `watchCardState for QUIET with no events falls back to caseCreatedAt`() {
        val watch =
            WatchEntity(
                id = 1L,
                caseId = caseId,
                kind = WatchKind.QUIET,
                threshold = 14,
                windowDays = null,
                expectedPer = ExpectedPer.WEEK,
                metric = VerdictMetric.OCCURRENCE_COUNT,
                minIntensity = null,
                enabled = true,
                lastFiredAt = null,
            )
        val now = clock.nowMillis()

        val state =
            watchCardState(
                watch,
                emptyList<EventEntity>(),
                DurationMode.NONE,
                caseCreatedAt = now - 5 * MILLIS_PER_DAY,
                now = now,
            )

        assertEquals(5L, state.silentDays)
    }

    @Test
    fun `watchCardState computes firedDaysAgo calendar-day-aware, null when never fired`() {
        val now = clock.nowMillis()
        val fired = oftenWatch(id = 1L, lastFiredAt = now - 2 * MILLIS_PER_DAY)
        val neverFired = oftenWatch(id = 2L, lastFiredAt = null)

        assertEquals(2L, watchCardState(fired, emptyList(), DurationMode.NONE, 0L, now).firedDaysAgo)
        assertNull(watchCardState(neverFired, emptyList(), DurationMode.NONE, 0L, now).firedDaysAgo)
    }
}
