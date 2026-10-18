package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.testtags.UiTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression for the widget case-picker overlap: before the fix, the label `Text` had no
 * [Modifier.weight], so a long label was measured at its full intrinsic width and the `Switch`
 * ended up pushed past the row's own bounds instead of stopping short of it. [ToggleRow] now gives
 * the label `weight(1f)` + `maxLines`/`overflow`, so the `Switch` should stay fully inside the row
 * -- and the label should stop at or before the `Switch` -- no matter how long the label is.
 */
@UiTest
class ToggleRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val containerWidth = 200.dp
    private val containerTag = "toggle_row_container"
    private val longLabel =
        "A dramatically overlong case name that would have collided with the switch before the fix"

    private fun setContent() {
        composeTestRule.setContent {
            Column(modifier = Modifier.width(containerWidth).testTag(containerTag)) {
                ToggleRow(label = longLabel, checked = true, onCheckedChange = {})
            }
        }
    }

    @Test
    fun longLabel_switchStaysInsideContainer() {
        setContent()

        val container = composeTestRule.onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        val switch =
            composeTestRule.onNodeWithTag(TOGGLE_ROW_SWITCH_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            "Expected the Switch's right edge (${switch.right}) to stay within the container " +
                "(${container.right}), but the long label pushed it out",
            switch.right <= container.right,
        )
    }

    @Test
    fun longLabel_textStopsBeforeSwitch() {
        setContent()

        val text = composeTestRule.onNodeWithText(longLabel, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val switch =
            composeTestRule.onNodeWithTag(TOGGLE_ROW_SWITCH_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            "Expected the label (ending at ${text.right}) to stop at or before the Switch " +
                "(starting at ${switch.left}), not overlap it",
            text.right <= switch.left,
        )
    }
}
