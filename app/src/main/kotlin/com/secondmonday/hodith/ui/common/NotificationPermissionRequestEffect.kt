package com.secondmonday.hodith.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/**
 * Spec §11: POST_NOTIFICATIONS is requested once — the ViewModel decides *when* (first Trigger
 * created, first check-in enabled) and marks it requested; this just shows the system dialog when
 * told to. Pre-13 devices have no such runtime permission, so there's nothing to launch.
 */
@Composable
fun NotificationPermissionRequestEffect(events: Flow<Unit>) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(events) {
        events.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
