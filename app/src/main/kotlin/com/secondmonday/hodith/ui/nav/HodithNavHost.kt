package com.secondmonday.hodith.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.ui.about.AboutRoute
import com.secondmonday.hodith.ui.archivedcases.ArchivedCasesRoute
import com.secondmonday.hodith.ui.bigpicture.BigPictureRoute
import com.secondmonday.hodith.ui.case.CaseEditRoute
import com.secondmonday.hodith.ui.casedetail.CaseDetailRoute
import com.secondmonday.hodith.ui.home.HomeRoute
import com.secondmonday.hodith.ui.logsheet.LogDetailRoute
import com.secondmonday.hodith.ui.settings.SettingsRoute
import com.secondmonday.hodith.ui.share.SharePreviewRoute
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.IconHalo
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.triggers.TriggersRoute
import com.secondmonday.hodith.ui.voice.LocalVoice

private const val CASE_EDIT_ROUTE = "case_edit"
private const val CASE_DETAIL_ROUTE = "case_detail"
private const val LOG_EDIT_ROUTE = "log_edit"
private const val ARCHIVED_CASES_ROUTE = "archived_cases"
private const val TRIGGERS_ROUTE = "triggers"
private const val SHARE_ROUTE = "share"
private const val ABOUT_ROUTE = "about"
private const val CASE_ID_ARG = "caseId"
private const val EVENT_ID_ARG = "eventId"
private const val NO_CASE_ID = -1L

@Composable
fun HodithNavHost(
    modifier: Modifier = Modifier,
    deepLinkCaseId: Long? = null,
) {
    val navController = rememberNavController()
    val voice = LocalVoice.current

    // Notification taps (trigger fired / check-in due) and widget Case taps (List/Single-case)
    // carry a caseId to land directly on that Case's detail screen. Start destination stays
    // Home; this navigates on top of it once per fresh launch — all of these MainActivity
    // intents use FLAG_ACTIVITY_CLEAR_TASK, guaranteeing a fresh composition each tap, so `Unit`
    // as the key (fire-once) is correct here.
    LaunchedEffect(Unit) {
        deepLinkCaseId?.let { caseId -> navController.navigate("$CASE_DETAIL_ROUTE/$caseId") }
    }
    val currentRoute =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val onDetailScreen =
                currentRoute?.startsWith(CASE_EDIT_ROUTE) == true ||
                    currentRoute?.startsWith(CASE_DETAIL_ROUTE) == true ||
                    currentRoute?.startsWith(LOG_EDIT_ROUTE) == true ||
                    currentRoute?.startsWith(TRIGGERS_ROUTE) == true ||
                    currentRoute?.startsWith(SHARE_ROUTE) == true ||
                    currentRoute == ARCHIVED_CASES_ROUTE ||
                    currentRoute == ABOUT_ROUTE
            if (!onDetailScreen) {
                val decorationStyle = LocalCardDecorationStyle.current
                // Plain only: explicit surface rather than NavigationBar's default containerColor
                // (NavigationBarDefaults.containerColor -> surfaceContainer, a role Plain didn't
                // author until this branch). Intense and Bright keep the M3 default — each
                // theme's colors stay independent of Plain's changes.
                val navBarContainerColor =
                    if (decorationStyle == CardDecorationStyle.PLAIN) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        NavigationBarDefaults.containerColor
                    }
                NavigationBar(containerColor = navBarContainerColor) {
                    HodithDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            // contentDescription is null: the visible label below already provides
                            // the accessible name, and a second description here would make
                            // TalkBack announce the same text twice.
                            icon = {
                                if (decorationStyle == CardDecorationStyle.BRIGHT) {
                                    BrightNavIcon(icon = destination.icon, selected = selected)
                                } else {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            },
                            label = { Text(destination.label(voice)) },
                            colors =
                                if (decorationStyle == CardDecorationStyle.BRIGHT) {
                                    // Material3's default pill indicator is replaced by BrightNavIcon's
                                    // IconHalo glow, so the indicator itself must be transparent.
                                    NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                                } else {
                                    NavigationBarItemDefaults.colors()
                                },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = HodithDestination.HOME.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(HodithDestination.HOME.route) {
                HomeRoute(
                    onNewCase = { navController.navigate(CASE_EDIT_ROUTE) },
                    onOpenCase = { caseId -> navController.navigate("$CASE_DETAIL_ROUTE/$caseId") },
                    onOpenArchivedCases = { navController.navigate(ARCHIVED_CASES_ROUTE) },
                )
            }
            composable(HodithDestination.BIG_PICTURE.route) {
                BigPictureRoute(onOpenCase = { caseId -> navController.navigate("$CASE_DETAIL_ROUTE/$caseId") })
            }
            composable(HodithDestination.SETTINGS.route) {
                SettingsRoute(onOpenAbout = { navController.navigate(ABOUT_ROUTE) })
            }
            composable(
                route = "$CASE_EDIT_ROUTE?$CASE_ID_ARG={$CASE_ID_ARG}",
                arguments =
                    listOf(
                        navArgument(CASE_ID_ARG) {
                            type = NavType.LongType
                            defaultValue = NO_CASE_ID
                        },
                    ),
            ) {
                CaseEditRoute(
                    onDone = { navController.popBackStack() },
                    onArchived = { navController.popBackStack(HodithDestination.HOME.route, false) },
                )
            }
            composable(
                route = "$CASE_DETAIL_ROUTE/{$CASE_ID_ARG}",
                arguments = listOf(navArgument(CASE_ID_ARG) { type = NavType.LongType }),
            ) {
                CaseDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEditCase = { caseId -> navController.navigate("$CASE_EDIT_ROUTE?$CASE_ID_ARG=$caseId") },
                    onEditEvent = { caseId, eventId -> navController.navigate("$LOG_EDIT_ROUTE/$caseId/$eventId") },
                    onOpenTriggers = { caseId -> navController.navigate("$TRIGGERS_ROUTE/$caseId") },
                    onOpenShare = { caseId -> navController.navigate("$SHARE_ROUTE/$caseId") },
                )
            }
            composable(
                route = "$LOG_EDIT_ROUTE/{$CASE_ID_ARG}/{$EVENT_ID_ARG}",
                arguments =
                    listOf(
                        navArgument(CASE_ID_ARG) { type = NavType.LongType },
                        navArgument(EVENT_ID_ARG) { type = NavType.LongType },
                    ),
            ) {
                LogDetailRoute(onDone = { navController.popBackStack() })
            }
            composable(ARCHIVED_CASES_ROUTE) {
                ArchivedCasesRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = "$TRIGGERS_ROUTE/{$CASE_ID_ARG}",
                arguments = listOf(navArgument(CASE_ID_ARG) { type = NavType.LongType }),
            ) {
                TriggersRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = "$SHARE_ROUTE/{$CASE_ID_ARG}",
                arguments = listOf(navArgument(CASE_ID_ARG) { type = NavType.LongType }),
            ) {
                SharePreviewRoute(onBack = { navController.popBackStack() })
            }
            composable(ABOUT_ROUTE) {
                AboutRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Soft Glow mockup's `.navitem.on .ico` — the active tab's icon sits inside an [IconHalo] glow
 * instead of Material3's default pill indicator (suppressed via `indicatorColor = Color
 * .Transparent` at the call site). Shared/app-wide like the bottom nav itself, so it branches on
 * [LocalCardDecorationStyle] here rather than per-screen.
 */
@Composable
private fun BrightNavIcon(
    icon: ImageVector,
    selected: Boolean,
) {
    if (selected) {
        // Smaller than IconHalo's own 34dp default: the mockup's nav icon sits in a compact 26px
        // circle, well below the card/stat-tile icons IconHalo's default size was tuned for.
        IconHalo(size = 28.dp) { Icon(icon, contentDescription = null) }
    } else {
        Icon(icon, contentDescription = null)
    }
}

@Composable
private fun NavBrightIconPreviewContent() {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { BrightNavIcon(icon = Icons.Filled.Home, selected = true) },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
        )
        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = { BrightNavIcon(icon = Icons.Filled.DateRange, selected = false) },
            label = { Text("Big Picture") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
        )
    }
}

@Preview(name = "Bottom nav — Bright light", showBackground = true, widthDp = 380)
@Composable
private fun NavBrightIconLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        NavBrightIconPreviewContent()
    }
}

@Preview(name = "Bottom nav — Bright dark", showBackground = true, widthDp = 380)
@Composable
private fun NavBrightIconDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        NavBrightIconPreviewContent()
    }
}
