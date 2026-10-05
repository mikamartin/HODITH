package com.secondmonday.hodith.ui.casedetail.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.ui.casedetail.InsightsCard
import com.secondmonday.hodith.ui.casedetail.InsightsDetailScaffold
import com.secondmonday.hodith.ui.casedetail.InsightsDrillDownDialog
import com.secondmonday.hodith.ui.casedetail.StatRow
import com.secondmonday.hodith.ui.casedetail.TagsSummary
import com.secondmonday.hodith.ui.casedetail.eventsWithTag
import com.secondmonday.hodith.ui.common.rememberTickingNow
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.viewmodel.TagsListViewModel

@Composable
fun TagsListRoute(
    onBack: () -> Unit,
    onEditEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TagsListScreen(
        totalEventCount = uiState.totalEventCount,
        distinctTagCount = uiState.distinctTagCount,
        tags = uiState.tags,
        eventsWithTags = uiState.eventsWithTags,
        durationMode = uiState.durationMode,
        caseIcon = uiState.caseIcon,
        caseName = uiState.caseName,
        nowMillis = viewModel::nowMillis,
        onBack = onBack,
        onEditEvent = onEditEvent,
        modifier = modifier,
    )
}

/**
 * Insights tag card's full-list screen: every tag with its event count, reached from the card's
 * "see all" link (the same navigate-not-expand shape as [com.secondmonday.hodith.ui.casedetail.trends.TrendsListScreen]).
 * Rows open the same drill-down dialog as the card, so the events behind a tag are one tap away from
 * either surface.
 */
@Composable
fun TagsListScreen(
    totalEventCount: Int,
    distinctTagCount: Int,
    tags: List<TagBreakdownEntry>,
    eventsWithTags: List<EventWithTags>,
    durationMode: DurationMode,
    caseIcon: String,
    caseName: String,
    nowMillis: () -> Long,
    onBack: () -> Unit,
    onEditEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val now by rememberTickingNow(clockNow = nowMillis)
    var selectedTag by remember { mutableStateOf<String?>(null) }
    InsightsDetailScaffold(
        sectionLabel = voice.insightsSectionLabelTags,
        caseIcon = caseIcon,
        caseName = caseName,
        infoTitle = voice.insightsTagsInfoTitle,
        infoBody = voice.insightsTagsInfoBody,
        onBack = onBack,
        modifier = modifier,
    ) {
        InsightsCard {
            TagsSummary(totalEventCount, distinctTagCount, voice)
            tags.forEach { tag ->
                StatRow(
                    label = tag.tagName,
                    value = tag.count.toString(),
                    onClick = { selectedTag = tag.tagName },
                    contentDescription = voice.insightsTagRowTapDescription(tag.tagName),
                )
            }
        }
    }
    selectedTag?.let { tagName ->
        InsightsDrillDownDialog(
            title = voice.insightsTagDrillDownTitle(tagName),
            events = eventsWithTag(eventsWithTags, tagName),
            now = now,
            durationMode = durationMode,
            voice = voice,
            suppressTagName = tagName,
            onEditEvent = onEditEvent,
            onDismiss = { selectedTag = null },
        )
    }
}
