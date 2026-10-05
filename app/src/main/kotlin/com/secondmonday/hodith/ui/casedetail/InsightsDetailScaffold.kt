package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.common.InfoIcon
import com.secondmonday.hodith.ui.voice.LocalVoice

/**
 * The shared chrome for an Insights card's full-list screen (Trends, Tags): a top bar titled with
 * the section and the Case it belongs to ([caseIcon]/[caseName], the same `"$icon $name"` shape
 * [com.secondmonday.hodith.ui.casedetail.CaseDetailScreen] uses), a back arrow, and an info icon
 * opening a dialog with [infoTitle]/[infoBody]. [content] fills a scrolling, padded column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsightsDetailScaffold(
    sectionLabel: String,
    caseIcon: String,
    caseName: String,
    infoTitle: String,
    infoBody: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val voice = LocalVoice.current
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(title = infoTitle, onDismiss = { showInfo = false }) {
            Text(infoBody)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(sectionLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$caseIcon $caseName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = voice.backButtonDescription)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        InfoIcon(contentDescription = voice.caseSectionInfoDescription)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
