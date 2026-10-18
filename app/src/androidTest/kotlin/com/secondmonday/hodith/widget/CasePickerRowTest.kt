package com.secondmonday.hodith.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.testtags.UiTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression for the single-case widget's case picker overlapping a long case name with its
 * `RadioButton`: the label `Text` had no [Modifier.weight], so a long name was measured at its
 * full intrinsic width and pushed the radio button past the row. Mirrors [ToggleRowTest]'s
 * approach for the multi-select widget dialog's equivalent row.
 */
@UiTest
class CasePickerRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val containerWidth = 200.dp
    private val containerTag = "case_picker_row_container"
    private val longName =
        "A dramatically overlong case name that would have collided with the radio button before the fix"

    private fun setContent() {
        composeTestRule.setContent {
            Column(modifier = Modifier.width(containerWidth).testTag(containerTag)) {
                CasePickerRow(case = testCase(name = longName), selected = true, onSelect = {})
            }
        }
    }

    @Test
    fun longName_radioButtonStaysInsideContainer() {
        setContent()

        val container = composeTestRule.onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        val radio =
            composeTestRule.onNodeWithTag(CASE_PICKER_RADIO_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            "Expected the RadioButton's right edge (${radio.right}) to stay within the container " +
                "(${container.right}), but the long name pushed it out",
            radio.right <= container.right,
        )
    }

    @Test
    fun longName_textStopsBeforeRadioButton() {
        setContent()

        val text =
            composeTestRule
                .onNodeWithText(longName, substring = true, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val radio =
            composeTestRule.onNodeWithTag(CASE_PICKER_RADIO_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            "Expected the label (ending at ${text.right}) to stop at or before the RadioButton " +
                "(starting at ${radio.left}), not overlap it",
            text.right <= radio.left,
        )
    }
}
