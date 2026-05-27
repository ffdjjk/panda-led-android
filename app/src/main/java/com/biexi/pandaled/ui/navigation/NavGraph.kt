package com.biexi.pandaled.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biexi.pandaled.ui.home.HomeScreen
import com.biexi.pandaled.ui.detail.DetailScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{projectId}"

    fun detailRoute(projectId: String) = "detail/$projectId"
}

@Composable
fun PandaLedNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onProjectClick = { projectId ->
                    navController.navigate(Routes.detailRoute(projectId))
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            DetailScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFullScreen = { /* handled via Activity */ }
            )
        }
    }
}
