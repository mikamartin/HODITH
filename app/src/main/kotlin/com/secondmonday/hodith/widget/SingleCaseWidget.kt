package com.secondmonday.hodith.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.secondmonday.hodith.MainActivity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.homeCaseRowsFlow
import dagger.hilt.android.EntryPointAccessors

/** Per-instance Glance state key — each Single-case widget placement is bound to its own Case, so
 * the binding lives in this widget's own [PreferencesGlanceStateDefinition] state rather than on
 * [com.secondmonday.hodith.data.CaseEntity] (mirroring [com.secondmonday.hodith.widget.CaseIdsKey],
 * the List widget's equivalent for a set of Cases). */
internal val CaseIdKey = longPreferencesKey("case_id")

class SingleCaseWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        provideContent {
            // Read reactively via currentState()/produceState rather than one-shot suspend reads
            // outside provideContent: Android binds a widget before its configure Activity even
            // runs, so Glance's session (and this composable) can already be alive with no Case
            // bound yet by the time finishConfigure() writes CaseIdKey. A value captured outside
            // provideContent is frozen for that session's lifetime — calling update() afterward
            // just reapplies the same frozen output, since there's no Compose State for it to
            // recompose against. Reading live state here means the composable recomposes itself
            // whenever CaseIdKey or the underlying Case/event data actually changes.
            val boundCaseId = currentState<Preferences>()[CaseIdKey]
            val now = entryPoint.clock().nowMillis()
            val repository = entryPoint.repository()
            val row by
                produceState<HomeCaseRow?>(initialValue = null, boundCaseId) {
                    if (boundCaseId == null) {
                        value = null
                    } else {
                        homeCaseRowsFlow(repository) { now }.collect { allRows ->
                            value = allRows.firstOrNull { it.caseId == boundCaseId }
                        }
                    }
                }

            GlanceTheme {
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(WidgetPalette.background)
                            .cornerRadius(WidgetCornerRadius)
                            .appWidgetBackground(),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentRow = row
                    if (currentRow == null) {
                        SingleCaseMissingContent(appWidgetId)
                    } else {
                        SingleCaseContent(currentRow)
                    }
                }
            }
        }
    }
}

class SingleCaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleCaseWidget()
}

/**
 * The Single-case widget's missing-Case state (its bound Case was deleted or archived) — mirrors
 * [SingleCaseContent]'s shape (a non-weighted top zone sized to its own content, then a button
 * filling the rest of the tile) so the layout stays visually consistent between states. Both the
 * "Edit" label and the "✎" icon reopen [SingleCaseWidgetConfigureActivity] — there is no message
 * area and no way to open the app directly in this state; the only action available is
 * reconfiguring.
 */
@Composable
private fun SingleCaseMissingContent(appWidgetId: Int) {
    val context = LocalContext.current
    val openConfigure =
        actionStartActivity(
            Intent(context, SingleCaseWidgetConfigureActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(WidgetSingleCaseOuterPadding),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().clickable(openConfigure),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = PlainVoice.singleCaseWidgetEditLabel,
                style =
                    TextStyle(
                        color = ColorProvider(WidgetPalette.onSurface),
                        fontSize = WidgetIconGlyphSize,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
        Spacer(modifier = GlanceModifier.height(WidgetSingleCaseLogButtonSpacing))
        Box(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(openConfigure)
                    .semantics { contentDescription = PlainVoice.singleCaseWidgetEditButtonDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✎",
                style = TextStyle(color = ColorProvider(WidgetPalette.onSurface), fontSize = WidgetGlyphButtonSize),
            )
        }
    }
}

@Composable
private fun SingleCaseContent(row: HomeCaseRow) {
    val context = LocalContext.current
    val caseDetailIntent =
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_CASE_ID, row.caseId)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(WidgetSingleCaseOuterPadding),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        // Non-weighted and sized to its own content, so a sibling can never squeeze it out of
        // room — unlike the icon+subtitle Column this replaced, which could clip the icon itself
        // when the subtitle demanded more space than the tile's true (non-resizable) size allowed.
        Box(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(intent = caseDetailIntent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = row.icon, style = TextStyle(fontSize = WidgetIconGlyphSize))
        }
        Spacer(modifier = GlanceModifier.height(WidgetSingleCaseLogButtonSpacing))
        // The log button stays put whether or not an event runs (spec §6) — on a running
        // `START_STOP` Case it starts a second one. Stop lives in Case Detail, opened by tapping
        // above.
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
                    .defaultWeight()
                    .background(WidgetPalette.surface)
                    .cornerRadius(WidgetLogButtonCornerRadius)
                    .clickable(tapAction)
                    .semantics { contentDescription = PlainVoice.quickLogButtonDescription(row.name) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style =
                    TextStyle(
                        color = ColorProvider(WidgetPalette.accent),
                        fontSize = WidgetGlyphButtonSize,
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }
    }
}
