package com.tepmex.paircompelo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tepmex.paircompelo.feature.history.HistoryScreen
import com.tepmex.paircompelo.feature.home.HomeScreen
import com.tepmex.paircompelo.feature.itemcomparison.ItemComparisonScreen
import com.tepmex.paircompelo.feature.itemranking.ItemRankingScreen
import com.tepmex.paircompelo.feature.items.ItemEditScreen
import com.tepmex.paircompelo.feature.listcomparison.ListComparisonScreen
import com.tepmex.paircompelo.feature.listranking.ListRankingScreen
import com.tepmex.paircompelo.feature.lists.ArchivedListsScreen
import com.tepmex.paircompelo.feature.lists.ListDetailScreen
import com.tepmex.paircompelo.feature.lists.ListEditScreen
import com.tepmex.paircompelo.feature.settings.SettingsScreen

@Composable
fun PairCompEloNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.Home.route) {
        composable(Routes.Home.route) {
            HomeScreen(
                onOpenList = { navController.navigate(Routes.ListDetail.create(it)) },
                onCreateList = { navController.navigate(Routes.ListEdit.create()) },
                onCompareItems = { navController.navigate(Routes.ItemCompare.create(it)) },
                onItemRanking = { navController.navigate(Routes.ItemRanking.create(it)) },
                onListRanking = { navController.navigate(Routes.ListRanking.route) },
                onCompareLists = { navController.navigate(Routes.ListCompare.route) },
                onSettings = { navController.navigate(Routes.Settings.route) },
                onArchived = { navController.navigate(Routes.ArchivedLists.route) },
                onHistory = { navController.navigate(Routes.GlobalHistory.route) },
            )
        }
        composable(Routes.ArchivedLists.route) {
            ArchivedListsScreen(
                onBack = { navController.popBackStack() },
                onOpenList = { navController.navigate(Routes.ListDetail.create(it)) },
            )
        }
        composable(
            route = "list_edit?listId={listId}",
            arguments = listOf(navArgument("listId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) {
            ListEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    if (id != null) navController.navigate(Routes.ListDetail.create(id))
                },
            )
        }
        composable(
            route = "list/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) {
            ListDetailScreen(
                onBack = { navController.popBackStack() },
                onEditList = { id -> navController.navigate(Routes.ListEdit.create(id)) },
                onAddItem = { id -> navController.navigate(Routes.ItemEdit.create(id)) },
                onEditItem = { listId, itemId ->
                    navController.navigate(Routes.ItemEdit.create(listId, itemId))
                },
                onCompare = { id -> navController.navigate(Routes.ItemCompare.create(id)) },
                onRanking = { id -> navController.navigate(Routes.ItemRanking.create(id)) },
                onHistory = { id -> navController.navigate(Routes.ItemHistory.create(id)) },
            )
        }
        composable(
            route = "list/{listId}/item_edit?itemId={itemId}",
            arguments = listOf(
                navArgument("listId") { type = NavType.StringType },
                navArgument("itemId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ItemEditScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "list/{listId}/compare",
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) {
            ItemComparisonScreen(onExit = { navController.popBackStack() })
        }
        composable(
            route = "list/{listId}/ranking",
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) {
            ItemRankingScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "list/{listId}/history",
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) {
            HistoryScreen(onBack = { navController.popBackStack() }, listScoped = true)
        }
        composable(Routes.ListCompare.route) {
            ListComparisonScreen(onExit = { navController.popBackStack() })
        }
        composable(Routes.ListRanking.route) {
            ListRankingScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GlobalHistory.route) {
            HistoryScreen(onBack = { navController.popBackStack() }, listScoped = false)
        }
        composable(Routes.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
