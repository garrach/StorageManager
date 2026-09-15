package com.example.storagemanager.ui.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.storagemanager.ui.dashboard.DashboardScreen
import com.example.storagemanager.ui.files.FilesScreen
import com.example.storagemanager.ui.scanner.ScannerScreen
import com.example.storagemanager.ui.cleaner.CleanerScreen
import com.example.storagemanager.ui.appusage.AppUsageScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
        enterTransition = { fadeIn() + slideIntoHorizontally { it / 4 } },
        exitTransition = { fadeOut() + slideOutOfHorizontally { -it / 4 } },
        popEnterTransition = { fadeIn() + slideIntoHorizontally { -it / 4 } },
        popExitTransition = { fadeOut() + slideOutOfHorizontally { it / 4 } },
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToCategory = { category ->
                    navController.navigate(Screen.Files.createRoute(category.route))
                },
                onNavigateToApps = {
                    navController.navigate(Screen.Apps.route)
                },
            )
        }

        composable(
            route = Screen.Files.route,
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { backStackEntry ->
            val categoryRoute = backStackEntry.arguments?.getString("category") ?: ""
            FilesScreen(
                categoryRoute = categoryRoute,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Scanner.route) {
            ScannerScreen(
                onBack = if (navController.currentDestination?.route != Screen.Scanner.route)
                    { { navController.popBackStack() } } else null,
            )
        }

        composable(Screen.Cleaner.route) {
            CleanerScreen(
                onBack = if (navController.currentDestination?.route != Screen.Cleaner.route)
                    { { navController.popBackStack() } } else null,
            )
        }

        composable(Screen.Apps.route) {
            AppUsageScreen(
                onBack = if (navController.currentDestination?.route != Screen.Apps.route)
                    { { navController.popBackStack() } } else null,
            )
        }
    }
}