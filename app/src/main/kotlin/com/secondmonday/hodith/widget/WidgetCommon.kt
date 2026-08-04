package com.secondmonday.hodith.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.secondmonday.hodith.data.quickLogEvent
import dagger.hilt.android.EntryPointAccessors

/**
 * Shared between [ListWidget] and [SingleCaseWidget] — both render the same fixed neutral chrome
 * (DEV_PLAYBOOK.md §4: Glance theming is limited, so widgets don't follow the in-app theme) and
 * log/stop actions, pulled out of [ListWidget] so [SingleCaseWidget] could reuse them unchanged.
 */
internal object WidgetPalette {
    val background = Color(0xFF1C1C1E)
    val surface = Color(0xFF2C2C2E)
    val onSurface = Color(0xFFF2F2F7)
    val onSurfaceMuted = Color(0xFFA0A0A5)
    val accent = Color(0xFF64D2FF)
    val stopBackground = Color(0xFFFF9F0A)
    val onStopBackground = Color(0xFF1C1C1E)
}

/** Minimum tappable target on any axis (Android accessibility guidance) — the widgets' compact
 * rows would otherwise size the "+" and "Stop" tap targets to their text alone. */
internal val MinTapTarget = 48.dp

/** App widget corner radius — matches the system-drawn corner mask on Android 12+ hosts. */
internal val WidgetCornerRadius = 16.dp

internal val CaseIdParam = ActionParameters.Key<Long>(EXTRA_CASE_ID)
internal val EventIdParam = ActionParameters.Key<Long>("com.secondmonday.hodith.widget.EXTRA_EVENT_ID")

/** Refreshes every Glance widget type after a data change — a log/stop on one widget keeps the
 * other in sync if the same Case happens to be shown in both. */
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
        refreshAllWidgets(context)
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
        refreshAllWidgets(context)
    }
}
