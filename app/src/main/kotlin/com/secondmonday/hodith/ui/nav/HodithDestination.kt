package com.secondmonday.hodith.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.secondmonday.hodith.ui.voice.Voice

enum class HodithDestination(
    val route: String,
    val icon: ImageVector,
    val label: (Voice) -> String,
) {
    HOME("home", Icons.Filled.Home, Voice::homeNavLabel),
    BIG_PICTURE("big_picture", Icons.Filled.DateRange, Voice::bigPictureNavLabel),
    SETTINGS("settings", Icons.Filled.Settings, Voice::settingsNavLabel),
}
