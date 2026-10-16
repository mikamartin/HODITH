package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints

/** Far enough outside any real viewport that the placed content can never be visible, regardless of parent clipping. */
private const val OFFSCREEN_PLACEMENT_PX = -100_000

/**
 * Renders [content] at exactly [widthPx] by [heightPx] device pixels into [graphicsLayer], fully
 * attached to the live composition it's called from — so it reliably redraws every frame, the
 * same as any visible content — but reports zero size to its own parent and places its real
 * pixels far outside the visible viewport, so it never affects the host screen's layout or
 * appears to the person using it. [clearAndSetSemantics] keeps it out of the semantics tree too -
 * off-screen placement alone hides it visually, but a test or an accessibility service still
 * walks semantics nodes regardless of position, so without this a query for "no Frequency text
 * anywhere on this tab" would wrongly see this hidden copy. Exists so a capture can be rendered
 * at a size the visible screen's own width can't offer (e.g. a PDF page's wider chart box)
 * without a second, detached Composition.
 */
@Composable
internal fun OffscreenCapture(
    graphicsLayer: GraphicsLayer,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
            ) {
                content()
            }
        },
    ) { measurables, _ ->
        val placeable = measurables.single().measure(Constraints.fixed(widthPx, heightPx))
        layout(0, 0) { placeable.place(OFFSCREEN_PLACEMENT_PX, OFFSCREEN_PLACEMENT_PX) }
    }
}
