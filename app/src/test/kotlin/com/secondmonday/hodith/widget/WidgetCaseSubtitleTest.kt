package com.secondmonday.hodith.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import com.secondmonday.hodith.data.CaseEventSpan
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.formatExpectedEndTime
import org.junit.Test

/**
 * Exercises [WidgetCaseSubtitle] directly, in isolation from either widget's own composable tree,
 * via Glance's own unit-testing library rather than a real [android.appwidget.AppWidgetHost]. This
 * is the only automated coverage of the List widget's pill/subtitle/count rendering: the List
 * widget's `LazyColumn` rows can never be reached by `AppWidgetHost.createView()`
 * ([WidgetActionsFlowTest]'s class doc comment), and the Single-case widget no longer renders a
 * subtitle at all (CLEANUP_LOG.md), so neither widget's own instrumented test can exercise this
 * shared function any more.
 */
class WidgetCaseSubtitleTest {
    private fun row(
        todayCount: Int = 0,
        ongoingEvent: CaseEventSpan? = null,
        runningCount: Int = 0,
    ) = HomeCaseRow(
        caseId = 1L,
        icon = "🐛",
        name = "Test Case",
        todayCount = todayCount,
        weekCount = 0,
        logFlow = LogFlow.ONE_TAP,
        tracksDuration = true,
        intensityEnabled = false,
        ongoingEvent = ongoingEvent,
        runningCount = runningCount,
    )

    @Test
    fun `no ongoing event shows the today count`() =
        runGlanceAppWidgetUnitTest {
            provideComposable {
                WidgetCaseSubtitle(row = row(todayCount = 3), now = 0L, use24Hour = false)
            }
            onNode(hasText(PlainVoice.widgetTodayCount(3))).assertExists()
        }

    @Test
    fun `one running event shows the Ongoing pill`() =
        runGlanceAppWidgetUnitTest {
            val ongoing = CaseEventSpan(caseId = 1L, occurredAt = 0L, endedAt = null, tracksDuration = true)
            provideComposable {
                WidgetCaseSubtitle(row = row(ongoingEvent = ongoing, runningCount = 1), now = 0L, use24Hour = false)
            }
            onNode(hasText(PlainVoice.ongoingPillLabel)).assertExists()
        }

    @Test
    fun `two running events show a count instead of one elapsed time`() =
        runGlanceAppWidgetUnitTest {
            val ongoing = CaseEventSpan(caseId = 1L, occurredAt = 0L, endedAt = null, tracksDuration = true)
            provideComposable {
                WidgetCaseSubtitle(row = row(ongoingEvent = ongoing, runningCount = 2), now = 0L, use24Hour = false)
            }
            onNode(hasText(PlainVoice.ongoingCountIndicator(2))).assertExists()
        }

    @Test
    fun `a running event with a future expected end shows the expected-end trailing text`() =
        runGlanceAppWidgetUnitTest {
            val now = 1_000_000L
            val expectedEndAt = now + 60 * 60 * 1000L
            val ongoing = CaseEventSpan(caseId = 1L, occurredAt = now, endedAt = expectedEndAt, tracksDuration = true)
            provideComposable {
                WidgetCaseSubtitle(row = row(ongoingEvent = ongoing, runningCount = 1), now = now, use24Hour = false)
            }
            val expectedTrailing =
                PlainVoice.ongoingExpectedEndTrailing(formatExpectedEndTime(expectedEndAt, now, use24Hour = false))
            onNode(hasText(expectedTrailing)).assertExists()
        }
}
