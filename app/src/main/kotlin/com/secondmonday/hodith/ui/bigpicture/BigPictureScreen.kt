package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.secondmonday.hodith.viewmodel.BigPictureUiState
import com.secondmonday.hodith.viewmodel.BigPictureViewModel

@Composable
fun BigPictureRoute(
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BigPictureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BigPictureScreen(uiState = uiState, onOpenCase = onOpenCase, modifier = modifier)
}

@Composable
fun BigPictureScreen(
    uiState: BigPictureUiState,
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Unit
            uiState.cases.isEmpty() -> {
                Text(text = voice.noCasesEmptyState, modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                // Spec §9: at least one Case always renders the grid, even with zero events logged
                // anywhere — the note above it is the only thing that depends on data presence,
                // and it clears itself the moment a single event exists.
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.events.isEmpty()) {
                        Text(
                            text = voice.bigPictureEarlyDays,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    BigPictureGrid(
                        earliestMonth = uiState.earliestMonth ?: uiState.currentMonth!!,
                        currentMonth = uiState.currentMonth!!,
                        cases = uiState.cases,
                        events = uiState.events,
                        today = uiState.today!!,
                        onOpenCase = onOpenCase,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
