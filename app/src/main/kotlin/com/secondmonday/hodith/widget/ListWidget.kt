package com.secondmonday.hodith.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.homeCaseRows
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

private object WidgetPalette {
    val background = Color(0xFF1C1C1E)
    val surface = Color(0xFF2C2C2E)
    val onSurface = Color(0xFFF2F2F7)
    val onSurfaceMuted = Color(0xFFA0A0A5)
    val accent = Color(0xFF64D2FF)
    val stopBackground = Color(0xFFFF9F0A)
    val onStopBackground = Color(0xFF1C1C1E)
}

private val CaseIdParam = ActionParameters.Key<Long>(EXTRA_CASE_ID)
private val EventIdParam = ActionParameters.Key<Long>("com.secondmonday.hodith.widget.EXTRA_EVENT_ID")

class QuickLogAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val caseId = parameters[CaseIdParam] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val now = entryPoint.clock().nowMillis()
        // endedAt always null here, matching HomeViewModel.quickLogOneTap: a ONE_TAP case with
        // durationMode START_STOP starts an ongoing event rather than an instantaneous one.
        entryPoint.repository().insertEvent(
            EventEntity(caseId = caseId, occurredAt = now, endedAt = null, intensity = null, note = null, loggedAt = now),
        )
        ListWidget().updateAll(context)
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
        ListWidget().updateAll(context)
    }
}

class ListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val casesWithEvents = entryPoint.repository().observeActiveCasesWithEvents().first()
        val now = entryPoint.clock().nowMillis()
        val rows = homeCaseRows(casesWithEvents.filter { it.case.pinned }, now)

        provideContent {
            GlanceTheme {
                Column(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(WidgetPalette.background)
                            .padding(12.dp),
                ) {
                    // Widget copy is pinned to PlainVoice regardless of the user's chosen in-app
                    // theme — mirrors the widget's fixed neutral palette (DEV_PLAYBOOK.md §4):
                    // colors already don't follow theme here, so reading Settings/DataStore just
                    // to swap text and not color would be inconsistent theming, not more of it.
                    Text(
                        text = PlainVoice.homeHeaderTitle,
                        style =
                            TextStyle(
                                color = ColorProvider(WidgetPalette.onSurfaceMuted),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        modifier = GlanceModifier.padding(bottom = 8.dp),
                    )
                    if (rows.isEmpty()) {
                        Text(
                            text = PlainVoice.widgetNoPinnedCasesMessage,
                            style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = 13.sp),
                        )
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                            items(rows) { row -> CaseRow(row, now) }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.glance.appwidget.lazy.LazyListScope.items(
    rows: List<HomeCaseRow>,
    itemContent: @Composable (HomeCaseRow) -> Unit,
) {
    items(count = rows.size, itemId = { rows[it].caseId }) { index ->
        itemContent(rows[index])
    }
}

/** Minimum tappable target on any axis (Android accessibility guidance) — the widget's compact
 * rows would otherwise size the "+" and "Stop" tap targets to their text alone. */
private val MinTapTarget = 48.dp

@Composable
private fun CaseRow(
    row: HomeCaseRow,
    now: Long,
) {
    val ongoing = row.ongoingEvent
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(WidgetPalette.surface)
                .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(text = row.icon, style = TextStyle(fontSize = 20.sp))
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.name,
                maxLines = 1,
                style =
                    TextStyle(
                        color = ColorProvider(WidgetPalette.onSurface),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            Text(
                text =
                    if (ongoing != null) {
                        PlainVoice.ongoingIndicator(formatElapsedDuration(ongoing.occurredAt, now))
                    } else {
                        PlainVoice.widgetTodayCount(row.todayCount)
                    },
                style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = 12.sp),
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        if (ongoing != null) {
            Box(
                modifier =
                    GlanceModifier
                        .height(MinTapTarget)
                        .background(WidgetPalette.stopBackground)
                        .cornerRadius(8.dp)
                        .clickable(actionRunCallback<StopEventAction>(actionParametersOf(EventIdParam to ongoing.id))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = PlainVoice.widgetStopAction,
                    style =
                        TextStyle(
                            color = ColorProvider(WidgetPalette.onStopBackground),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = GlanceModifier.padding(horizontal = 14.dp),
                )
            }
        } else {
            val context = LocalContext.current
            val tapAction =
                when (row.logFlow) {
                    LogFlow.ONE_TAP -> actionRunCallback<QuickLogAction>(actionParametersOf(CaseIdParam to row.caseId))
                    LogFlow.DETAIL_SHEET ->
                        actionStartActivity(
                            intent =
                                Intent(context, WidgetLogTrampolineActivity::class.java)
                                    .putExtra(EXTRA_CASE_ID, row.caseId),
                        )
                }
            Box(
                modifier =
                    GlanceModifier
                        .size(MinTapTarget)
                        .clickable(tapAction)
                        .semantics { contentDescription = PlainVoice.quickLogButtonDescription(row.name) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style =
                        TextStyle(
                            color = ColorProvider(WidgetPalette.accent),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
    }
}
