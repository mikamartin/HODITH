package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
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
}
