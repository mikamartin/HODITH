package com.secondmonday.hodith.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.secondmonday.hodith.MainActivity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.homeCaseRows
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/** Per-instance Glance state key — unlike List widget's Case-level `pinned` flag, each Single-case
 * widget placement is bound to its own Case, so the binding lives in this widget's own
 * [PreferencesGlanceStateDefinition] state rather than on [com.secondmonday.hodith.data.CaseEntity]. */
internal val CaseIdKey = longPreferencesKey("case_id")

class SingleCaseWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val boundCaseId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[CaseIdKey]
        val now = entryPoint.clock().nowMillis()
        val row =
            boundCaseId?.let { caseId ->
                val casesWithEvents = entryPoint.repository().observeActiveCasesWithEvents().first()
                homeCaseRows(casesWithEvents.filter { it.case.id == caseId }, now).firstOrNull()
            }

        provideContent {
            GlanceTheme {
                val glanceContext = LocalContext.current
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(WidgetPalette.background)
                            .cornerRadius(WidgetCornerRadius)
                            .appWidgetBackground(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row == null) {
                        Text(
                            text = PlainVoice.widgetCaseNotFoundMessage,
                            style =
                                TextStyle(
                                    color = ColorProvider(WidgetPalette.onSurfaceMuted),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                ),
                            modifier =
                                GlanceModifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clickable(actionStartActivity(Intent(glanceContext, MainActivity::class.java))),
                        )
                    } else {
                        SingleCaseContent(row, now)
                    }
                }
            }
        }
    }
}

class SingleCaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleCaseWidget()
}

@Composable
private fun SingleCaseContent(
    row: HomeCaseRow,
    now: Long,
) {
    val ongoing = row.ongoingEvent
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Column(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .clickable(
                        actionStartActivity(
                            intent =
                                Intent(context, MainActivity::class.java)
                                    .putExtra(EXTRA_CASE_ID, row.caseId),
                        ),
                    ),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(text = row.icon, style = TextStyle(fontSize = 24.sp))
            Text(
                text =
                    if (ongoing != null) {
                        formatElapsedDuration(ongoing.occurredAt, now)
                    } else {
                        PlainVoice.widgetTodayCount(row.todayCount)
                    },
                style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = 11.sp, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        if (ongoing != null) {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        } else {
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
                        .fillMaxWidth()
                        .height(MinTapTarget)
                        .background(WidgetPalette.surface)
                        .cornerRadius(8.dp)
                        .clickable(tapAction)
                        .semantics { contentDescription = PlainVoice.quickLogButtonDescription(row.name) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style =
                        TextStyle(
                            color = ColorProvider(WidgetPalette.accent),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
    }
}
