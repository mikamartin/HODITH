package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.viewmodel.BigPictureUiState
import com.secondmonday.hodith.viewmodel.BigPictureViewModel

@Composable
fun BigPictureRoute(
    modifier: Modifier = Modifier,
    viewModel: BigPictureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BigPictureScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun BigPictureScreen(
    uiState: BigPictureUiState,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Unit
            uiState.cases.isEmpty() -> {
                Text(text = voice.noCasesEmptyState, modifier = Modifier.align(Alignment.Center))
            }
            uiState.events.isEmpty() -> {
                Text(text = voice.bigPictureEarlyDays, modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                BigPictureGrid(
                    earliestMonth = uiState.earliestMonth ?: uiState.currentMonth!!,
                    currentMonth = uiState.currentMonth!!,
                    cases = uiState.cases,
                    events = uiState.events,
                    today = uiState.today!!,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
