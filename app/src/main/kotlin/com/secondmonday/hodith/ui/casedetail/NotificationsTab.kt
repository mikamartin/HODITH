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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.ui.common.ExpectationEarlyCard
import com.secondmonday.hodith.ui.common.ExpectationVerdictCard
import com.secondmonday.hodith.ui.common.FabListBottomClearance
import com.secondmonday.hodith.ui.common.RowWithInfo
import com.secondmonday.hodith.ui.common.themedSwitchColors
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.NotificationCardState
import com.secondmonday.hodith.viewmodel.NotificationsUiState
import com.secondmonday.hodith.viewmodel.formatRate
import com.secondmonday.hodith.viewmodel.notificationCardState

/**
 * Case Detail's bell tab (spec §11/§14): the check-ins row (moved here from Case Edit), then one
 * card per Notification, an add FAB (owned by [CaseDetailScreen]'s outer `floatingActionButton`
 * slot, not this composable — no nested Scaffold), and an empty state. Stateless: [uiState] and
 * every callback come from [CaseDetailScreen]'s own `hiltViewModel<NotificationsViewModel>()`.
 */
@Composable
internal fun NotificationsTabContent(
    uiState: NotificationsUiState,
    now: Long,
    voice: Voice,
    onCheckInToggle: (Boolean) -> Unit,
    onSetEnabled: (Long, Boolean) -> Unit,
    onCreateRequest: () -> Unit,
    onCardClick: (NotificationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            RowWithInfo(voice.caseCheckInLabel, voice.caseCheckInInfoTitle, voice.caseCheckInInfoBody, voice.caseSectionInfoDescription) {
                Switch(checked = uiState.checkInsEnabled, onCheckedChange = onCheckInToggle, colors = themedSwitchColors())
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                uiState.isLoading -> Unit
                uiState.notifications.isEmpty() ->
                    NotificationsEmptyState(voice = voice, onCreate = onCreateRequest, modifier = Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = FabListBottomClearance),
                    ) {
                        items(uiState.notifications, key = { it.id }) { notification ->
                            val cardState =
                                remember(notification, uiState.events, uiState.durationMode, uiState.caseCreatedAt, now) {
                                    notificationCardState(notification, uiState.events, uiState.durationMode, uiState.caseCreatedAt, now)
                                }
                            NotificationCard(
                                state = cardState,
                                voice = voice,
                                onSetEnabled = { enabled -> onSetEnabled(notification.id, enabled) },
                                onClick = { onCardClick(notification) },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun NotificationsEmptyState(
    voice: Voice,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(voice.notificationsEmptyTitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(voice.notificationsEmptyBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onCreate) { Text(voice.notificationsEmptyCta) }
    }
}

/**
 * One Notification's card: title, settings line (OFTEN only), the always-shown Now line, a
 * last-fired line when set, the enable switch, and — for OFTEN, tier-gated — the comparison line
 * via the existing [ExpectationEarlyCard]/[ExpectationVerdictCard]. A disabled Notification is
 * dimmed (same `alpha` treatment the old `NotificationListItem` used) but still shows its Now line
 * and comparison line (spec: a disabled Notification still shows both). Tapping the card opens the
 * editor prefilled for edit; delete lives inside that editor now, not here.
 */
@Composable
private fun NotificationCard(
    state: NotificationCardState,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (state.kind == NotificationKind.OFTEN) {
                        Text(
                            text =
                                voice.notificationSettingsLine(
                                    lookbackDays = state.lookbackDays ?: 0,
                                    showMeasure = state.tracksDuration,
                                    metric = state.metric,
                                    minIntensity = state.minIntensity,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onSetEnabled,
                    colors = themedSwitchColors(),
                    modifier = Modifier.semantics { contentDescription = voice.notificationToggleDescription(title) },
                )
            }
            Text(text = nowLine(state, voice), style = MaterialTheme.typography.bodyMedium)
            state.firedDaysAgo?.let { daysAgo ->
                Text(
                    text = voice.notificationFiredAgo(daysAgo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val expectation = state.expectation
            val result = state.verdictResult
            if (expectation != null && result != null) {
                if (result.tier == ConfidenceTier.NO_VERDICT) {
                    ExpectationEarlyCard(expectation, result, voice)
                } else {
                    ExpectationVerdictCard(expectation, result, voice)
                }
            }
        }
    }
}

private fun cardTitle(
    state: NotificationCardState,
    voice: Voice,
): String =
    when (state.kind) {
        NotificationKind.OFTEN -> voice.notificationCardTitleOften(state.threshold, state.expectedPer)
        NotificationKind.QUIET -> voice.notificationCardTitleQuiet(state.threshold)
    }

/** The card's always-shown Now line — separate from, and above, the tier-gated comparison line below it. */
private fun nowLine(
    state: NotificationCardState,
    voice: Voice,
): String =
    when (state.kind) {
        NotificationKind.OFTEN ->
            voice.notificationNowLineOften(formatRate(state.observedRate ?: 0.0, state.expectedPer, state.metric))
        NotificationKind.QUIET -> voice.notificationNowLineQuiet(state.silentDays ?: 0L)
    }
