package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.secondmonday.hodith.ui.voice.LocalVoice

/**
 * Shared stand-in for nav destinations not built yet.
 */
@Composable
fun ComingSoonPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(LocalVoice.current.comingSoonPlaceholder)
    }
}
