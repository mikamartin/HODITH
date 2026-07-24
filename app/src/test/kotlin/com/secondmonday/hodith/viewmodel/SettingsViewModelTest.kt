package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.demo.DemoDataSeeder
import com.secondmonday.hodith.domain.FakeClock
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
class SettingsViewModelTest {
    private val hodithRepository = FakeHodithRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val demoDataSeeder = DemoDataSeeder(hodithRepository, FakeClock())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(settingsRepository, hodithRepository, demoDataSeeder)

    @Test
    fun `uiState reflects the persisted theme`() =
        runTest {
            settingsRepository.theme.value = AppTheme.INTENSE
            val viewModel = viewModel()

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(AppTheme.INTENSE, state.theme)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onThemeSelect persists the new theme`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onThemeSelect(AppTheme.BRIGHT)

            assertEquals(AppTheme.BRIGHT, settingsRepository.theme.value)
        }

    @Test
    fun `loadDemoData inserts seed cases and signals completion`() =
        runTest {
            val viewModel = viewModel()

            viewModel.demoDataLoaded.test {
                viewModel.loadDemoData()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(hodithRepository.cases.value.isNotEmpty())
        }

    @Test
    fun `deleteAllData clears every case`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loadDemoData()

            viewModel.deleteAllData()

            assertTrue(hodithRepository.cases.value.isEmpty())
            assertTrue(hodithRepository.events.value.isEmpty())
            assertTrue(hodithRepository.tags.value.isEmpty())
        }
}
