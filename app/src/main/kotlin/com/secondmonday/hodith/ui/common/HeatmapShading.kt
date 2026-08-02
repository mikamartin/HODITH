package com.secondmonday.hodith.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.secondmonday.hodith.domain.HEATMAP_TIER_COUNT
import com.secondmonday.hodith.domain.HeatmapLevel

/** L1's minimum shade — any lower and the lightest tier would be indistinguishable from [HeatmapLevel.EMPTY]. */
private const val HEATMAP_MIN_SHADE_FRACTION = 0.28f

/** Past this tier, a cell is saturated enough to need on-primary contrast; lighter tiers read fine with the muted default. */
private const val HEATMAP_CONTRAST_TIER_THRESHOLD = HEATMAP_TIER_COUNT / 2 + 1

/**
 * Shared with [com.secondmonday.hodith.ui.casedetail.InsightsTab] and
 * [com.secondmonday.hodith.ui.share.ShareCardTemplate] so the calendar heatmap, rhythm grid,
 * intensity cells, dot timeline, and their share-card mini-copies all shade identically — one
 * formula, not three copies that could quietly drift apart.
 */
private fun HeatmapLevel.shadeFraction(): Float {
    if (this == HeatmapLevel.EMPTY) return 0f
    val tier = ordinal
    return HEATMAP_MIN_SHADE_FRACTION + (1f - HEATMAP_MIN_SHADE_FRACTION) * (tier - 1) / (HEATMAP_TIER_COUNT - 1)
}

@Composable
internal fun HeatmapLevel.toCellColor(): Color {
    if (this == HeatmapLevel.EMPTY) return MaterialTheme.colorScheme.surfaceVariant
    return lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary, shadeFraction())
}

@Composable
internal fun HeatmapLevel.toTextColor(): Color =
    if (this != HeatmapLevel.EMPTY && ordinal >= HEATMAP_CONTRAST_TIER_THRESHOLD) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
