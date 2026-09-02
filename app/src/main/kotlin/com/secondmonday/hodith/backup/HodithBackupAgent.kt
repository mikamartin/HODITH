package com.secondmonday.hodith.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.secondmonday.hodith.data.DataStoreSettingsRepository
import com.secondmonday.hodith.di.DataStoreModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * `allowBackup`/`dataExtractionRules` are static manifest flags the OS reads once at install
 * time, so the Settings cloud-backup toggle is enforced here instead: skip the full-data backup
 * pass entirely when the user has opted out. Governs both cloud backup and device-transfer, since
 * both route through [onFullBackup] on API 31+ — there's no way to tell them apart here.
 *
 * The OS instantiates this class via reflection, outside the Hilt graph, so it builds its own
 * [DataStoreSettingsRepository] directly rather than via injection — the same constraint every
 * framework-instantiated component in this codebase (widgets, receivers) already works around.
 */
class HodithBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        val settingsRepository =
            DataStoreSettingsRepository(
                DataStoreModule.provideSettingsDataStore(applicationContext),
                applicationContext,
            )
        val backupEnabled = runBlocking { settingsRepository.observeCloudBackupEnabled().first() }
        if (backupEnabled) {
            super.onFullBackup(data)
        }
    }

    // Unused: the app relies on full-data backup (onFullBackup) rather than key/value backup.
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit
}
