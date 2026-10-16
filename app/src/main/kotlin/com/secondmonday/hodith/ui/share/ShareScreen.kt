package com.secondmonday.hodith.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.data.pdf.HistoryPdfContent
import com.secondmonday.hodith.data.pdf.InsightsPdfContent
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.casedetail.FrequencyCard
import com.secondmonday.hodith.ui.common.HodithTab
import com.secondmonday.hodith.ui.common.HodithTabRow
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CASE_NAME_MAX_LENGTH
import com.secondmonday.hodith.viewmodel.HistoryShareUiState
import com.secondmonday.hodith.viewmodel.HistoryShareViewModel
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareUiState
import com.secondmonday.hodith.viewmodel.ShareViewModel
import com.secondmonday.hodith.viewmodel.formatCardTimestamp
import com.secondmonday.hodith.viewmodel.historyPdfContent
import com.secondmonday.hodith.viewmodel.insightsPdfStatLines
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.pdfAppLinkText
import com.secondmonday.hodith.viewmodel.pdfDateRangeBounds
import com.secondmonday.hodith.viewmodel.shareCardState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val IMAGE_MIME_TYPE = "image/png"
private const val PDF_MIME_TYPE = "application/pdf"

/** Targets roughly 200dpi at the PDF's own content width/chart-box height (feature/pdf-export) - sharp enough to print, validated by the chart-legibility spike this replaced. */
private const val PDF_CHART_CAPTURE_WIDTH_PX = 1440
private const val PDF_CHART_CAPTURE_HEIGHT_PX = 600

/** The Share screen's three tabs. Summary and Insights are the two formats of one card; History is the Case's log. */
internal enum class ShareTab { SUMMARY, INSIGHTS, HISTORY }

/**
 * The Share screen's route. Both share ViewModels are scoped to this route's back-stack entry, so every tab sees the
 * same instance: section picks and their order carry across tab switches, and the name is held one level up.
 */
@Composable
fun ShareRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    insightViewModel: ShareViewModel = hiltViewModel(),
    historyViewModel: HistoryShareViewModel = hiltViewModel(),
) {
    val insightState by insightViewModel.uiState.collectAsStateWithLifecycle()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    val chartGraphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    // Mirrors ShareScreen's own check (it needs the same answer to decide whether to render the
    // off-screen chart capture at all): a chart bitmap is only ever read back when one was
    // actually recorded - chartGraphicsLayer.toImageBitmap() on a layer nothing drew into would
    // be reading content that was never rendered.
    val hasFrequencyChart =
        insightState.case?.let { case ->
            (insightsTabState(case, insightState.events, insightViewModel.nowMillis()) as? InsightsTabState.Ready)?.stats?.frequency !=
                null
        }
            ?: false

    LaunchedEffect(Unit) {
        merge(insightViewModel.shareRequests, historyViewModel.shareRequests).collectLatest { uri ->
            launchShareSheet(context, uri, IMAGE_MIME_TYPE)
        }
    }
    LaunchedEffect(Unit) {
        merge(insightViewModel.pdfRequests, historyViewModel.pdfRequests).collectLatest { uri ->
            launchShareSheet(context, uri, PDF_MIME_TYPE)
        }
    }

    ShareScreen(
        insightState = insightState,
        historyState = historyState,
        now = insightViewModel.nowMillis(),
        graphicsLayer = graphicsLayer,
        chartGraphicsLayer = chartGraphicsLayer,
        onBack = onBack,
        onSectionToggle = insightViewModel::setSectionSelected,
        onSectionMove = insightViewModel::moveSection,
        onInsightShareClick = { scope.launch { insightViewModel.share(graphicsLayer.toImageBitmap().asAndroidBitmap()) } },
        onInsightSharePdfClick = { content ->
            scope.launch {
                val chartBitmap = if (hasFrequencyChart) chartGraphicsLayer.toImageBitmap().asAndroidBitmap() else null
                insightViewModel.sharePdf(content.copy(chartBitmap = chartBitmap))
            }
        },
        onDateFromPicked = historyViewModel::setDateFrom,
        onDateToPicked = historyViewModel::setDateTo,
        onFieldToggle = historyViewModel::setFieldSelected,
        onHistorySortOrderSelect = historyViewModel::setSortOrder,
        onHistoryShareClick = { scope.launch { historyViewModel.share(graphicsLayer.toImageBitmap().asAndroidBitmap()) } },
        onHistorySharePdfClick = historyViewModel::sharePdf,
        modifier = modifier,
    )
}

private fun launchShareSheet(
    context: Context,
    uri: Uri,
    mimeType: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    insightState: ShareUiState,
    historyState: HistoryShareUiState,
    now: Long,
    graphicsLayer: GraphicsLayer,
    chartGraphicsLayer: GraphicsLayer,
    onBack: () -> Unit,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
    onSectionMove: (available: List<ShareInsightsSection>, from: Int, to: Int) -> Unit,
    onInsightShareClick: () -> Unit,
    onInsightSharePdfClick: (InsightsPdfContent) -> Unit,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    onFieldToggle: (HistoryRowField, Boolean) -> Unit,
    onHistorySortOrderSelect: (ChronologicalOrder) -> Unit,
    onHistoryShareClick: () -> Unit,
    onHistorySharePdfClick: (HistoryPdfContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val use24Hour = LocalTimeFormat.current.is24Hour
    var tab by rememberSaveable { mutableStateOf(ShareTab.SUMMARY) }
    // Null until the field is edited, so an untouched field shows the Case's own name. Held here, above the tabs,
    // so a switch between them never clears what was typed.
    var typedName by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        // Plain only: same rule as CaseEditScreen. The controls sit on white (surface), so the tinted preview stage
        // below them reads as a distinct block. Intense and Bright keep the tinted background.
        containerColor =
            if (LocalCardDecorationStyle.current == CardDecorationStyle.PLAIN) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background
            },
        topBar = {
            TopAppBar(
                title = { Text(voice.shareScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        val case = insightState.case
        if (insightState.isLoading || historyState.isLoading || case == null || historyState.case == null) return@Scaffold

        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            HodithTabRow {
                HodithTab(
                    selected = tab == ShareTab.SUMMARY,
                    onClick = { tab = ShareTab.SUMMARY },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(voice.shareTabSummaryLabel)
                }
                HodithTab(
                    selected = tab == ShareTab.INSIGHTS,
                    onClick = { tab = ShareTab.INSIGHTS },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(voice.shareTabInsightsLabel)
                }
                HodithTab(
                    selected = tab == ShareTab.HISTORY,
                    onClick = { tab = ShareTab.HISTORY },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(voice.shareTabHistoryLabel)
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ShareNameField(
                    value = typedName ?: case.name,
                    label = voice.shareNameFieldLabel,
                    onValueChange = { typedName = it.take(CASE_NAME_MAX_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                )

                val displayName = shareDisplayName(typedName, case.name)
                var insightsPdfMode by rememberSaveable { mutableStateOf(false) }
                var historyPdfMode by rememberSaveable { mutableStateOf(false) }
                // Recomputed independently of InsightShareTab's own internal copy (same cheap,
                // memoization-friendly call - see its comment) because only this scope has what
                // feature/pdf-export's PDF button needs: displayName and the resolved Voice, both
                // local to this composable, not the ViewModel.
                val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
                val pdfInsightsState = remember(case, insightState.events, today) { insightsTabState(case, insightState.events, now) }
                val pdfFrequency = (pdfInsightsState as? InsightsTabState.Ready)?.stats?.frequency

                fun buildInsightsPdfContent(): InsightsPdfContent {
                    val cardData =
                        shareCardState(
                            case = case,
                            displayName = displayName,
                            insightsState = pdfInsightsState,
                            eventCount = insightState.events.size,
                            observedDays = observationSpanDays(insightState.events.map { it.event }, case.createdAt, now),
                            format = ShareCardFormat.STORY,
                            selectedSections = insightState.selection.selectedSections,
                            generatedAtMillis = now,
                            sectionOrder = insightState.sectionOrder,
                        )
                    val (fromText, toText) = pdfDateRangeBounds(case.createdAt, null, now)
                    return InsightsPdfContent(
                        documentTitle = voice.sharePdfDocumentTitle,
                        caseName = displayName,
                        kicker = voice.shareTabInsightsLabel,
                        countedWithText = voice.sharePdfGeneratedLine(formatCardTimestamp(now, use24Hour)),
                        dateRangeLabel = voice.sharePdfDateRangeLabel,
                        dateRangeText = voice.shareHistoryRangeNote(fromText, toText),
                        appLinkText = pdfAppLinkText(),
                        statLines = insightsPdfStatLines(cardData, voice),
                        chartBitmap = null,
                    )
                }

                fun buildHistoryPdfContent(): HistoryPdfContent =
                    historyPdfContent(
                        case = case,
                        displayName = displayName,
                        events = historyState.events,
                        sortOrder = historyState.selection.sortOrder,
                        dateFrom = historyState.selection.dateFrom,
                        dateTo = historyState.selection.dateTo,
                        fields = historyState.selection.fields,
                        use24Hour = use24Hour,
                        voice = voice,
                        now = now,
                        generatedAtMillis = now,
                    )

                when (tab) {
                    ShareTab.SUMMARY ->
                        InsightShareTab(
                            format = ShareCardFormat.SQUARE,
                            uiState = insightState,
                            now = now,
                            displayName = displayName,
                            graphicsLayer = graphicsLayer,
                            onSectionToggle = onSectionToggle,
                            onSectionMove = onSectionMove,
                        )
                    ShareTab.INSIGHTS -> {
                        InsightShareTab(
                            format = ShareCardFormat.STORY,
                            uiState = insightState,
                            now = now,
                            displayName = displayName,
                            graphicsLayer = graphicsLayer,
                            onSectionToggle = onSectionToggle,
                            onSectionMove = onSectionMove,
                            pdfMode = insightsPdfMode,
                            onPdfModeChange = { insightsPdfMode = it },
                        )
                        // Off-screen (see OffscreenCapture/OffscreenCaptureTest) - rendered at the
                        // PDF's own content width, not the phone's, so every tick label fits
                        // instead of the mobile card's sparser label spacing. Only kept hot while
                        // PDF mode is actually on, so flipping the toggle is what starts it recording.
                        if (insightsPdfMode && pdfFrequency != null) {
                            OffscreenCapture(
                                graphicsLayer = chartGraphicsLayer,
                                widthPx = PDF_CHART_CAPTURE_WIDTH_PX,
                                heightPx = PDF_CHART_CAPTURE_HEIGHT_PX,
                            ) {
                                FrequencyCard(pdfFrequency, null, {}, voice, tickCountOverride = pdfFrequency.bars.size)
                            }
                        }
                    }
                    ShareTab.HISTORY ->
                        HistoryShareTab(
                            uiState = historyState,
                            now = now,
                            displayName = displayName,
                            graphicsLayer = graphicsLayer,
                            pdfMode = historyPdfMode,
                            onPdfModeChange = { historyPdfMode = it },
                            onDateFromPicked = onDateFromPicked,
                            onDateToPicked = onDateToPicked,
                            onFieldToggle = onFieldToggle,
                            onSortOrderSelect = onHistorySortOrderSelect,
                        )
                }

                // One button per tab, same shape as Summary's: Insights/History route it to the
                // image or PDF handler depending on their own toggle instead of showing a second
                // button (feature/pdf-export).
                val onShareClick =
                    when (tab) {
                        ShareTab.SUMMARY -> onInsightShareClick
                        ShareTab.INSIGHTS -> {
                            { if (insightsPdfMode) onInsightSharePdfClick(buildInsightsPdfContent()) else onInsightShareClick() }
                        }
                        ShareTab.HISTORY -> {
                            { if (historyPdfMode) onHistorySharePdfClick(buildHistoryPdfContent()) else onHistoryShareClick() }
                        }
                    }
                Button(onClick = onShareClick, modifier = Modifier.fillMaxWidth()) {
                    Text(voice.shareOpenDescription)
                }
            }
        }
    }
}

/**
 * The preview block every tab shares: one tinted stage holding a "Preview" heading and the card, so the heading reads as
 * part of the preview rather than a separate label. The stage uses `surfaceVariant`, not a surfaceContainer tier: on Plain
 * every surfaceContainer tier is white, which matched the screen background. Only the card is captured for export,
 * through the modifier [card] is handed.
 */
@Composable
internal fun SharePreviewStage(
    voice: Voice,
    graphicsLayer: GraphicsLayer,
    card: @Composable (captureModifier: Modifier) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                voice.sharePreviewLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start),
            )
            card(
                Modifier.drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
            )
        }
    }
}
