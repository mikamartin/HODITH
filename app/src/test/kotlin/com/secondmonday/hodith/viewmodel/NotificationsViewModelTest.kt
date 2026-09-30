package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
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
class NotificationsViewModelTest {
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
        NotificationsViewModel(
            repository,
            settingsRepository,
            clock,
            NotificationPermissionRequestSignal(),
            SavedStateHandle(mapOf("caseId" to caseId)),
        )

    private fun oftenNotification(
        id: Long = 1L,
        caseId: Long = this.caseId,
        threshold: Int = 5,
        windowDays: Int? = 7,
        enabled: Boolean = true,
        lastFiredAt: Long? = null,
    ) = NotificationEntity(
        id = id,
        caseId = caseId,
        kind = NotificationKind.OFTEN,
        threshold = threshold,
        windowDays = windowDays,
        expectedPer = ExpectedPer.WEEK,
        metric = VerdictMetric.OCCURRENCE_COUNT,
        minIntensity = null,
        enabled = enabled,
        lastFiredAt = lastFiredAt,
    )

    @Test
    fun `uiState only includes notifications for this case`() =
        runTest {
            repository.notifications.value =
                listOf(oftenNotification(id = 1L, caseId = caseId), oftenNotification(id = 2L, caseId = 99L))

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(1, state.notifications.size)
                assertEquals(1L, state.notifications.single().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState computes firedDaysAgo from lastFiredAt, null when never fired`() =
        runTest {
            val fired = clock.nowMillis() - 3 * MILLIS_PER_DAY
            repository.notifications.value =
                listOf(
                    oftenNotification(id = 1L, lastFiredAt = fired),
                    oftenNotification(id = 2L, lastFiredAt = null),
                )

            viewModel().uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(3L, state.notifications.single { it.id == 1L }.firedDaysAgo)
                assertNull(state.notifications.single { it.id == 2L }.firedDaysAgo)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createNotification inserts an OFTEN notification with its window`() =
        runTest {
            viewModel().createNotification(kind = NotificationKind.OFTEN, threshold = 5, windowDays = 7)

            val inserted = repository.notifications.value.single()
            assertEquals(caseId, inserted.caseId)
            assertEquals(NotificationKind.OFTEN, inserted.kind)
            assertEquals(5, inserted.threshold)
            assertEquals(7, inserted.windowDays)
            assertTrue(inserted.enabled)
        }

    @Test
    fun `createNotification drops windowDays for QUIET even if one is passed`() =
        runTest {
            viewModel().createNotification(kind = NotificationKind.QUIET, threshold = 14, windowDays = 30)

            val inserted = repository.notifications.value.single()
            assertEquals(NotificationKind.QUIET, inserted.kind)
            assertEquals(14, inserted.threshold)
            assertNull(inserted.windowDays)
        }

    @Test
    fun `createNotification does not insert an OFTEN notification with a zero-day window`() =
        runTest {
            viewModel().createNotification(kind = NotificationKind.OFTEN, threshold = 5, windowDays = 0)

            assertTrue(repository.notifications.value.isEmpty())
        }

    @Test
    fun `setEnabled updates only the enabled flag`() =
        runTest {
            repository.notifications.value = listOf(oftenNotification(id = 1L, enabled = true))

            viewModel().setEnabled(notificationId = 1L, enabled = false)

            val updated = repository.notifications.value.single()
            assertFalse(updated.enabled)
            assertEquals(5, updated.threshold)
        }

    @Test
    fun `deleteNotification removes it`() =
        runTest {
            repository.notifications.value = listOf(oftenNotification(id = 1L), oftenNotification(id = 2L))

            viewModel().deleteNotification(notificationId = 1L)

            assertEquals(listOf(2L), repository.notifications.value.map { it.id })
        }

    @Test
    fun `notificationRows maps entities to rows with calendar-day-aware firedDaysAgo`() {
        val now = clock.nowMillis()
        val rows =
            notificationRows(
                notifications = listOf(oftenNotification(id = 1L, lastFiredAt = now - 2 * MILLIS_PER_DAY)),
                nowMillis = now,
            )

        assertEquals(2L, rows.single().firedDaysAgo)
    }
}
