package com.secondmonday.hodith.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.HomeCaseRow
import com.secondmonday.hodith.viewmodel.HomeUiState
import com.secondmonday.hodith.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    onNewCase: () -> Unit,
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState = uiState, onNewCase = onNewCase, onOpenCase = onOpenCase, modifier = modifier)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNewCase: () -> Unit,
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onNewCase) {
                Icon(Icons.Filled.Add, contentDescription = voice.newCaseFabDescription)
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            when {
                uiState.isLoading -> Unit
                uiState.cases.isEmpty() -> {
                    Text(
                        text = voice.noCasesEmptyState,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.cases, key = { it.caseId }) { row ->
                            HomeCaseListItem(row = row, voice = voice, onClick = { onOpenCase(row.caseId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCaseListItem(
    row: HomeCaseRow,
    voice: Voice,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.icon, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = voice.homeCaseCounts(row.todayCount, row.weekCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
