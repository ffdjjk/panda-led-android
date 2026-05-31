package com.biexi.pandaled.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biexi.pandaled.ui.home.HomeScreen
import com.biexi.pandaled.ui.detail.DetailScreen
import com.biexi.pandaled.ui.subscribe.SubscribeScreen

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{projectId}"
    const val SUBSCRIBE = "subscribe/{projectId}"

    fun detailRoute(projectId: String) = "detail/$projectId"
    fun subscribeRoute(projectId: String) = "subscribe/$projectId"
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

            // Observe result from SubscribeScreen
            val subscribeActionResult by backStackEntry.savedStateHandle
                .getStateFlow("subscribe_action", null as String?)
                .collectAsState()

            DetailScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFullScreen = { /* handled via Activity */ },
                onNavigateToSubscribe = {
                    navController.navigate(Routes.subscribeRoute(projectId))
                },
                subscribeActionResult = subscribeActionResult,
                onClearSubscribeResult = {
                    backStackEntry.savedStateHandle.remove<String>("subscribe_action")
                }
            )
        }

        composable(
            route = Routes.SUBSCRIBE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            SubscribeScreen(
                onBack = { navController.popBackStack() },
                onStartWithAd = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("subscribe_action", "start_with_ad")
                    navController.popBackStack()
                },
                onSubscribed = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("subscribe_action", "subscribed")
                    navController.popBackStack()
                }
            )
        }
    }
}
