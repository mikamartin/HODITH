package com.secondmonday.hodith.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Abstracts triggering a widget refresh, the same way [com.secondmonday.hodith.domain.Clock]
 * abstracts real time — so ViewModels that need to refresh widgets after a `pinned`/`archived`
 * change (Case edit, archive, unarchive, delete forever) or a widget-triggered log
 * ([com.secondmonday.hodith.viewmodel.WidgetLogSheetViewModel]) stay unit-testable on the JVM
 * without a real `Context` or Glance. Refreshes every widget type ([ListWidget] and
 * [SingleCaseWidget]) — either could be showing the affected Case.
 */
interface WidgetRefresher {
    suspend fun refreshWidgets()
}

class GlanceWidgetRefresher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        // Enqueues rather than calling refreshAllWidgets(context) directly — see
        // WidgetRefreshWorker's doc for why a refresh triggered from the same widget's own
        // click/configure transaction needs a separate execution context to actually stick.
        override suspend fun refreshWidgets() {
            WidgetRefreshWorker.enqueueRefresh(context)
        }
    }
