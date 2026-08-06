package com.secondmonday.hodith.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.secondmonday.hodith.MainActivity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.homeCaseRows
import dagger.hilt.android.EntryPointAccessors

/** Per-instance Glance state key — each List widget placement picks its own set of Cases, so the
 * selection lives in this widget's own [PreferencesGlanceStateDefinition] state rather than on
 * [com.secondmonday.hodith.data.CaseEntity]. No native `Set<Long>` Preferences type exists, so ids
 * are stored as strings. */
internal val CaseIdsKey = stringSetPreferencesKey("case_ids")

class ListWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

        provideContent {
            // Read reactively via currentState()/produceState rather than one-shot suspend reads
            // outside provideContent — see SingleCaseWidget's provideGlance for why: a value
            // captured outside provideContent is frozen for the session's lifetime once Android's
            // bind-before-configure sequence starts an early session, and update() calls
            // afterward can't force a recompute against something that isn't Compose State.
            val selectedCaseIds = currentState<Preferences>()[CaseIdsKey].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
            val now = entryPoint.clock().nowMillis()
            val rows by
                produceState(initialValue = emptyList<HomeCaseRow>(), selectedCaseIds) {
                    entryPoint.repository().observeActiveCasesWithEvents().collect { casesWithEvents ->
                        value = homeCaseRows(casesWithEvents.filter { it.case.id in selectedCaseIds }, now)
                    }
                }

            GlanceTheme {
                val context = LocalContext.current
                Column(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(WidgetPalette.background)
                            .cornerRadius(WidgetCornerRadius)
                            .appWidgetBackground()
                            .padding(12.dp),
                ) {
                    // Widget copy is pinned to PlainVoice regardless of the user's chosen in-app
                    // theme — mirrors the widget's fixed neutral palette (DEV_PLAYBOOK.md §4):
                    // colors already don't follow theme here, so reading Settings/DataStore just
                    // to swap text and not color would be inconsistent theming, not more of it.
                    Box(
                        modifier =
                            GlanceModifier
                                .fillMaxWidth()
                                .height(MinTapTarget)
                                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = PlainVoice.homeHeaderTitle,
                            style =
                                TextStyle(
                                    color = ColorProvider(WidgetPalette.onSurfaceMuted),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        )
                    }
                    if (rows.isEmpty()) {
                        Box(
                            modifier =
                                GlanceModifier
                                    .fillMaxWidth()
                                    .height(MinTapTarget)
                                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = PlainVoice.widgetNoCasesSelectedMessage,
                                style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = 13.sp),
                            )
                        }
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

@Composable
private fun CaseRow(
    row: HomeCaseRow,
    now: Long,
) {
    val ongoing = row.ongoingEvent
    val context = LocalContext.current
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
        Column(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .clickable(
                        actionStartActivity(
                            intent =
                                Intent(context, MainActivity::class.java)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    .putExtra(EXTRA_CASE_ID, row.caseId),
                        ),
                    ),
        ) {
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
