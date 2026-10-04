package com.secondmonday.hodith.ui.share

import android.content.Intent
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareInsightsSection
import com.secondmonday.hodith.viewmodel.ShareUiState
import com.secondmonday.hodith.viewmodel.ShareViewModel
import com.secondmonday.hodith.viewmodel.availableShareSections
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.shareCardState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private const val SHARE_MIME_TYPE = "image/png"

/** Test hook only — `onNodeWithText` is ambiguous once a section's label also appears in the live card preview above. */
internal const val SECTION_TOGGLE_TAG_PREFIX = "section_toggle_"

@Composable
fun SharePreviewRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.shareRequests.collectLatest { uri ->
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = SHARE_MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    SharePreviewScreen(
        uiState = uiState,
        now = viewModel.nowMillis(),
        graphicsLayer = graphicsLayer,
        onBack = onBack,
        onFormatSelect = viewModel::setFormat,
        onDisplayNameChange = viewModel::setDisplayNameOverride,
        onSectionToggle = viewModel::setSectionSelected,
        onShareClick = { scope.launch { viewModel.share(graphicsLayer.toImageBitmap().asAndroidBitmap()) } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePreviewScreen(
    uiState: ShareUiState,
    now: Long,
    graphicsLayer: GraphicsLayer,
    onBack: () -> Unit,
    onFormatSelect: (ShareCardFormat) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.shareInsightScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        val case = uiState.case
        if (uiState.isLoading || case == null) return@Scaffold

        val events = uiState.events.map { it.event }
        // insightsTabState only cares about "now" down to the calendar day (see CaseDetailScreen.kt's
        // matching comment) -- memoizing on that day, not the raw value `now` returns fresh on every
        // recomposition, keeps this screen's own recomposition (e.g. every keystroke while editing the
        // display name, every section toggle) from re-running the full stats/Trends computation, now
        // including Story C T4's permutation test.
        val today = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate() }
        val insightsState = remember(case, uiState.events, today) { insightsTabState(case, uiState.events, now) }
        val selection = uiState.selection
        val displayName = selection.displayNameOverride ?: case.name

        val cardData =
            shareCardState(
                case = case,
                displayName = displayName,
                insightsState = insightsState,
                eventCount = events.size,
                observedDays = observationSpanDays(events, case.createdAt, now),
                format = selection.format,
                selectedSections = selection.selectedSections,
                generatedAtMillis = now,
            )

        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Controls first, preview second (matches Log Share's own screen order) — a long
            // sections list would otherwise crowd the controls below the fold.
            SegmentedChoiceRow(
                options =
                    listOf(
                        ShareCardFormat.SQUARE to voice.shareFormatSquareLabel,
                        ShareCardFormat.STORY to voice.shareFormatStoryLabel,
                    ),
                selected = selection.format,
                onSelect = onFormatSelect,
            )

            ShareNameField(
                caseName = case.name,
                displayNameOverride = selection.displayNameOverride,
                label = voice.shareNameFieldLabel,
                onDisplayNameChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
            )

            // Square is a fixed preset built from the Case's own settings; only Story is customizable.
            if (selection.format == ShareCardFormat.STORY) {
                SectionsPicker(
                    availableSections = availableShareSections((insightsState as? InsightsTabState.Ready)?.stats),
                    selectedSections = selection.selectedSections,
                    voice = voice,
                    onSectionToggle = onSectionToggle,
                )
            }

            // The card sits on its own tinted stage below a divider, so the controls above read as
            // settings and the card as the thing being shared. Only the card is captured for export.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShareCardTemplate(
                    data = cardData,
                    voice = voice,
                    modifier =
                        Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                )
            }

            Button(onClick = onShareClick, modifier = Modifier.fillMaxWidth()) {
                Text(voice.shareOpenDescription)
            }
        }
    }
}

/** [availableSections] arrives in card order from [availableShareSections], so only sections the Case has data for get a row; with none, the picker is omitted. */
@Composable
private fun SectionsPicker(
    availableSections: List<ShareInsightsSection>,
    selectedSections: Set<ShareInsightsSection>,
    voice: Voice,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
) {
    if (availableSections.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(voice.shareSectionsPickerLabel, style = MaterialTheme.typography.labelLarge)
        availableSections.forEach { section ->
            ToggleRow(
                label = sectionLabel(section, voice),
                checked = section in selectedSections,
                onCheckedChange = { onSectionToggle(section, it) },
                modifier = Modifier.testTag(SECTION_TOGGLE_TAG_PREFIX + section.name),
            )
        }
    }
}

/** Each row reads as the title of the card section it toggles. */
internal fun sectionLabel(
    section: ShareInsightsSection,
    voice: Voice,
): String =
    when (section) {
        ShareInsightsSection.GAPS -> voice.shareGapsTitle
        ShareInsightsSection.STREAKS -> voice.shareStreaksTitle
        ShareInsightsSection.DURATION -> voice.shareDurationTitle
        ShareInsightsSection.RHYTHM -> voice.insightsSectionLabelRhythmStarts
        ShareInsightsSection.INTENSITY -> voice.insightsSectionLabelIntensity
        ShareInsightsSection.TRENDS -> voice.insightsSectionLabelTrends
        ShareInsightsSection.TAGS -> voice.shareTopTagsTitle
    }
