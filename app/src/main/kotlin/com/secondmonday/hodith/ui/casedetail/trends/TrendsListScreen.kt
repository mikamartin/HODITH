package com.secondmonday.hodith.ui.casedetail.trends

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.ui.casedetail.InsightsDetailScaffold
import com.secondmonday.hodith.ui.casedetail.TrendFindingPlank
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.TrendsListViewModel
import java.time.LocalDate

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
@Composable
fun TrendsListScreen(
    findings: List<TrendFinding>,
    caseIcon: String,
    caseName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    InsightsDetailScaffold(
        sectionLabel = voice.insightsSectionLabelTrends,
        caseIcon = caseIcon,
        caseName = caseName,
        infoTitle = voice.insightsTrendsInfoTitle,
        infoBody = voice.insightsTrendsInfoBody,
        onBack = onBack,
        modifier = modifier,
    ) {
        findings.forEach { finding -> TrendFindingPlank(finding, voice) }
    }
}

private val previewTrendsListFindings =
    listOf(
        TrendFinding(TrendFindingKind.FREQUENCY_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 20, 8.0, 12.0, latestEvidenceAt = 0L),
        TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 9, 3.2, 5.8, latestEvidenceAt = 0L),
        TrendFinding(TrendFindingKind.STREAK_SHIFT, ShiftDirection.DOWN, TrendReliability.HINT, 7, 4.0, 2.0, latestEvidenceAt = 0L),
        TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.DOWN, TrendReliability.PATTERN, 14, 9.5, 4.0, latestEvidenceAt = 0L),
        TrendFinding(TrendFindingKind.RECURRENCE_SHAPE, ShiftDirection.UP, TrendReliability.HINT, 11, 3.0, 0.73, latestEvidenceAt = 0L),
        TrendFinding(
            latestEvidenceAt = 0L,
            kind = TrendFindingKind.CHANGE_POINT,
            direction = ShiftDirection.UP,
            reliability = TrendReliability.PATTERN,
            sampleCount = 20,
            priorValue = 3.0,
            recentValue = 9.0,
            changePointDate = LocalDate.of(2026, 3, 14),
        ),
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
