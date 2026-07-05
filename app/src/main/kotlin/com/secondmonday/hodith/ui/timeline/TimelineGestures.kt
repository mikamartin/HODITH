package com.secondmonday.hodith.ui.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

private const val ZOOM_SLOP = 0.03f

/**
 * A single pointer-input loop that decides, per gesture, whether the user tapped (no meaningful
 * movement or pinch) or panned/zoomed the shared timeline. Running both through one gesture
 * detector — rather than a tap detector layered on top of a separate transform-gesture detector —
 * is what makes the disambiguation reliable: one state machine tracks touch-slop across both
 * possibilities instead of two independent detectors racing each other on the same pointer stream.
 */
suspend fun PointerInputScope.detectTapOrTimelineGesture(
    onTap: (Offset) -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var pastTouchSlop = false
        var accumulatedPan = Offset.Zero
        var accumulatedZoom = 1f
        val touchSlop = viewConfiguration.touchSlop

        do {
            val event = awaitPointerEvent()
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!pastTouchSlop) {
                accumulatedPan += panChange
                accumulatedZoom *= zoomChange
                if (accumulatedPan.getDistance() > touchSlop || abs(1f - accumulatedZoom) > ZOOM_SLOP) {
                    pastTouchSlop = true
                }
            }

            if (pastTouchSlop) {
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
                }
                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            }
        } while (event.changes.any { it.pressed })

        if (pastTouchSlop) {
            onGestureEnd()
        } else {
            onTap(down.position)
        }
    }
}
