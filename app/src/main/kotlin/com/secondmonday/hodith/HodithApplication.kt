package com.secondmonday.hodith

import android.app.Application
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.notification.NotificationEvalWorker
import com.secondmonday.hodith.notification.ensureNotificationChannel
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.widget.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HodithApplication : Application() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.enqueue(this)
        NotificationEvalWorker.enqueue(this)
        applicationScope.launch {
            ensureNotificationChannel(this@HodithApplication, voiceFor(settingsRepository.observeTheme().first()))
        }
    }
}
