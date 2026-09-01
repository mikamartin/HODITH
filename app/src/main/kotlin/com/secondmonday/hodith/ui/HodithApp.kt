package com.secondmonday.hodith.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.TimeFormat
import com.secondmonday.hodith.ui.common.NotificationPermissionRequestEffect
import com.secondmonday.hodith.ui.nav.HodithNavHost
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalBigPictureCellStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalShareCardSkin
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.theme.bigPictureCellStyle
import com.secondmonday.hodith.ui.theme.cardDecorationStyle
import com.secondmonday.hodith.ui.theme.shareCardSkin
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.voiceFor
import kotlinx.coroutines.flow.Flow

/**
 * [notificationPermissionRequests] is collected here, at the app root, rather than inside whichever
 * screen requests it (Case Edit, Triggers) — Case Edit navigates away the instant a save completes,
 * which could dispose that screen's own effect before it gets a chance to launch the system dialog.
 */
@Composable
fun HodithApp(
    themeFlow: Flow<AppTheme>,
    timeFormatFlow: Flow<TimeFormat>,
    notificationPermissionRequests: Flow<Unit>,
    deepLinkCaseId: Long? = null,
) {
    val theme by themeFlow.collectAsStateWithLifecycle(initialValue = AppTheme.PLAIN)
    val timeFormat by timeFormatFlow.collectAsStateWithLifecycle(initialValue = TimeFormat.TWELVE_HOUR)

    NotificationPermissionRequestEffect(events = notificationPermissionRequests)

    CompositionLocalProvider(
        LocalVoice provides voiceFor(theme),
        LocalBigPictureCellStyle provides bigPictureCellStyle(theme),
        LocalCardDecorationStyle provides cardDecorationStyle(theme),
        LocalShareCardSkin provides shareCardSkin(theme),
        LocalTimeFormat provides timeFormat,
    ) {
        HodithTheme(theme = theme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                HodithNavHost(modifier = Modifier.fillMaxSize().safeDrawingPadding(), deepLinkCaseId = deepLinkCaseId)
            }
        }
    }
}
