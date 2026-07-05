package com.secondmonday.hodith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.secondmonday.hodith.ui.timeline.TimelineGrid
import com.secondmonday.hodith.ui.timeline.TimelineViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val timelineViewModel: TimelineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val rows by timelineViewModel.rows.collectAsState()
                    TimelineGrid(
                        rows = rows,
                        initialWindow = timelineViewModel.initialWindow,
                        onDotTap = { _, _ -> },
                        onCaseTap = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
