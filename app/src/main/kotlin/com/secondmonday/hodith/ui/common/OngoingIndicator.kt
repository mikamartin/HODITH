package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.formatElapsedDuration

/**
 * Elapsed-time display for a Case's ongoing event (spec §6), shared by Home's row and Case
 * Detail's header so the two screens can't drift out of sync in wording or styling.
 */
@Composable
fun OngoingElapsedText(
    startedAt: Long,
    now: Long,
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    Text(
        text = voice.ongoingIndicator(formatElapsedDuration(startedAt, now)),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/** Stop is always an immediate one-tap action (spec §6) — no sheet, regardless of `logFlow`. */
@Composable
fun StopIconButton(
    caseName: String,
    voice: Voice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Filled.Done, contentDescription = voice.stopActionDescription(caseName))
    }
}

/**
 * The 24h "still going, or forgot to stop it?" gentle prompt (spec §6). First `Card`-based
 * component in the app — kept intentionally small (message + two text actions) rather than
 * introducing a heavier banner system for a single use case.
 */
@Composable
fun StaleOngoingBanner(
    caseName: String,
    elapsed: String,
    voice: Voice,
    onEditEndTime: () -> Unit,
    onStillGoing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(voice.staleOngoingPromptMessage(caseName, elapsed), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEditEndTime) { Text(voice.staleOngoingEditEndTimeAction) }
                TextButton(onClick = onStillGoing) { Text(voice.staleOngoingStillGoingAction) }
            }
        }
    }
}
