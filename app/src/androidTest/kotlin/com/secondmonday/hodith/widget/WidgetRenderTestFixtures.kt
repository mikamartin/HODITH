package com.secondmonday.hodith.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

// Shared by the widget flow tests that drive a real AppWidgetHost (ListWidgetConfigureFlowTest,
// SingleCaseWidgetConfigureFlowTest, WidgetActionsFlowTest, WidgetChromeNavigationTest): rendering
// a bound widget's real View tree, walking it for text, and finding the clickable ancestor of a
// TextView.

internal fun renderedView(
    context: Context,
    host: AppWidgetHost,
    appWidgetId: Int,
): View {
    val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
    var view: View? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { view = host.createView(context, appWidgetId, info) }
    return requireNotNull(view)
}

internal fun collectText(view: View): List<String> {
    val result = mutableListOf<String>()

    fun visit(v: View) {
        if (v is TextView) result.add(v.text.toString())
        if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
    }
    visit(view)
    return result
}

// Glance renders .clickable(...) as an OnClickListener on an ancestor wrapper View, not
// necessarily the leaf TextView itself, so this tracks the nearest clickable ancestor while
// descending and returns it once the matching text is found.
internal fun findClickableAncestorOfText(
    view: View,
    text: String,
    clickableAncestor: View? = if (view.hasOnClickListeners()) view else null,
): View? {
    if (view is TextView && view.text.toString() == text) return clickableAncestor
    if (view is ViewGroup) {
        val ancestor = if (view.hasOnClickListeners()) view else clickableAncestor
        for (i in 0 until view.childCount) {
            findClickableAncestorOfText(view.getChildAt(i), text, ancestor)?.let { return it }
        }
    }
    return null
}

// Returns the TextView itself (not just its text), so a test can assert rendered pixel attributes
// — size, color — against a WidgetCommon.kt token, not just that the text is present.
internal fun findTextViewWithText(
    view: View,
    text: String,
): TextView? {
    if (view is TextView && view.text.toString() == text) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            findTextViewWithText(view.getChildAt(i), text)?.let { return it }
        }
    }
    return null
}

// Converts a WidgetCommon.kt sp token to the px value a rendered TextView.textSize should equal, via
// the test device's own DisplayMetrics — matches how Android resolves sp -> px at render time, so it
// holds across density instead of hardcoding a px number.
internal fun spToPx(
    context: Context,
    sp: Float,
): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)

internal fun bindAndRenderSingleCaseWidget(
    context: Context,
    host: AppWidgetHost,
    caseId: Long,
): Int =
    runBlocking {
        val appWidgetId = host.allocateAppWidgetId()
        val provider = ComponentName(context, SingleCaseWidgetReceiver::class.java)
        val bound = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(appWidgetId, provider)
        assertTrue("bindAppWidgetIdIfAllowed failed - is bind permission granted for this package?", bound)

        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, glanceId) { prefs -> prefs[CaseIdKey] = caseId }
        SingleCaseWidget().update(context, glanceId)
        appWidgetId
    }
