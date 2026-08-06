package com.secondmonday.hodith.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.ListWidgetConfigureUiState
import com.secondmonday.hodith.viewmodel.ListWidgetConfigureViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The List widget's mandatory `android:configure` activity (spec §15) — the system launches this
 * automatically every time the widget is added, before it shows any content. Each instance is
 * bound to its own selected Cases via this widget's own Glance [CaseIdsKey] state (per-instance,
 * not a Case flag), mirroring [SingleCaseWidgetConfigureActivity], so the picker always shows.
 */
@AndroidEntryPoint
class ListWidgetConfigureActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val viewModel: ListWidgetConfigureViewModel by viewModels()

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Standard AppWidget convention: cancelled unless/until finishConfigure() runs, so
        // backing out (or process death) leaves the widget host without a placed widget.
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val theme by settingsRepository.observeTheme().collectAsStateWithLifecycle(initialValue = AppTheme.PLAIN)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalVoice provides voiceFor(theme)) {
                HodithTheme(theme = theme) {
                    when (val state = uiState) {
                        ListWidgetConfigureUiState.Loading -> Unit
                        is ListWidgetConfigureUiState.Picker ->
                            CasePickerDialog(
                                voice = LocalVoice.current,
                                cases = state.cases,
                                selectedCaseIds = state.selectedCaseIds,
                                onToggle = viewModel::toggle,
                                onConfirm = { viewModel.confirmSelection(::finishConfigure) },
                                onDismiss = { finish() },
                            )
                    }
                }
            }
        }
    }

    private fun finishConfigure(caseIds: Set<Long>) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(applicationContext, glanceId) { prefs ->
                prefs[CaseIdsKey] = caseIds.map { it.toString() }.toSet()
            }
            // Targeted update(context, glanceId) rather than updateAll(): updateAll() only
            // refreshes widget instances Glance already knows about, and this widget is being
            // configured for the first time — it isn't necessarily in that list yet, so
            // updateAll() can silently miss it. ListWidget.provideGlance reads CaseIdsKey
            // reactively via currentState(), so this call mainly nudges the host to repaint
            // promptly rather than being the only thing that can make the new value visible.
            ListWidget().update(applicationContext, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun CasePickerDialog(
    voice: Voice,
    cases: List<CaseEntity>,
    selectedCaseIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voice.widgetConfigureTitle) },
        text = {
            if (cases.isEmpty()) {
                Text(voice.widgetConfigureNoCasesMessage)
            } else {
                LazyColumn {
                    item { Text(voice.widgetConfigureBody) }
                    items(items = cases, key = { it.id }) { case ->
                        CasePickerRow(
                            case = case,
                            checked = case.id in selectedCaseIds,
                            onCheckedChange = { onToggle(case.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(voice.widgetConfigureConfirmAction) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.widgetConfigureSkipAction) }
        },
    )
}

@Composable
private fun CasePickerRow(
    case: CaseEntity,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${case.icon}  ${case.name}")
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
