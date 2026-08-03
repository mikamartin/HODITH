package com.secondmonday.hodith.viewmodel

import app.cash.turbine.test
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.FakeSettingsRepository
import com.secondmonday.hodith.data.backup.BackupSerializer
import com.secondmonday.hodith.data.backup.FakeBackupFileWriter
import com.secondmonday.hodith.data.demo.DemoDataSeeder
import com.secondmonday.hodith.domain.FakeClock
import com.squareup.moshi.Moshi
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
    private val backupSerializer = BackupSerializer(Moshi.Builder().build())
    private val backupFileWriter = FakeBackupFileWriter()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(settingsRepository, hodithRepository, demoDataSeeder, backupSerializer, backupFileWriter)

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
    fun `uiState reflects developer mode unlock state`() =
        runTest {
            settingsRepository.developerModeUnlocked.value = true
            val viewModel = viewModel()

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertTrue(state.developerModeUnlocked)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState reflects the persisted check-in default interval`() =
        runTest {
            settingsRepository.checkInDefaultInterval.value = CheckInDefaultInterval.THIRTY
            val viewModel = viewModel()

            viewModel.uiState.test {
                val state = awaitLoadedItem { it.isLoading }
                assertEquals(CheckInDefaultInterval.THIRTY, state.checkInDefaultInterval)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onCheckInDefaultIntervalSelect persists the new interval`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onCheckInDefaultIntervalSelect(CheckInDefaultInterval.OFF)

            assertEquals(CheckInDefaultInterval.OFF, settingsRepository.checkInDefaultInterval.value)
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

    @Test
    fun `performExport then performImport round-trips all data`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loadDemoData()
            val exported = hodithRepository.cases.value
            val json = viewModel.performExport()

            hodithRepository.deleteAllData()
            val result = viewModel.performImport(json)

            assertEquals(BackupEvent.ImportSuccess, result)
            assertEquals(exported, hodithRepository.cases.value)
        }

    @Test
    fun `performImport rejects malformed JSON without touching existing data`() =
        runTest {
            val viewModel = viewModel()
            viewModel.loadDemoData()
            val casesBefore = hodithRepository.cases.value

            val result = viewModel.performImport("not json")

            assertEquals(BackupEvent.ImportFailure(ImportFailureReason.INVALID), result)
            assertEquals(casesBefore, hodithRepository.cases.value)
        }

    @Test
    fun `performImport rejects an unsupported schema version`() =
        runTest {
            val viewModel = viewModel()
            val json = """{"schemaVersion":99,"cases":[],"tags":[],"events":[],"eventTags":[],"hunches":[],"triggers":[]}"""

            val result = viewModel.performImport(json)

            assertEquals(BackupEvent.ImportFailure(ImportFailureReason.UNSUPPORTED_VERSION), result)
        }
}
