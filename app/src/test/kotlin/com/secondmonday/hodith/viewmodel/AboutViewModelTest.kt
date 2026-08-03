package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val settingsRepository = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AboutViewModel(settingsRepository)

    @Test
    fun `first few taps produce no events and do not unlock`() =
        runTest {
            val viewModel = viewModel()

            repeat(3) { viewModel.onVersionTapped() }

            assertFalse(settingsRepository.developerModeUnlocked.value)
        }

    @Test
    fun `taps within the countdown window emit decreasing remaining counts`() =
        runTest {
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                repeat(6) { viewModel.onVersionTapped() }

                assertEquals(DeveloperModeUnlockEvent.Countdown(3), awaitItem())
                assertEquals(DeveloperModeUnlockEvent.Countdown(2), awaitItem())
                assertEquals(DeveloperModeUnlockEvent.Countdown(1), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `seventh tap unlocks developer mode and persists it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.unlockEvents.test {
                repeat(7) { viewModel.onVersionTapped() }

                repeat(3) { awaitItem() } // the countdown events from taps 4-6
                assertEquals(DeveloperModeUnlockEvent.Unlocked, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(settingsRepository.developerModeUnlocked.value)
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
