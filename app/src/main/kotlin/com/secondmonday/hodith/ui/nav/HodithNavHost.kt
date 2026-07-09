package com.secondmonday.hodith.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secondmonday.hodith.ui.bigpicture.BigPictureScreen
import com.secondmonday.hodith.ui.case.CaseEditRoute
import com.secondmonday.hodith.ui.home.HomeRoute
import com.secondmonday.hodith.ui.settings.SettingsScreen
import com.secondmonday.hodith.ui.voice.LocalVoice

private const val CASE_EDIT_ROUTE = "case_edit"
private const val CASE_ID_ARG = "caseId"
private const val NO_CASE_ID = -1L

@Composable
fun HodithNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val voice = LocalVoice.current
    val currentRoute =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentRoute?.startsWith(CASE_EDIT_ROUTE) != true) {
                NavigationBar {
                    HodithDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
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
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label(voice)) },
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
                    onEditCase = { caseId -> navController.navigate("$CASE_EDIT_ROUTE?$CASE_ID_ARG=$caseId") },
                )
            }
            composable(HodithDestination.BIG_PICTURE.route) { BigPictureScreen() }
            composable(HodithDestination.SETTINGS.route) { SettingsScreen() }
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
                CaseEditRoute(onDone = { navController.popBackStack() })
            }
        }
    }
}
