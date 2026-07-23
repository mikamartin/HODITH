package com.secondmonday.hodith.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.nav.HodithNavHost
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor
import kotlinx.coroutines.flow.Flow

@Composable
fun HodithApp(themeFlow: Flow<AppTheme>) {
    val theme by themeFlow.collectAsStateWithLifecycle(initialValue = AppTheme.SERIOUS)

    CompositionLocalProvider(LocalVoice provides voiceFor(theme)) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                HodithNavHost(modifier = Modifier.fillMaxSize().safeDrawingPadding())
            }
        }
    }
}
