package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.testtags.UiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for the `modifier` default: a caller's modifier must add to the row's own
 * full-width layout and 8dp top gap, not replace them. Before the fix the default lived on the
 * parameter, so passing any modifier (like this test's tag) silently dropped both.
 *
 * On asserting [SegmentedRowFontSizeCoordinator]'s autoSize-driven shrink: `onNodeWithText`/
 * `getUnclippedBoundsInRoot` on a segment's label resolves to the *merged* `SegmentedButton` (or
 * Bright's `selectable` [androidx.compose.foundation.layout.Box]) semantics node, not the inner
 * `Text` leaf — and that merged node's height is pinned to `SegmentedButtonDefaults`' fixed
 * minimum touch-target height (40dp), which doesn't move no matter how far the inner label's font
 * actually shrinks. That's why two earlier instrumented-test attempts (a bounding-box height
 * assertion, and a `GetTextLayoutResult`-semantics probe, both since removed) saw zero observable
 * change from 300dp down to 20dp/segment — they were reading the button's fixed chrome height, not
 * the label's real size. [coordinatedFontSize_shrinksBelowBaselineAtNarrowWidth] below reads
 * [SegmentedRowFontSizeCoordinator.resolved] directly instead — the same value every segment's
 * `onTextLayout` callback already reports in production — which sidesteps the semantics tree
 * entirely. Don't reintroduce a semantics-tree-based assertion for this property.
 */
@UiTest
class SegmentedChoiceRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val containerTag = "segmented_container"
    private val rowTag = "segmented_row"
    private val containerWidth = 300.dp
    private val options = listOf(0 to "One", 1 to "Two")

    private fun setContent(stretchToFill: Boolean = true) {
        composeTestRule.setHodithContent(theme = AppTheme.PLAIN) {
            Column(modifier = Modifier.width(containerWidth).testTag(containerTag)) {
                SegmentedChoiceRow(
                    options = options,
                    selected = 0,
                    onSelect = {},
                    modifier = Modifier.testTag(rowTag),
                    stretchToFill = stretchToFill,
                )
            }
        }
    }

    @Test
    fun callerModifier_keepsFullWidthLayout() {
        setContent()

        val container = composeTestRule.onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        val row = composeTestRule.onNodeWithTag(rowTag).getUnclippedBoundsInRoot()

        assertEquals(widthOf(container).value, widthOf(row).value, 0.5f)
    }

    @Test
    fun callerModifier_keepsEightDpTopGap() {
        setContent()

        val container = composeTestRule.onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        val row = composeTestRule.onNodeWithTag(rowTag).getUnclippedBoundsInRoot()

        assertEquals(8f, (row.top - container.top).value, 0.5f)
    }

    /**
     * Regression for the duration-mode row (None/Manual/Start-stop) wrapping "Start/stop" to two
     * lines in the Plain theme: Plain's Inter font is wider per-character than Intense's condensed
     * Oswald, so the same 3-way split only overflowed here. Compares the long label's rendered
     * height against a short sibling's in the same row/width/theme rather than hardcoding font
     * metrics -- a wrapped two-line label is roughly 2x as tall as a one-line sibling.
     */
    @Test
    fun longLabelOption_doesNotWrapInPlainTheme() = assertLongLabelDoesNotWrap(AppTheme.PLAIN)

    /** Same control, Bright's own [BrightSegmentedChoiceRow] branch -- covered for the same reason. */
    @Test
    fun longLabelOption_doesNotWrapInBrightTheme() = assertLongLabelDoesNotWrap(AppTheme.BRIGHT)

    private fun assertLongLabelDoesNotWrap(theme: AppTheme) {
        setDurationModeStyleContent(theme)

        val shortLabelHeight = heightOf(composeTestRule.onNodeWithText("None").getUnclippedBoundsInRoot())
        val longLabelHeight = heightOf(composeTestRule.onNodeWithText("Start/stop").getUnclippedBoundsInRoot())

        assertTrue(
            "Expected \"Start/stop\" to render on one line like \"None\", but its text block was " +
                "$longLabelHeight tall vs $shortLabelHeight",
            longLabelHeight <= shortLabelHeight * 1.3f,
        )
    }

    private fun setDurationModeStyleContent(theme: AppTheme) {
        composeTestRule.setHodithContent(theme = theme) {
            Column(modifier = Modifier.width(containerWidth)) {
                SegmentedChoiceRow(
                    options = listOf(0 to "None", 1 to "Manual", 2 to "Start/stop"),
                    selected = 2,
                    onSelect = {},
                )
            }
        }
    }

    /**
     * See the class doc comment on why this reads [SegmentedRowFontSizeCoordinator.resolved]
     * directly rather than any bounding-box or semantics-tree measurement.
     */
    @Test
    fun coordinatedFontSize_shrinksBelowBaselineAtNarrowWidth() {
        val coordinator = SegmentedRowFontSizeCoordinator()
        var baseline: TextUnit = TextUnit.Unspecified
        composeTestRule.setHodithContent(theme = AppTheme.PLAIN) {
            baseline = MaterialTheme.typography.labelLarge.fontSize
            CompositionLocalProvider(LocalSegmentedRowFontSizeCoordinator provides coordinator) {
                Column(modifier = Modifier.width(60.dp)) {
                    SegmentedChoiceRow(
                        options = listOf(0 to "None", 1 to "Manual", 2 to "Start/stop"),
                        selected = 2,
                        onSelect = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val resolved = coordinator.resolved
        assertTrue(
            "Expected the coordinator to resolve to a size below baseline $baseline, was $resolved",
            resolved != null && resolved.value < baseline.value,
        )
    }

    @Test
    fun stretchToFillFalse_dropsFullWidthLayout() {
        setContent(stretchToFill = false)

        val container = composeTestRule.onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        val row = composeTestRule.onNodeWithTag(rowTag).getUnclippedBoundsInRoot()

        assertTrue(
            "Expected an inline row narrower than its container, was ${widthOf(row)} of ${widthOf(container)}",
            widthOf(row) < widthOf(container),
        )
    }

    private fun widthOf(bounds: DpRect): Dp = bounds.right - bounds.left

    private fun heightOf(bounds: DpRect): Dp = bounds.bottom - bounds.top
}
