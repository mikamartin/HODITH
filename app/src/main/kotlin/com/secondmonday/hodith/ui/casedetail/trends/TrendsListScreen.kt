package com.secondmonday.hodith.ui.casedetail.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.casedetail.TrendFindingPlank
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.common.InfoIcon
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.TrendsListViewModel

@Composable
fun TrendsListRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrendsListScreen(
        findings = uiState.findings,
        caseIcon = uiState.caseIcon,
        caseName = uiState.caseName,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Spec §10 Trends section: the full (domain-capped, at [com.secondmonday.hodith.domain.TRENDS_MAX_FINDINGS])
 * findings list, reached from the Trends card's "show more" action rather than an in-place expand
 * — the resolved shape for Story C T1's cap/reveal decision. The title names both the section and
 * the Case it belongs to ([caseIcon]/[caseName], the same `"$icon $name"` shape
 * [com.secondmonday.hodith.ui.casedetail.CaseDetailScreen] itself uses) — a bare "Trends" reads
 * ambiguous once you're this deep in the nav stack. Rows are [TrendFindingPlank], not
 * [com.secondmonday.hodith.ui.casedetail.TrendFindingRow] — this screen is the one place Plain's
 * planks apply, matching the Log tab's [com.secondmonday.hodith.ui.casedetail.EventRow] convention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsListScreen(
    findings: List<TrendFinding>,
    caseIcon: String,
    caseName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(title = voice.insightsTrendsInfoTitle, onDismiss = { showInfo = false }) {
            Text(voice.insightsTrendsInfoBody)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(voice.insightsSectionLabelTrends, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$caseIcon $caseName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        InfoIcon(contentDescription = voice.caseSectionInfoDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            findings.forEach { finding -> TrendFindingPlank(finding, voice) }
        }
    }
}

private val previewTrendsListFindings =
    listOf(
        TrendFinding(TrendFindingKind.FREQUENCY_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 20, 8.0, 12.0),
        TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 9, 3.2, 5.8),
        TrendFinding(TrendFindingKind.STREAK_SHIFT, ShiftDirection.DOWN, TrendReliability.HINT, 7, 4.0, 2.0),
        TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.DOWN, TrendReliability.PATTERN, 14, 9.5, 4.0),
    )

@Composable
private fun TrendsListScreenPreviewContent(theme: AppTheme) {
    CompositionLocalProvider(LocalVoice provides voiceFor(theme)) {
        TrendsListScreen(
            findings = previewTrendsListFindings,
            caseIcon = "☕",
            caseName = "Coffee",
            onBack = {},
        )
    }
}

@Preview(name = "Trends list — Plain light", showBackground = true, widthDp = 380, heightDp = 500)
@Composable
private fun TrendsListScreenPlainPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.PLAIN) {
            TrendsListScreenPreviewContent(AppTheme.PLAIN)
        }
    }
}

@Preview(name = "Trends list — Intense light", showBackground = true, widthDp = 380, heightDp = 500)
@Composable
private fun TrendsListScreenIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.INTENSE) {
            TrendsListScreenPreviewContent(AppTheme.INTENSE)
        }
    }
}

@Preview(name = "Trends list — Bright light", showBackground = true, widthDp = 380, heightDp = 500)
@Composable
private fun TrendsListScreenBrightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            TrendsListScreenPreviewContent(AppTheme.BRIGHT)
        }
    }
}
