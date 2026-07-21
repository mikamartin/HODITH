package com.secondmonday.hodith.ui.archivedcases

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.secondmonday.hodith.ui.common.ConfirmDialog
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.ArchivedCaseRow
import com.secondmonday.hodith.viewmodel.ArchivedCasesUiState
import com.secondmonday.hodith.viewmodel.ArchivedCasesViewModel

@Composable
fun ArchivedCasesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchivedCasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ArchivedCasesScreen(
        uiState = uiState,
        onBack = onBack,
        onUnarchive = viewModel::unarchive,
        onDeleteForever = viewModel::deleteForever,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedCasesScreen(
    uiState: ArchivedCasesUiState,
    onBack: () -> Unit,
    onUnarchive: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    var deleteTarget by remember { mutableStateOf<ArchivedCaseRow?>(null) }

    val target = deleteTarget
    if (target != null) {
        ConfirmDialog(
            title = voice.deleteCaseForeverConfirmTitle,
            body = voice.deleteCaseForeverConfirmBody(target.eventCount),
            confirmLabel = voice.deleteCaseForeverConfirmAction,
            cancelLabel = voice.deleteCaseForeverCancelAction,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteForever(target.caseId)
                deleteTarget = null
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.archivedCasesTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> Unit
                uiState.cases.isEmpty() -> {
                    Text(
                        text = voice.archivedCasesEmptyState,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.cases, key = { it.caseId }) { row ->
                            ArchivedCaseListItem(
                                row = row,
                                voice = voice,
                                onUnarchive = { onUnarchive(row.caseId) },
                                onRequestDelete = { deleteTarget = row },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedCaseListItem(
    row: ArchivedCaseRow,
    voice: Voice,
    onUnarchive: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.icon, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.name, style = MaterialTheme.typography.titleMedium)
            Text(text = voice.archivedCaseEventCount(row.eventCount), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onUnarchive) {
            Icon(Icons.Filled.Refresh, contentDescription = voice.unarchiveCaseDescription(row.name))
        }
        IconButton(onClick = onRequestDelete) {
            Icon(Icons.Filled.Delete, contentDescription = voice.deleteCaseForeverDescription(row.name))
        }
    }
}
