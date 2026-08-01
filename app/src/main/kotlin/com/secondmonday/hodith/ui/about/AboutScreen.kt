package com.secondmonday.hodith.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.BuildConfig
import com.secondmonday.hodith.ui.voice.LocalVoice

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AboutScreen(onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(voice.aboutScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AboutSection(label = voice.aboutVersionLabel, body = BuildConfig.VERSION_NAME)
            AboutSection(label = voice.aboutPrivacyLabel, body = voice.aboutPrivacyBody)
            AboutSection(label = voice.aboutLicensesLabel, body = voice.aboutLicensesBody)
        }
    }
}

@Composable
private fun AboutSection(
    label: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
