package com.secondmonday.hodith.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.secondmonday.hodith.data.quickLogEvent
import com.secondmonday.hodith.ui.theme.PlainLightBackground
import com.secondmonday.hodith.ui.theme.PlainLightError
import com.secondmonday.hodith.ui.theme.PlainLightOnError
import com.secondmonday.hodith.ui.theme.PlainLightOnPrimary
import com.secondmonday.hodith.ui.theme.PlainLightOnSurface
import com.secondmonday.hodith.ui.theme.PlainLightOnSurfaceVariant
import com.secondmonday.hodith.ui.theme.PlainLightPrimary
import com.secondmonday.hodith.ui.theme.PlainLightSurface
import dagger.hilt.android.EntryPointAccessors

/**
 * Shared between [ListWidget] and [SingleCaseWidget] — both render the Plain theme's light
 * palette (`plainLight` in `ui/theme/Color.kt`) regardless of the user's chosen in-app theme or
 * system light/dark mode (DEV_PLAYBOOK.md §4), pulled out of [ListWidget] so [SingleCaseWidget]
 * could reuse them unchanged.
 */
internal object WidgetPalette {
    val background = PlainLightBackground
    val surface = PlainLightSurface
    val onSurface = PlainLightOnSurface
    val onSurfaceMuted = PlainLightOnSurfaceVariant
    val accent = PlainLightPrimary
    val onAccent = PlainLightOnPrimary
    val stopBackground = PlainLightError
    val onStopBackground = PlainLightOnError
}

/** Minimum tappable target on any axis (Android accessibility guidance) — the widgets' compact
 * rows would otherwise size the "+" and "Stop" tap targets to their text alone. */
internal val MinTapTarget = 48.dp

/** App widget corner radius — matches the system-drawn corner mask on Android 12+ hosts. */
internal val WidgetCornerRadius = 16.dp

internal val CaseIdParam = ActionParameters.Key<Long>(EXTRA_CASE_ID)
internal val EventIdParam = ActionParameters.Key<Long>("com.secondmonday.hodith.widget.EXTRA_EVENT_ID")

/** Refreshes every Glance widget type — a log/stop on one widget keeps the other in sync if the
 * same Case happens to be shown in both. Called from [WidgetRefreshWorker.doWork] only: an
 * `ActionCallback` or configure activity can't reliably push its own widget id's next update from
 * within its own click/configure transaction (see [WidgetRefreshWorker]'s doc), so every other
 * caller goes through [WidgetRefreshWorker.enqueueRefresh] instead of calling this directly. */
internal suspend fun refreshAllWidgets(context: Context) {
    ListWidget().updateAll(context)
    SingleCaseWidget().updateAll(context)
}

class QuickLogAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val caseId = parameters[CaseIdParam] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        entryPoint.repository().insertEvent(quickLogEvent(caseId = caseId, now = entryPoint.clock().nowMillis()))
        WidgetRefreshWorker.enqueueRefresh(context)
    }
}

class StopEventAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val eventId = parameters[EventIdParam] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repository = entryPoint.repository()
        val event = repository.getEvent(eventId) ?: return
        repository.updateEvent(event.copy(endedAt = entryPoint.clock().nowMillis()))
        WidgetRefreshWorker.enqueueRefresh(context)
    }
}
