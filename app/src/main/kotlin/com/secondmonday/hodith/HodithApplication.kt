package com.secondmonday.hodith

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HodithApplication : Application() {
    @Inject
    lateinit var appInitializers: Set<@JvmSuppressWildcards AppInitializer>

    override fun onCreate() {
        super.onCreate()
        appInitializers.forEach { it.init() }
    }
}
