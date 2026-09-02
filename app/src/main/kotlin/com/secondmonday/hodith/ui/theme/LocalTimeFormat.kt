package com.secondmonday.hodith.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.TimeFormat

/**
 * The user's 12h/24h clock preference (spec §14), threaded to every composable that renders a
 * wall-clock time — the Case-detail event list, the Big Picture detail dialogs, the log sheet's
 * time button and its time-picker wheel. Provided once at the app root in
 * [com.secondmonday.hodith.ui.HodithApp] from `SettingsRepository.observeTimeFormat()`, the same
 * way `LocalVoice` is, and re-provided by the widget log trampoline since it hosts the log sheet
 * outside the main Activity.
 *
 * The default matches a no-op read: [TimeFormat.TWELVE_HOUR], which is also what the repository
 * falls back to on a device set to 12-hour.
 */
val LocalTimeFormat = staticCompositionLocalOf { TimeFormat.TWELVE_HOUR }
