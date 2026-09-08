package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.voice.voiceFor

/** Symmetric inset so a wrapped line never reaches either screen edge — one step outside the 16dp content padding. */
private val EMPTY_STATE_HORIZONTAL_PADDING = 24.dp

/**
 * The shared "nothing here yet" note every list/tab shows before it has data (Home, Big Picture,
 * Archived cases, the Case Detail Log and Insights tabs). A [BoxScope] extension so it drops into
 * each screen's existing centring [Box].
 *
 * The container only centres the text *node*; [TextAlign.Center] plus [fillMaxWidth] is what keeps
 * the glyphs centred once the string wraps to two lines (the longer Intense and Bright phrasings do,
 * at a phone width), instead of every wrapped line laying out flush against the start edge.
 */
@Composable
fun BoxScope.CenteredEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = EMPTY_STATE_HORIZONTAL_PADDING),
    )
}

@Composable
private fun CenteredEmptyStatePreviewContent(theme: AppTheme) {
    val voice = voiceFor(theme)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Long enough to wrap at a phone width in every voice — the case the fix exists for.
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            CenteredEmptyState(voice.insightsNotEnoughDataMessage(eventsRemaining = 1))
        }
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            CenteredEmptyState(voice.eventListEmptyState)
        }
    }
}

@Preview(name = "CenteredEmptyState — Plain light", showBackground = true, widthDp = 320)
@Composable
private fun CenteredEmptyStatePlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) { CenteredEmptyStatePreviewContent(AppTheme.PLAIN) }
}

@Preview(name = "CenteredEmptyState — Intense", showBackground = true, widthDp = 320)
@Composable
private fun CenteredEmptyStateIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) { CenteredEmptyStatePreviewContent(AppTheme.INTENSE) }
}

@Preview(name = "CenteredEmptyState — Bright light", showBackground = true, widthDp = 320)
@Composable
private fun CenteredEmptyStateBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) { CenteredEmptyStatePreviewContent(AppTheme.BRIGHT) }
}
