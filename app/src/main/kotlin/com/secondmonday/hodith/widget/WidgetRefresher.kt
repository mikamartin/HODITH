package com.secondmonday.hodith.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Abstracts triggering a List widget refresh, the same way [com.secondmonday.hodith.domain.Clock]
 * abstracts real time — so ViewModels that need to refresh the widget after a `pinned`/`archived`
 * change (Case edit, archive, unarchive, delete forever) stay unit-testable on the JVM without a
 * real `Context` or Glance.
 */
interface WidgetRefresher {
    suspend fun refreshListWidget()
}

class GlanceWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        override suspend fun refreshListWidget() {
            ListWidget().updateAll(context)
        }
    }
