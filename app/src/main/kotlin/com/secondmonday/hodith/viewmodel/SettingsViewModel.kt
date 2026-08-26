package com.secondmonday.hodith.viewmodel

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CheckInDefaultInterval
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.backup.BACKUP_SCHEMA_VERSION
import com.secondmonday.hodith.data.backup.BackupFileWriter
import com.secondmonday.hodith.data.backup.BackupSerializer
import com.secondmonday.hodith.data.demo.DemoDataSeeder
import com.squareup.moshi.JsonDataException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.PLAIN,
    val checkInDefaultInterval: CheckInDefaultInterval = CheckInDefaultInterval.SEVEN,
    val developerModeUnlocked: Boolean = false,
    val cloudBackupEnabled: Boolean = true,
    val isLoading: Boolean = true,
)

enum class ImportFailureReason { INVALID, UNSUPPORTED_VERSION, IO_ERROR, SEMANTIC_INVALID }

sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent

    data object ExportFailure : BackupEvent

    data object ImportSuccess : BackupEvent

    data class ImportFailure(
        val reason: ImportFailureReason,
    ) : BackupEvent
}

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val hodithRepository: HodithRepository,
        private val demoDataSeeder: DemoDataSeeder,
        private val backupSerializer: BackupSerializer,
        private val backupFileWriter: BackupFileWriter,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                settingsRepository.observeTheme(),
                settingsRepository.observeCheckInDefaultInterval(),
                settingsRepository.observeDeveloperModeUnlocked(),
                settingsRepository.observeCloudBackupEnabled(),
            ) { theme, checkInDefaultInterval, developerModeUnlocked, cloudBackupEnabled ->
                SettingsUiState(
                    theme = theme,
                    checkInDefaultInterval = checkInDefaultInterval,
                    developerModeUnlocked = developerModeUnlocked,
                    cloudBackupEnabled = cloudBackupEnabled,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SettingsUiState(),
            )

        private val _demoDataLoaded = Channel<Unit>(Channel.BUFFERED)
        val demoDataLoaded: Flow<Unit> = _demoDataLoaded.receiveAsFlow()

        fun onThemeSelect(theme: AppTheme) {
            viewModelScope.launch { settingsRepository.setTheme(theme) }
        }

        fun onCheckInDefaultIntervalSelect(interval: CheckInDefaultInterval) {
            viewModelScope.launch { settingsRepository.setCheckInDefaultInterval(interval) }
        }

        fun onCloudBackupToggle(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setCloudBackupEnabled(enabled) }
        }

        fun loadDemoData() {
            viewModelScope.launch {
                demoDataSeeder.seed()
                _demoDataLoaded.send(Unit)
            }
        }

        fun deleteAllData() {
            viewModelScope.launch { hodithRepository.deleteAllData() }
        }

        private val _backupEvents = Channel<BackupEvent>(Channel.BUFFERED)
        val backupEvents: Flow<BackupEvent> = _backupEvents.receiveAsFlow()

        /** Pure export logic (serialize current data), split out from [exportData]'s Uri/stream handling so it's unit-testable. */
        suspend fun performExport(): String = backupSerializer.toJson(hodithRepository.exportBackupData())

        fun exportData(uri: Uri) {
            viewModelScope.launch {
                try {
                    val json = performExport()
                    val stream = backupFileWriter.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
                    stream.use { it.write(json.toByteArray()) }
                    _backupEvents.send(BackupEvent.ExportSuccess)
                } catch (e: IOException) {
                    _backupEvents.send(BackupEvent.ExportFailure)
                }
            }
        }

        /** Pure import logic (parse, validate, restore), split out from [importData]'s Uri/stream handling so it's unit-testable. */
        suspend fun performImport(json: String): BackupEvent {
            val declaredVersion =
                backupSerializer.peekSchemaVersion(json)
                    ?: return BackupEvent.ImportFailure(ImportFailureReason.INVALID)

            if (declaredVersion > BACKUP_SCHEMA_VERSION) {
                return BackupEvent.ImportFailure(ImportFailureReason.UNSUPPORTED_VERSION)
            }

            val backup =
                try {
                    backupSerializer.fromJson(json, declaredVersion)
                } catch (e: JsonDataException) {
                    return BackupEvent.ImportFailure(ImportFailureReason.INVALID)
                } catch (e: IOException) {
                    return BackupEvent.ImportFailure(ImportFailureReason.INVALID)
                }

            if (!validateBackup(backup).isValid) {
                return BackupEvent.ImportFailure(ImportFailureReason.SEMANTIC_INVALID)
            }

            return try {
                hodithRepository.importBackupData(backup)
                BackupEvent.ImportSuccess
            } catch (e: SQLException) {
                // SQLException, not a broader catch, so CancellationException still propagates.
                BackupEvent.ImportFailure(ImportFailureReason.SEMANTIC_INVALID)
            }
        }

        fun importData(uri: Uri) {
            viewModelScope.launch {
                val json =
                    try {
                        val stream = backupFileWriter.openInputStream(uri) ?: throw IOException("No input stream for $uri")
                        stream.use { it.readBytes().decodeToString() }
                    } catch (e: IOException) {
                        _backupEvents.send(BackupEvent.ImportFailure(ImportFailureReason.IO_ERROR))
                        return@launch
                    }

                _backupEvents.send(performImport(json))
            }
        }
    }
