package com.secondmonday.hodith

import android.app.Application
import com.secondmonday.hodith.widget.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HodithApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.enqueue(this)
    }
}
