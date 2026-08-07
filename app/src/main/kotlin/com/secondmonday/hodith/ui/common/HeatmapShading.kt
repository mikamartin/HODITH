package com.secondmonday.hodith.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.secondmonday.hodith.domain.HEATMAP_TIER_COUNT
import com.secondmonday.hodith.domain.HeatmapLevel

/** L1's minimum shade — any lower and the lightest tier would be indistinguishable from [HeatmapLevel.EMPTY]. */
private const val HEATMAP_MIN_SHADE_FRACTION = 0.28f

/**
 * Shared with [com.secondmonday.hodith.ui.casedetail.InsightsTab] and
 * [com.secondmonday.hodith.ui.share.ShareCardTemplate] so the calendar heatmap, rhythm grid,
 * intensity cells, and their share-card mini-copies all shade identically — one formula, not
 * several copies that could quietly drift apart. [tierCount] must match whatever tier count
 * [com.secondmonday.hodith.domain.heatmapLevelFor] used to produce this level, since the same
 * ordinal means a different position on the scale depending on the resolution it was bucketed into.
 */
private fun HeatmapLevel.shadeFraction(tierCount: Int): Float {
    if (this == HeatmapLevel.EMPTY) return 0f
    val tier = ordinal
    return HEATMAP_MIN_SHADE_FRACTION + (1f - HEATMAP_MIN_SHADE_FRACTION) * (tier - 1) / (tierCount - 1)
}

@Composable
internal fun HeatmapLevel.toCellColor(tierCount: Int = HEATMAP_TIER_COUNT): Color {
    if (this == HeatmapLevel.EMPTY) return MaterialTheme.colorScheme.surfaceVariant
    return lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary, shadeFraction(tierCount))
}

@Composable
internal fun HeatmapLevel.toTextColor(tierCount: Int = HEATMAP_TIER_COUNT): Color =
    if (this != HeatmapLevel.EMPTY && ordinal >= tierCount / 2 + 1) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
