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
import androidx.compose.material3.RadioButton
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
import com.secondmonday.hodith.viewmodel.SingleCaseWidgetConfigureUiState
import com.secondmonday.hodith.viewmodel.SingleCaseWidgetConfigureViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Single-case widget's mandatory `android:configure` activity (spec §15). Shows a
 * single-select picker — each widget instance is bound to its own Case via this widget's own
 * Glance [CaseIdKey] state (per-instance, not a Case flag), mirroring
 * [ListWidgetConfigureActivity]'s multi-select picker for the List widget.
 */
@AndroidEntryPoint
class SingleCaseWidgetConfigureActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val viewModel: SingleCaseWidgetConfigureViewModel by viewModels()

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
                        SingleCaseWidgetConfigureUiState.Loading -> Unit
                        is SingleCaseWidgetConfigureUiState.Picker ->
                            CasePickerDialog(
                                voice = LocalVoice.current,
                                cases = state.cases,
                                selectedCaseId = state.selectedCaseId,
                                onSelect = viewModel::select,
                                onConfirm = { viewModel.confirmSelection(::finishConfigure) },
                                onDismiss = { finish() },
                            )
                    }
                }
            }
        }
    }

    private fun finishConfigure(caseId: Long) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(applicationContext, glanceId) { prefs -> prefs[CaseIdKey] = caseId }
            // Targeted update(context, glanceId) rather than updateAll(): updateAll() only
            // refreshes widget instances Glance already knows about, and this widget is being
            // configured for the first time — it isn't necessarily in that list yet, so
            // updateAll() can silently miss it. SingleCaseWidget.provideGlance reads CaseIdKey
            // reactively via currentState(), so this call mainly nudges the host to repaint
            // promptly rather than being the only thing that can make the new value visible.
            SingleCaseWidget().update(applicationContext, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun CasePickerDialog(
    voice: Voice,
    cases: List<CaseEntity>,
    selectedCaseId: Long?,
    onSelect: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(voice.singleCaseWidgetConfigureTitle) },
        text = {
            if (cases.isEmpty()) {
                Text(voice.widgetConfigureNoCasesMessage)
            } else {
                LazyColumn {
                    item { Text(voice.singleCaseWidgetConfigureBody) }
                    items(items = cases, key = { it.id }) { case ->
                        CasePickerRow(
                            case = case,
                            selected = case.id == selectedCaseId,
                            onSelect = { onSelect(case.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selectedCaseId != null) {
                Text(voice.singleCaseWidgetConfigureConfirmAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.widgetConfigureSkipAction) }
        },
    )
}

@Composable
private fun CasePickerRow(
    case: CaseEntity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${case.icon}  ${case.name}")
        RadioButton(selected = selected, onClick = onSelect)
    }
}
