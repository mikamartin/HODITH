package com.secondmonday.hodith.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secondmonday.hodith.BuildConfig
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.viewmodel.AboutViewModel
import com.secondmonday.hodith.viewmodel.DeveloperModeUnlockEvent
import kotlinx.coroutines.flow.Flow

private const val PRIVACY_POLICY_URL = "https://secondmondaystudios.com/privacy/hodith.html"

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    AboutScreen(
        onBack = onBack,
        unlockEvents = viewModel.unlockEvents,
        onVersionTapped = viewModel::onVersionTapped,
        onOpenPrivacyPolicy = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    unlockEvents: Flow<DeveloperModeUnlockEvent>,
    onVersionTapped: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voice = LocalVoice.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        unlockEvents.collect {
            snackbarHostState.showSnackbar(voice.aboutDeveloperModeUnlockedMessage, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            AboutSection(label = voice.aboutIdeaLabel, body = voice.aboutIdeaBody)
            AboutSection(
                label = voice.aboutVersionLabel,
                body = BuildConfig.VERSION_NAME,
                modifier = Modifier.clickable(onClick = onVersionTapped),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AboutSection(label = voice.aboutPrivacyLabel, body = voice.aboutPrivacyBody)
                TextButton(onClick = onOpenPrivacyPolicy) {
                    Text(voice.aboutPrivacyPolicyLinkLabel)
                }
            }
            AboutSection(label = voice.aboutLicensesLabel, body = voice.aboutLicensesBody)
        }
    }
}

@Composable
private fun AboutSection(
    label: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
