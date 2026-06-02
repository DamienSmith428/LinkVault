package com.linkvault.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.linkvault.ui.screens.folder.FolderScreen
import com.linkvault.ui.screens.home.HomeScreen
import com.linkvault.ui.screens.search.SearchScreen
import com.linkvault.ui.screens.settings.SettingsScreen
import com.linkvault.ui.screens.queue.QueueScreen
import com.linkvault.ui.screens.about.AboutScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Folder : Screen("folder/{folderId}") {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Queue : Screen("queue")
    object About : Screen("about")
}

@Composable
fun LinkVaultNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onFolderClick = { folderId ->
                    navController.navigate(Screen.Folder.createRoute(folderId))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onQueueClick = {
                    navController.navigate(Screen.Queue.route)
                }
            )
        }
        composable(
            route = Screen.Folder.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: return@composable
            FolderScreen(
                folderId = folderId,
                onNavigateUp = { navController.popBackStack() },
                onQueueClick = { navController.navigate(Screen.Queue.route) }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onFolderClick = { folderId ->
                    navController.navigate(Screen.Folder.createRoute(folderId))
                },
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateUp = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable(Screen.Queue.route) {
            QueueScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
