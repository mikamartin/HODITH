package com.secondmonday.hodith.widget

import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * [androidx.glance.appwidget.GlanceAppWidget] and its
 * [androidx.glance.appwidget.action.ActionCallback]s aren't Android framework components, so Hilt
 * can't inject them directly the way it does an `Activity` or `ViewModel` — this is the standard
 * Hilt workaround, pulling singletons out of the `Application`'s component by hand.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): HodithRepository

    fun clock(): Clock
}
