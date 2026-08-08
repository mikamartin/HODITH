package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.domain.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val settingsRepository = FakeSettingsRepository()
    private val clock = FakeClock(1_000_000L)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AboutViewModel(settingsRepository, clock)

    /** Taps out a correctly timed 3-pause-4 pattern: three quick taps, a mid-window pause, four quick taps. */
    private fun AboutViewModel.tapCorrectPattern() {
        repeat(3) {
            onVersionTapped()
            clock.advanceBy(100L)
        }
        clock.advanceBy(1_500L)
        repeat(4) {
            onVersionTapped()
            clock.advanceBy(100L)
        }
    }

    @Test
    fun `first few taps produce no events and do not unlock`() =
        runTest {
            val viewModel = viewModel()

            repeat(3) {
                viewModel.onVersionTapped()
                clock.advanceBy(100L)
            }

            assertFalse(settingsRepository.developerModeUnlocked.value)
        }

    @Test
    fun `correctly timed pattern unlocks developer mode and persists it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                viewModel.tapCorrectPattern()

                assertTrue(awaitItem() is DeveloperModeUnlockEvent.Unlocked)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(settingsRepository.developerModeUnlocked.value)
        }

    @Test
    fun `pause that is too short does not unlock`() =
        runTest {
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                repeat(3) {
                    viewModel.onVersionTapped()
                    clock.advanceBy(100L)
                }
                clock.advanceBy(100L) // shorter than the minimum pause
                repeat(4) {
                    viewModel.onVersionTapped()
                    clock.advanceBy(100L)
                }

                expectNoEvents()
            }
            assertFalse(settingsRepository.developerModeUnlocked.value)
        }

    @Test
    fun `pause that is too long does not unlock`() =
        runTest {
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                repeat(3) {
                    viewModel.onVersionTapped()
                    clock.advanceBy(100L)
                }
                clock.advanceBy(10_000L) // longer than the maximum pause
                repeat(4) {
                    viewModel.onVersionTapped()
                    clock.advanceBy(100L)
                }

                expectNoEvents()
            }
            assertFalse(settingsRepository.developerModeUnlocked.value)
        }

    @Test
    fun `taps too slow within a burst reset the attempt`() =
        runTest {
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                viewModel.onVersionTapped()
                clock.advanceBy(5_000L) // longer than the max gap within a burst
                viewModel.onVersionTapped()
                clock.advanceBy(100L)
                viewModel.onVersionTapped()

                assertFalse(settingsRepository.developerModeUnlocked.value)
                expectNoEvents()
            }
        }

    @Test
    fun `further taps after unlock do nothing`() =
        runTest {
            settingsRepository.developerModeUnlocked.value = true
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                viewModel.onVersionTapped()
                expectNoEvents()
            }
        }
}
