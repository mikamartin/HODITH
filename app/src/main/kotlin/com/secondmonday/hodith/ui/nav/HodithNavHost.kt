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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondmonday.hodith.ui.bigpicture.BigPictureScreen
import com.secondmonday.hodith.ui.home.HomeRoute
import com.secondmonday.hodith.ui.settings.SettingsScreen
import com.secondmonday.hodith.ui.voice.LocalVoice

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
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = HodithDestination.HOME.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(HodithDestination.HOME.route) { HomeRoute() }
            composable(HodithDestination.BIG_PICTURE.route) { BigPictureScreen() }
            composable(HodithDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
