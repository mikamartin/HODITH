package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.ui.common.FabListBottomClearance
import com.secondmonday.hodith.ui.common.RowWithInfo
import com.secondmonday.hodith.ui.common.expectationProgressFraction
import com.secondmonday.hodith.ui.common.themedSwitchColors
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.WatchCardState
import com.secondmonday.hodith.viewmodel.WatchesUiState
import com.secondmonday.hodith.viewmodel.formatRate
import com.secondmonday.hodith.viewmodel.watchCardState

/**
 * Case Detail's bell tab (spec §11/§14): the check-ins row (moved here from Case Edit), then one
 * card per Watch, an add FAB (owned by [CaseDetailScreen]'s outer `floatingActionButton`
 * slot, not this composable — no nested Scaffold), and an empty state. Stateless: [uiState] and
 * every callback come from [CaseDetailScreen]'s own `hiltViewModel<WatchesViewModel>()`.
 */
@Composable
internal fun WatchesTabContent(
    uiState: WatchesUiState,
    now: Long,
    voice: Voice,
    onCheckInToggle: (Boolean) -> Unit,
    onSetEnabled: (Long, Boolean) -> Unit,
    onCreateRequest: () -> Unit,
    onCardClick: (WatchEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Unit
            uiState.watches.isEmpty() -> {
                CheckInRow(uiState.checkInsEnabled, onCheckInToggle, voice)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    WatchesEmptyState(voice = voice, onCreate = onCreateRequest, modifier = Modifier.align(Alignment.Center))
                }
            }
            else ->
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = FabListBottomClearance),
                ) {
                    item {
                        CheckInRow(uiState.checkInsEnabled, onCheckInToggle, voice)
                    }
                    items(uiState.watches, key = { it.id }) { watch ->
                        val cardState =
                            remember(watch, uiState.events, uiState.durationMode, uiState.caseCreatedAt, now) {
                                watchCardState(watch, uiState.events, uiState.durationMode, uiState.caseCreatedAt, now)
                            }
                        WatchCard(
                            state = cardState,
                            voice = voice,
                            onSetEnabled = { enabled -> onSetEnabled(watch.id, enabled) },
                            onClick = { onCardClick(watch) },
                        )
                    }
                }
        }
    }
}

/** Scrolls away with the rest of the tab's content rather than staying pinned above it, so the card list keeps the full screen height. */
@Composable
private fun CheckInRow(
    checkInsEnabled: Boolean,
    onCheckInToggle: (Boolean) -> Unit,
    voice: Voice,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        RowWithInfo(voice.caseCheckInLabel, voice.caseCheckInInfoTitle, voice.caseCheckInInfoBody, voice.caseSectionInfoDescription) {
            Switch(checked = checkInsEnabled, onCheckedChange = onCheckInToggle, colors = themedSwitchColors())
        }
    }
}

@Composable
private fun WatchesEmptyState(
    voice: Voice,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(voice.watchesEmptyTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(voice.watchesEmptyBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onCreate) { Text(voice.watchesEmptyCta) }
    }
}

/**
 * One Watch's card, two zones (validated against the Layout B prototype): an upper zone — title,
 * settings line (OFTEN only) and the enable switch — stating what's being watched for, and a
 * tinted lower zone stating its current state (observed rate/comparison once a verdict exists, a
 * progress bar before one does, or the plain silence count for QUIET). A disabled Watch is dimmed
 * (both zones together) but still shows its current state. Tapping the card opens the editor
 * prefilled for edit; delete lives inside that editor now, not here.
 */
@Composable
private fun WatchCard(
    state: WatchCardState,
    voice: Voice,
    onSetEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val title = cardTitle(state, voice)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp).alpha(if (state.enabled) 1f else 0.55f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardEyebrow(voice.watchDefinitionEyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onSetEnabled,
                    colors = themedSwitchColors(),
                    modifier = Modifier.semantics { contentDescription = voice.watchToggleDescription(title) },
                )
            }
            if (state.kind == WatchKind.OFTEN) {
                Text(
                    text =
                        voice.watchSettingsLine(
                            lookbackDays = state.lookbackDays ?: 0,
                            showMeasure = state.tracksDuration,
                            metric = state.metric,
                            minIntensity = state.minIntensity,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardEyebrow(voice.watchNowEyebrow, color = MaterialTheme.colorScheme.primary)
                    WatchNowBody(state, voice)
                    state.firedDaysAgo?.let { daysAgo -> FiredLine(daysAgo, voice) }
                }
            }
        }
    }
}

@Composable
private fun CardEyebrow(
    text: String,
    color: Color,
) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
}

/** The tinted Now zone's middle content — depends on the Watch's kind and, for OFTEN, its verdict tier. */
@Composable
private fun WatchNowBody(
    state: WatchCardState,
    voice: Voice,
) {
    val expectation = state.expectation
    val result = state.verdictResult
    when {
        state.kind == WatchKind.QUIET ->
            Text(voice.watchNowLineQuiet(state.silentDays ?: 0L), style = MaterialTheme.typography.bodyMedium)
        expectation != null && result != null && result.tier == ConfidenceTier.NO_VERDICT -> {
            val daysActive = expectation.metric == VerdictMetric.DAYS_ACTIVE
            val observationCount = if (daysActive) result.activeDayCount else result.eventCount
            val progressUnit = if (daysActive) voice.expectationProgressUnitDaysActive else voice.expectationProgressUnitEvents
            TierBadge(voice.expectationEarlyBadgeLabel)
            LinearProgressIndicator(
                progress = { expectationProgressFraction(observationCount, result.windowDays) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                voice.expectationProgressLabel(observationCount, progressUnit, result.windowDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        expectation != null && result != null -> {
            // Guaranteed non-null once the tier clears NO_VERDICT: computeVerdict always resolves a band then.
            val band = checkNotNull(result.comparisonBand) { "Verdict state must carry a resolved comparison band" }
            val daysActive = expectation.metric == VerdictMetric.DAYS_ACTIVE
            val rateLabel = formatRate(result.observedRate, expectation.per, expectation.metric)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TierBadge(voice.expectationTierBadgeLabel(result.tier))
            }
            Text(
                voice.watchComparisonLabel(band, daysActive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (daysActive) {
                    voice.verdictMetaDaysActive(result.tier, result.activeDayCount, result.windowDays)
                } else {
                    voice.verdictMeta(result.tier, result.eventCount, result.windowDays)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TierBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FiredLine(
    daysAgo: Long,
    voice: Voice,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            voice.watchFiredAgo(daysAgo),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun cardTitle(
    state: WatchCardState,
    voice: Voice,
): String =
    when (state.kind) {
        WatchKind.OFTEN -> voice.watchCardTitleOften(state.threshold, state.expectedPer, state.metric)
        WatchKind.QUIET -> voice.watchCardTitleQuiet(state.threshold)
    }
