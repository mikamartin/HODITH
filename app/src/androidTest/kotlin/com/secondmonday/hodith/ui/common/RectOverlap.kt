package com.secondmonday.hodith.ui.common

import androidx.compose.ui.geometry.Rect

/** True when two screen rects visually collide — used to assert a FAB doesn't cover a list row's trailing button. */
internal fun Rect.overlapsRect(other: Rect): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top
