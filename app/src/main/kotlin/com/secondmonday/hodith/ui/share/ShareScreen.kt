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
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.ui.common.HodithTab
import com.secondmonday.hodith.ui.common.HodithTabRow
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CASE_NAME_MAX_LENGTH
import com.secondmonday.hodith.viewmodel.LogShareUiState
import com.secondmonday.hodith.viewmodel.LogShareViewModel
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareUiState
import com.secondmonday.hodith.viewmodel.ShareViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val SHARE_MIME_TYPE = "image/png"

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
    logViewModel: LogShareViewModel = hiltViewModel(),
) {
    val insightState by insightViewModel.uiState.collectAsStateWithLifecycle()
    val logState by logViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        merge(insightViewModel.shareRequests, logViewModel.shareRequests).collectLatest { uri ->
            launchShareSheet(context, uri)
        }
    }

    ShareScreen(
        insightState = insightState,
        logState = logState,
        now = insightViewModel.nowMillis(),
        graphicsLayer = graphicsLayer,
        onBack = onBack,
        onSectionToggle = insightViewModel::setSectionSelected,
        onSectionMove = insightViewModel::moveSection,
        onInsightShareClick = { scope.launch { insightViewModel.share(graphicsLayer.toImageBitmap().asAndroidBitmap()) } },
        onDateFromPicked = logViewModel::setDateFrom,
        onDateToPicked = logViewModel::setDateTo,
        onFieldToggle = logViewModel::setFieldSelected,
        onLogSortOrderSelect = logViewModel::setSortOrder,
        onLogShareClick = { scope.launch { logViewModel.share(graphicsLayer.toImageBitmap().asAndroidBitmap()) } },
        modifier = modifier,
    )
}

private fun launchShareSheet(
    context: Context,
    uri: Uri,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    insightState: ShareUiState,
    logState: LogShareUiState,
    now: Long,
    graphicsLayer: GraphicsLayer,
    onBack: () -> Unit,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
    onSectionMove: (available: List<ShareInsightsSection>, from: Int, to: Int) -> Unit,
    onInsightShareClick: () -> Unit,
    onDateFromPicked: (LocalDate?) -> Unit,
    onDateToPicked: (LocalDate) -> Unit,
    onFieldToggle: (LogRowField, Boolean) -> Unit,
    onLogSortOrderSelect: (ChronologicalOrder) -> Unit,
    onLogShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
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
        if (insightState.isLoading || logState.isLoading || case == null || logState.case == null) return@Scaffold

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
                    ShareTab.INSIGHTS ->
                        InsightShareTab(
                            format = ShareCardFormat.STORY,
                            uiState = insightState,
                            now = now,
                            displayName = displayName,
                            graphicsLayer = graphicsLayer,
                            onSectionToggle = onSectionToggle,
                            onSectionMove = onSectionMove,
                        )
                    ShareTab.HISTORY ->
                        LogShareTab(
                            uiState = logState,
                            now = now,
                            displayName = displayName,
                            graphicsLayer = graphicsLayer,
                            onDateFromPicked = onDateFromPicked,
                            onDateToPicked = onDateToPicked,
                            onFieldToggle = onFieldToggle,
                            onSortOrderSelect = onLogSortOrderSelect,
                        )
                }

                Button(
                    onClick = if (tab == ShareTab.HISTORY) onLogShareClick else onInsightShareClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
