package com.secondmonday.hodith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import com.secondmonday.hodith.ui.HodithApp
import com.secondmonday.hodith.widget.EXTRA_CASE_ID
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var notificationPermissionRequestSignal: NotificationPermissionRequestSignal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLinkCaseId = intent.getLongExtra(EXTRA_CASE_ID, -1L).takeIf { it != -1L }
        setContent {
            HodithApp(
                themeFlow = settingsRepository.observeTheme(),
                notificationPermissionRequests = notificationPermissionRequestSignal.events,
                deepLinkCaseId = deepLinkCaseId,
            )
        }
    }
}
