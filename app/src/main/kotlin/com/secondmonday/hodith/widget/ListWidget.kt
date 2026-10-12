package com.secondmonday.hodith.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.formatElapsedDuration
import com.secondmonday.hodith.viewmodel.formatExpectedEndTime
import com.secondmonday.hodith.viewmodel.homeCaseRowsFlow
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
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        provideContent {
            // Read reactively via currentState()/produceState rather than one-shot suspend reads
            // outside provideContent — see SingleCaseWidget's provideGlance for why: a value
            // captured outside provideContent is frozen for the session's lifetime once Android's
            // bind-before-configure sequence starts an early session, and update() calls
            // afterward can't force a recompute against something that isn't Compose State.
            val selectedCaseIds = currentState<Preferences>()[CaseIdsKey].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
            val now = entryPoint.clock().nowMillis()
            val repository = entryPoint.repository()
            val rows by
                produceState(initialValue = emptyList<HomeCaseRow>(), selectedCaseIds) {
                    homeCaseRowsFlow(repository) { now }.collect { allRows ->
                        value = allRows.filter { it.caseId in selectedCaseIds }
                    }
                }
            val use24Hour by rememberWidgetUse24Hour(entryPoint)

            GlanceTheme {
                val context = LocalContext.current
                val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
                val openConfigure =
                    actionStartActivity(
                        Intent(context, ListWidgetConfigureActivity::class.java)
                            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                    )
                Column(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(WidgetPalette.background)
                            .cornerRadius(WidgetCornerRadius)
                            .appWidgetBackground()
                            .padding(WidgetOuterPadding),
                ) {
                    // Widget copy is pinned to PlainVoice regardless of the user's chosen in-app
                    // theme — mirrors the widget's fixed neutral palette (DEV_PLAYBOOK.md §4):
                    // colors already don't follow theme here, so reading Settings/DataStore just
                    // to swap text and not color would be inconsistent theming, not more of it.
                    //
                    // The title Text keeps its own `.padding(vertical = WidgetHeaderPadding)`
                    // inside `.clickable()` (rather than moving that padding onto the Row), so its
                    // tap target stays exactly what WidgetHeaderPadding's own doc comment already
                    // signs off on (~37dp — a deliberate below-[MinTapTarget] trade for this one
                    // control) instead of shrinking further to just its bare text line height. The
                    // edit icon Box is sized at [MinTapTarget] on its own and needs no padding;
                    // the Row's `CenterVertically` lines the two up despite their different
                    // heights. `.defaultWeight()` on the title, the same weight-inside-a-Row shape
                    // CaseRow's name Column uses, grows it to fill the Row's remaining width (so it
                    // still wraps instead of clipping on a narrow widget) without claiming the
                    // icon's space. The icon is always present, in both the empty and populated
                    // states, since the header renders above the `rows.isEmpty()` branch — it's
                    // the widget's one persistent way back into the Case picker, since nothing
                    // else in a populated widget reopens it.
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                    ) {
                        Text(
                            text = PlainVoice.homeHeaderTitle,
                            style =
                                TextStyle(
                                    color = ColorProvider(WidgetPalette.onSurface),
                                    fontSize = WidgetHeaderTitleSize,
                                    fontWeight = WidgetHeaderTitleWeight,
                                ),
                            modifier =
                                GlanceModifier
                                    .defaultWeight()
                                    .padding(vertical = WidgetHeaderPadding)
                                    .clickable(openApp),
                        )
                        Spacer(modifier = GlanceModifier.width(WidgetIconTextSpacing))
                        Box(
                            modifier =
                                GlanceModifier
                                    .size(MinTapTarget)
                                    .clickable(openConfigure)
                                    .semantics { contentDescription = PlainVoice.widgetEditButtonDescription },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "✎",
                                style = TextStyle(color = ColorProvider(WidgetPalette.onSurface), fontSize = WidgetGlyphButtonSize),
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            text = PlainVoice.widgetNoCasesSelectedMessage,
                            style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = WidgetInfoMessageSize),
                            modifier =
                                GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = WidgetHeaderPadding)
                                    .clickable(openConfigure),
                        )
                    } else {
                        Spacer(modifier = GlanceModifier.height(WidgetPlankSpacing))
                        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                            items(rows) { index, row -> CaseRow(row, now, use24Hour, isLast = index == rows.lastIndex) }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.glance.appwidget.lazy.LazyListScope.items(
    rows: List<HomeCaseRow>,
    itemContent: @Composable (index: Int, row: HomeCaseRow) -> Unit,
) {
    items(count = rows.size, itemId = { rows[it].caseId }) { index ->
        itemContent(index, rows[index])
    }
}

@Composable
private fun CaseRow(
    row: HomeCaseRow,
    now: Long,
    use24Hour: Boolean,
    isLast: Boolean,
) {
    val context = LocalContext.current
    val caseDetailIntent =
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_CASE_ID, row.caseId)
    // Column wrapper + trailing Spacer, not a bottom margin: Glance has no margin — every `padding`
    // is interior and `background` fills it — so the gap that makes each row read as a discrete
    // plank has to be a real (transparent) Spacer between the LazyColumn items.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(WidgetPalette.surface)
                    .cornerRadius(WidgetCornerRadius)
                    .padding(WidgetRowPadding),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(text = row.icon, style = TextStyle(fontSize = WidgetIconGlyphSize))
            Spacer(modifier = GlanceModifier.width(WidgetIconTextSpacing))
            Column(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(intent = caseDetailIntent)),
            ) {
                Text(
                    text = row.name,
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = ColorProvider(WidgetPalette.onSurface),
                            fontSize = WidgetCaseNameSize,
                            fontWeight = FontWeight.Medium,
                        ),
                )
                WidgetCaseSubtitle(row = row, now = now, use24Hour = use24Hour)
            }
            Spacer(modifier = GlanceModifier.width(WidgetLogButtonSpacing))
            // The log button stays put whether or not an event runs (spec §6) — on a running
            // `START_STOP` Case it starts a second one. Stop lives in Case Detail, opened by tapping
            // the row.
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
                            fontSize = WidgetGlyphButtonSize,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
        if (!isLast) {
            Spacer(modifier = GlanceModifier.height(WidgetPlankSpacing))
        }
    }
}

/**
 * A widget Case row's line-2: the "Ongoing" pill + live elapsed (one running event) or a count
 * (several), matching the in-app treatment (spec §6); the neutral today-count when nothing runs.
 * Shared by [ListWidget] and [SingleCaseWidget]. Widgets always render [PlainVoice] (DEV_PLAYBOOK.md §4).
 */
@Composable
internal fun WidgetCaseSubtitle(
    row: HomeCaseRow,
    now: Long,
    use24Hour: Boolean,
) {
    val ongoing = row.ongoingEvent
    if (ongoing == null) {
        Text(
            text = PlainVoice.widgetTodayCount(row.todayCount),
            style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = WidgetSubtitleSize),
        )
        return
    }
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(
            modifier =
                GlanceModifier
                    .background(WidgetPalette.accentContainer)
                    .cornerRadius(WidgetPillCornerRadius)
                    .padding(horizontal = WidgetPillPaddingHorizontal, vertical = WidgetPillPaddingVertical),
        ) {
            Text(
                text = PlainVoice.ongoingPillLabel,
                style =
                    TextStyle(
                        color = ColorProvider(WidgetPalette.onAccentContainer),
                        fontSize = WidgetPillTextSize,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text =
                when {
                    row.runningCount >= 2 -> PlainVoice.ongoingCountIndicator(row.runningCount)
                    ongoing.durationMode == DurationMode.START_STOP -> formatElapsedDuration(ongoing.occurredAt, now)
                    else ->
                        PlainVoice.ongoingExpectedEndTrailing(
                            formatExpectedEndTime(ongoing.endedAt!!, now, use24Hour),
                        )
                },
            style = TextStyle(color = ColorProvider(WidgetPalette.onSurfaceMuted), fontSize = WidgetSubtitleSize),
        )
    }
}
