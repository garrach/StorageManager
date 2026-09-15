package com.example.storagemanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.storagemanager.ui.navigation.NAV_ITEMS
import com.example.storagemanager.ui.navigation.Screen

@Composable
fun AdaptiveScaffold(
    navController: NavController,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isTopLevel = currentRoute in NAV_ITEMS.map { it.route }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                NavigationBar {
                    NAV_ITEMS.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screenIcon(screen), contentDescription = screen.route) },
                            label = { Text(screenLabel(screen), style = MaterialTheme.typography.labelSmall) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

private fun screenIcon(screen: Screen): ImageVector = when (screen) {
    Screen.Dashboard -> Icons.Filled.Home
    Screen.Scanner -> Icons.Filled.Search
    Screen.Cleaner -> Icons.Filled.CleaningServices
    Screen.Apps -> Icons.Filled.Apps
    else -> Icons.Filled.HelpOutline
}

private fun screenLabel(screen: Screen): String = when (screen) {
    Screen.Dashboard -> "Overview"
    Screen.Scanner -> "Scan"
    Screen.Cleaner -> "Clean"
    Screen.Apps -> "Apps"
    else -> screen.route
}