package com.secondmonday.hodith.ui.home

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
import androidx.compose.material3.MaterialTheme
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
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    Box(modifier = modifier.fillMaxSize()) {
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
                        HomeCaseListItem(row = row, voice = voice)
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
