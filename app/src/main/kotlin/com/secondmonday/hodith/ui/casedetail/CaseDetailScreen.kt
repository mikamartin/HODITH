package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.ui.logsheet.LogDetailSheet
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CaseDetailUiState
import com.secondmonday.hodith.viewmodel.CaseDetailViewModel
import com.secondmonday.hodith.viewmodel.LogDraft
import com.secondmonday.hodith.viewmodel.draftFrom
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatEventTime

@Composable
fun CaseDetailRoute(
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CaseDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEditCase = onEditCase,
        onDeleteEvent = viewModel::deleteEvent,
        newEventDraft = viewModel::newEventDraft,
        onSaveEvent = viewModel::saveEvent,
        nowMillis = viewModel::nowMillis,
        modifier = modifier,
    )
}

private data class EditRequest(
    val event: EventEntity?,
    val originalTags: List<TagEntity>,
    val now: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    uiState: CaseDetailUiState,
    onBack: () -> Unit,
    onEditCase: (Long) -> Unit,
    onDeleteEvent: (EventEntity) -> Unit,
    newEventDraft: () -> LogDraft,
    onSaveEvent: (LogDraft, EventEntity?, List<TagEntity>) -> Unit,
    nowMillis: () -> Long,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val case = uiState.case
    val now = nowMillis()
    var editRequest by remember { mutableStateOf<EditRequest?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(case?.let { "${it.icon} ${it.name}" }.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
                actions = {
                    if (case != null) {
                        IconButton(onClick = { onEditCase(case.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = voice.caseDetailEditDescription)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(voice.retroLogEntryLabel) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { editRequest = EditRequest(event = null, originalTags = emptyList(), now = now) },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> Unit
                uiState.events.isEmpty() -> {
                    Text(text = voice.eventListEmptyState, modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.events, key = { it.event.id }) { eventWithTags ->
                            EventRow(
                                eventWithTags = eventWithTags,
                                now = now,
                                voice = voice,
                                onClick = {
                                    editRequest = EditRequest(event = eventWithTags.event, originalTags = eventWithTags.tags, now = now)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val request = editRequest
    if (case != null && request != null) {
        LogDetailSheet(
            isEditing = request.event != null,
            durationMode = case.durationMode,
            intensityEnabled = case.intensityEnabled,
            initialDraft = request.event?.let { draftFrom(it, now = 0L, tags = request.originalTags) } ?: newEventDraft(),
            tagSuggestions = uiState.tagSuggestions,
            now = request.now,
            onSave = { draft ->
                onSaveEvent(draft, request.event, request.originalTags)
                editRequest = null
            },
            onDismiss = { editRequest = null },
            onDelete =
                request.event?.let { event ->
                    {
                        onDeleteEvent(event)
                        editRequest = null
                    }
                },
        )
    }
}

@Composable
private fun EventRow(
    eventWithTags: EventWithTags,
    now: Long,
    voice: Voice,
    onClick: () -> Unit,
) {
    val event = eventWithTags.event

    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = formatEventTime(event.occurredAt, now), style = MaterialTheme.typography.bodyLarge)
        val details = eventDetailSummary(event, eventWithTags.tags, voice)
        if (details != null) {
            Text(text = details, style = MaterialTheme.typography.bodySmall)
        }
    }
}
