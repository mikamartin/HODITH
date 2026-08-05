package com.secondmonday.hodith.widget

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick

// Shared by ListWidgetConfigureFlowTest/SingleCaseWidgetConfigureFlowTest. LazyColumn flattens row
// items into semantics siblings regardless of visual Row grouping, so text-based sibling matching
// can't tell which toggle/radio belongs to which case's Text — and since these run against the
// real shared app database (which may have other, unrelated Cases in the same list), clicking
// every [control] isn't safe either. Matching by vertical bounds overlap with the target Text node
// is what actually identifies the right row.
internal fun ComposeTestRule.clickRowControl(
    caseName: String,
    control: SemanticsMatcher,
) {
    val textBounds = onNode(hasText(caseName, substring = true)).fetchSemanticsNode().boundsInRoot
    val targetId =
        onAllNodes(control)
            .fetchSemanticsNodes()
            .first { it.boundsInRoot.top < textBounds.bottom && it.boundsInRoot.bottom > textBounds.top }
            .id
    onNode(SemanticsMatcher("id == $targetId") { it.id == targetId }).performClick()
}
