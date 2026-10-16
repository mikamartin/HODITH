package com.secondmonday.hodith.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val CAPTURE_TRIGGER_TAG = "offscreen_capture_trigger"
private const val MARKER_SIZE_DP = 24
private const val CAPTURE_WIDTH_PX = 400
private const val CAPTURE_HEIGHT_PX = 200

/**
 * Validates the technique feature/pdf-export's chart capture relies on, before any PDF code is
 * built on top of it: [OffscreenCapture] renders content at a fixed pixel size, fully attached to
 * the live composition (not a second, detached Composition), without disturbing the host
 * screen's real layout. Red/blue halves prove the right content actually drew at the right place
 * (not a blank layer); the marker's unchanged bounds prove zero footprint.
 */
@UiTest
class OffscreenCaptureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capturesFixedSizeContentWithoutAffectingVisibleLayout() {
        var captured: Bitmap? = null

        composeTestRule.setHodithContent {
            val graphicsLayer = rememberGraphicsLayer()
            var triggered by remember { mutableStateOf(false) }

            LaunchedEffect(triggered) {
                if (triggered) captured = graphicsLayer.toImageBitmap().asAndroidBitmap()
            }

            OffscreenCapture(graphicsLayer = graphicsLayer, widthPx = CAPTURE_WIDTH_PX, heightPx = CAPTURE_HEIGHT_PX) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxSize().background(Color.Red))
                    Box(Modifier.weight(1f).fillMaxSize().background(Color.Blue))
                }
            }

            Box(
                Modifier
                    .size(MARKER_SIZE_DP.dp)
                    .testTag(CAPTURE_TRIGGER_TAG)
                    .clickable { triggered = true },
            )
        }

        // One full draw pass settles before the capture is even requested, same as a real screen
        // would have happened by the time a person taps a share button.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(CAPTURE_TRIGGER_TAG).performClick()
        composeTestRule.waitForIdle()

        // toImageBitmap() hands back a GPU-backed Config.HARDWARE bitmap - exactly what a PDF
        // exporter will also need to convert before drawing it into a software PdfDocument page
        // Canvas. getPixel() needs the same conversion here, to inspect what was actually drawn.
        val bitmap = requireNotNull(captured) { "Offscreen capture never completed" }.copy(Bitmap.Config.ARGB_8888, false)
        assertEquals(CAPTURE_WIDTH_PX, bitmap.width)
        assertEquals(CAPTURE_HEIGHT_PX, bitmap.height)
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(CAPTURE_WIDTH_PX / 4, CAPTURE_HEIGHT_PX / 2))
        assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(CAPTURE_WIDTH_PX * 3 / 4, CAPTURE_HEIGHT_PX / 2))

        // The hidden 400x200px content must not have pushed or resized anything in the real layout.
        composeTestRule.onNodeWithTag(CAPTURE_TRIGGER_TAG).assertWidthIsEqualTo(MARKER_SIZE_DP.dp).assertHeightIsEqualTo(MARKER_SIZE_DP.dp)
    }
}
