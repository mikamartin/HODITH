package com.secondmonday.hodith.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.observationSpanDays
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.HunchTabState
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareInsightsSection
import com.secondmonday.hodith.viewmodel.ShareUiState
import com.secondmonday.hodith.viewmodel.ShareViewModel
import com.secondmonday.hodith.viewmodel.hunchTabState
import com.secondmonday.hodith.viewmodel.insightsTabState
import com.secondmonday.hodith.viewmodel.shareCardState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        onShowHunchVsRealityToggle = viewModel::setShowHunchVsReality,
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
    onShowHunchVsRealityToggle: (Boolean) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.shareOpenDescription) },
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
        val insightsState = insightsTabState(case, uiState.events, now)
        val hunchState = hunchTabState(case, uiState.activeHunch, events, history = emptyList(), now = now)
        val selection = uiState.selection
        val displayName = selection.displayNameOverride ?: case.name

        val cardData =
            shareCardState(
                case = case,
                displayName = displayName,
                insightsState = insightsState,
                hunchState = hunchState,
                eventCount = events.size,
                observedDays = observationSpanDays(events, case.createdAt, now),
                format = selection.format,
                selectedSections = selection.selectedSections,
                showHunchVsReality = selection.showHunchVsReality,
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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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

            SegmentedChoiceRow(
                options =
                    listOf(
                        ShareCardFormat.STORY to voice.shareFormatStoryLabel,
                        ShareCardFormat.SQUARE to voice.shareFormatSquareLabel,
                    ),
                selected = selection.format,
                onSelect = onFormatSelect,
            )

            if (selection.format == ShareCardFormat.STORY && hunchState is HunchTabState.Verdict) {
                ToggleRow(
                    label = voice.shareHunchVsRealityToggleLabel,
                    checked = selection.showHunchVsReality,
                    onCheckedChange = onShowHunchVsRealityToggle,
                )
            }

            OutlinedTextField(
                value = selection.displayNameOverride ?: "",
                onValueChange = onDisplayNameChange,
                label = { Text(voice.shareNameFieldLabel) },
                placeholder = { Text(case.name) },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionsPicker(
                case = case,
                frequencyAvailable = (insightsState as? InsightsTabState.Ready)?.stats?.frequency != null,
                selectedSections = selection.selectedSections,
                voice = voice,
                onSectionToggle = onSectionToggle,
            )

            Button(onClick = onShareClick, modifier = Modifier.fillMaxWidth()) {
                Text(voice.shareOpenDescription)
            }
        }
    }
}

@Composable
private fun SectionsPicker(
    case: CaseEntity,
    frequencyAvailable: Boolean,
    selectedSections: Set<ShareInsightsSection>,
    voice: Voice,
    onSectionToggle: (ShareInsightsSection, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(voice.shareSectionsPickerLabel, style = MaterialTheme.typography.labelLarge)
        availableSections(case, frequencyAvailable).forEach { section ->
            ToggleRow(
                label = sectionLabel(section, voice),
                checked = section in selectedSections,
                onCheckedChange = { onSectionToggle(section, it) },
                modifier = Modifier.testTag(SECTION_TOGGLE_TAG_PREFIX + section.name),
            )
        }
    }
}

/**
 * Frequency is offered only when the Insights tab itself shows it (hidden for a Case with a
 * multi-day event, spec §9); Duration/Intensity only when the Case tracks them — same
 * conditionals as the real Insights tab.
 */
private fun availableSections(
    case: CaseEntity,
    frequencyAvailable: Boolean,
): List<ShareInsightsSection> =
    buildList {
        if (frequencyAvailable) add(ShareInsightsSection.FREQUENCY)
        add(ShareInsightsSection.RHYTHM)
        add(ShareInsightsSection.GAPS)
        add(ShareInsightsSection.TREND)
        if (case.durationMode.tracksDuration) add(ShareInsightsSection.DURATION)
        if (case.intensityEnabled) add(ShareInsightsSection.INTENSITY)
    }

private fun sectionLabel(
    section: ShareInsightsSection,
    voice: Voice,
): String =
    when (section) {
        ShareInsightsSection.FREQUENCY -> voice.insightsSectionLabelFrequency
        ShareInsightsSection.RHYTHM -> voice.insightsSectionLabelRhythm
        ShareInsightsSection.GAPS -> voice.insightsSectionLabelGaps
        ShareInsightsSection.TREND -> voice.insightsSectionLabelTrend
        ShareInsightsSection.DURATION -> voice.insightsSectionLabelDuration
        ShareInsightsSection.INTENSITY -> voice.insightsSectionLabelIntensity
    }

/**
 * The whole row toggles, not just the [Switch] — bigger tap target, and it merges the label and
 * switch into one accessible node (a screen reader announces "Rhythm, Switch, on" instead of an
 * unlabelled switch). [Switch.onCheckedChange] is `null` since [Modifier.toggleable] on the row
 * already owns the click, per Material's guidance for a label+control row.
 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = null)
    }
}
