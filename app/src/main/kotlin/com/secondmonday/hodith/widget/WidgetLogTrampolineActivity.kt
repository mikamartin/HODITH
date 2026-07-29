package com.secondmonday.hodith.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.ui.logsheet.LogDetailSheet
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.WidgetLogSheetViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Shared with [ListWidget]'s `actionStartActivity` parameters key for this same extra. */
internal const val EXTRA_CASE_ID = "com.secondmonday.hodith.widget.EXTRA_CASE_ID"

/**
 * The widget's DETAIL_SHEET entry point (spec §15) — a transparent, otherwise-invisible Activity
 * whose only job is to host the same [LogDetailSheet] used in-app, then finish. Unlike the
 * widget's own chrome (fixed to [com.secondmonday.hodith.ui.voice.PlainVoice], see
 * [ListWidget]), this sheet reads the user's real theme/voice via [SettingsRepository] — once
 * we're inside an actual app screen rather than Glance's RemoteViews, there's no reason to
 * suppress it.
 */
@AndroidEntryPoint
class WidgetLogTrampolineActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val viewModel: WidgetLogSheetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val caseId = intent.getLongExtra(EXTRA_CASE_ID, -1L)
        if (caseId == -1L) {
            finish()
            return
        }
        viewModel.load(caseId)

        setContent {
            val theme by settingsRepository.observeTheme().collectAsStateWithLifecycle(initialValue = AppTheme.PLAIN)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalVoice provides voiceFor(theme)) {
                HodithTheme(theme = theme) {
                    uiState?.let { state ->
                        LogDetailSheet(
                            isEditing = false,
                            durationMode = state.durationMode,
                            intensityEnabled = state.intensityEnabled,
                            initialDraft = state.draft,
                            tagSuggestions = state.tagSuggestions,
                            now = viewModel.nowMillis(),
                            onSave = { draft -> viewModel.save(draft) { finish() } },
                            onDismiss = { finish() },
                        )
                    }
                }
            }
        }
    }
}
