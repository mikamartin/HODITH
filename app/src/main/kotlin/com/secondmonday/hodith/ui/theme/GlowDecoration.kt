package com.secondmonday.hodith.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme

/**
 * Bright-only "gradient-wash card": diagonal tint-to-surface gradient, hairline border, soft
 * tinted shadow (Soft Glow mockup's `.card`/`.hrow`, docs/mockups/bright-theme-soft-glow.html).
 * Meant to be reached only from a [LocalCardDecorationStyle.BRIGHT] branch — Plain/Intense keep
 * plain [androidx.compose.material3.Card] and never call this.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = tint.copy(alpha = 0.35f),
                    spotColor = tint.copy(alpha = 0.35f),
                ).clip(shape)
                .background(Brush.linearGradient(listOf(lerp(surface, tint, 0.07f), surface)))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * Bright-only "icon halo": a tinted circle with a soft blurred glow bleeding past its edge (Soft
 * Glow mockup's `.icon-badge`/`.halo`/`.navitem.on .ico`) — shared by Home's case icons, Big
 * Picture's today-cell ring, and Insights' stat-tile icons (PROGRESS.md's Bright theme redesign
 * checklist). [size] is the icon's own layout footprint; the glow overflows it without affecting
 * layout, same as the mockup's non-layout-affecting `box-shadow` blur.
 */
@Composable
fun IconHalo(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 34.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(size * 1.7f)
                    .blur(size / 2)
                    .background(tint.copy(alpha = 0.45f), CircleShape),
        )
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(lerp(surface, tint, 0.16f)),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Preview(name = "GlowCard — Bright light", showBackground = true, widthDp = 320)
@Composable
private fun GlowCardLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        GlowCard(modifier = Modifier.padding(16.dp)) {
            Text("Went for a run", style = MaterialTheme.typography.titleMedium)
            Text("Today: 1 (this week: 4)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(name = "GlowCard — Bright dark", showBackground = true, widthDp = 320)
@Composable
private fun GlowCardDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        GlowCard(modifier = Modifier.padding(16.dp), tint = MaterialTheme.colorScheme.secondary) {
            Text("Coffee before noon", style = MaterialTheme.typography.titleMedium)
            Text("Still going · 1h 12m", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(name = "IconHalo — Bright light", showBackground = true, widthDp = 160, heightDp = 100)
@Composable
private fun IconHaloLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        Box(modifier = Modifier.padding(32.dp)) {
            IconHalo { Text("🏃") }
        }
    }
}

@Preview(name = "IconHalo — Bright dark", showBackground = true, widthDp = 160, heightDp = 100)
@Composable
private fun IconHaloDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        Box(modifier = Modifier.padding(32.dp)) {
            IconHalo(tint = MaterialTheme.colorScheme.secondary) { Text("☕") }
        }
    }
}
