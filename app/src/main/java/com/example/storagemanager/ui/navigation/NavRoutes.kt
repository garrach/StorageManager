package com.example.storagemanager.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Files : Screen("files/{category}") {
        fun createRoute(category: String): String = "files/$category"
    }
    data object Scanner : Screen("scanner")
    data object Cleaner : Screen("cleaner")
    data object Apps : Screen("apps")
    data object Permission : Screen("permission")

    companion object {
        const val GRAPH = "main_graph"
    }
}

val NAV_ITEMS = listOf(
    Screen.Dashboard,
    Screen.Scanner,
    Screen.Cleaner,
    Screen.Apps,
)