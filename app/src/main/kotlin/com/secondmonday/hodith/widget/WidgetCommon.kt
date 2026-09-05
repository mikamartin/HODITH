package com.secondmonday.hodith.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.text.FontWeight
import com.secondmonday.hodith.data.quickLogEvent
import com.secondmonday.hodith.ui.theme.PlainLightBackground
import com.secondmonday.hodith.ui.theme.PlainLightOnPrimary
import com.secondmonday.hodith.ui.theme.PlainLightOnPrimaryContainer
import com.secondmonday.hodith.ui.theme.PlainLightOnSurface
import com.secondmonday.hodith.ui.theme.PlainLightOnSurfaceVariant
import com.secondmonday.hodith.ui.theme.PlainLightPrimary
import com.secondmonday.hodith.ui.theme.PlainLightPrimaryContainer
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
    val accentContainer = PlainLightPrimaryContainer
    val onAccentContainer = PlainLightOnPrimaryContainer
}

/** Minimum tappable target on any axis (Android accessibility guidance) — the widgets' compact
 * rows would otherwise size the "+" and "Stop" tap targets to their text alone. */
internal val MinTapTarget = 48.dp

/** App widget corner radius — matches the system-drawn corner mask on Android 12+ hosts, and Home's
 * Plain plank `Card` (`HomeScreen.kt`'s `PlainPlankHomeCaseListItem`, which takes no explicit
 * `shape` and so renders at M3's default Card shape, `ui/theme/Shape.kt`'s `medium`/`large`). */
internal val WidgetCornerRadius = 8.dp

/**
 * Type/spacing/shape tokens mirroring Plain's `ui/theme/Type.kt`/`Shape.kt` scale as closely as
 * Glance's API allows (DEV_PLAYBOOK.md §4 has the full mapping and the two gaps Glance can't
 * close: no `lineHeight`/`letterSpacing` on [androidx.glance.text.TextStyle], and no SemiBold in
 * [androidx.glance.text.FontWeight]). Values without a Home/`Type.kt` role are widget-only —
 * documented in DEV_PLAYBOOK.md §4 rather than derived from an app surface.
 */
internal val WidgetIconGlyphSize = 24.sp
internal val WidgetCaseNameSize = 16.sp
internal val WidgetSubtitleSize = 12.sp
internal val WidgetPillTextSize = 11.sp
internal val WidgetPlusGlyphSize = 20.sp
internal val WidgetHeaderTitleSize = 14.sp
internal val WidgetInfoMessageSize = 14.sp

/**
 * Weight for the List widget header ("How often does it truly happen?"). Its in-app counterpart —
 * Home's header `Text` at `headlineSmall` (`HomeScreen.kt`) — carries Plain's display weight,
 * `FontWeight.SemiBold`, which `androidx.glance.text.FontWeight` can't express (no 600 rung) and
 * which Glance's RemoteViews host wouldn't apply as a 600 typeface anyway; Bold is the nearest
 * rung. The widget keeps the header at the compact [WidgetHeaderTitleSize] rather than the app's
 * 24sp — a widget's canvas is far narrower — so this is a weight-only nod to the app role, not a
 * full mirror. `WidgetTokenFidelityTest` fails if Plain's `headlineSmall` weight moves off SemiBold.
 */
internal val WidgetHeaderTitleWeight = FontWeight.Bold

internal val WidgetIconTextSpacing = 12.dp
internal val WidgetPillPaddingHorizontal = 8.dp
internal val WidgetPillPaddingVertical = 2.dp
internal val WidgetPillCornerRadius = 999.dp
internal val WidgetLogButtonCornerRadius = 8.dp

/** Widget-only layout, no Home analogue — a widget's canvas is far narrower than a phone screen,
 * so these stay hand-tuned rather than copied from Home's row padding/spacing. */
internal val WidgetOuterPadding = 12.dp
internal val WidgetRowPadding = 10.dp
internal val WidgetLogButtonSpacing = 8.dp
internal val WidgetSingleCaseOuterPadding = 8.dp
internal val WidgetSingleCaseLogButtonSpacing = 6.dp

/** Vertical padding on the List widget header and empty-state message (both are a bare
 * `.fillMaxWidth()` Text, so the padding also sets the clickable area). Sized so the one-line tap
 * target clears [MinTapTarget] (15 + ~14sp line + 15 ≈ 50dp); with no fixed height the Text is free
 * to wrap and grow. Widget-only, no Home analogue. */
internal val WidgetHeaderPadding = 15.dp

/**
 * Gap between List widget Case planks (and between the header and the first plank), giving each row
 * the discrete-card look Home's Plain planks have. Mirrors the space Home's `PlainPlankHomeCaseListItem`
 * `Card` leaves between adjacent planks — its
 * [com.secondmonday.hodith.ui.home.PlainPlankVerticalMargin] applied both above and below, so twice
 * that value. A unit test (`WidgetTokenFidelityTest`) fails if the two drift apart.
 */
internal val WidgetPlankSpacing = 8.dp

internal val CaseIdParam = ActionParameters.Key<Long>(EXTRA_CASE_ID)

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
