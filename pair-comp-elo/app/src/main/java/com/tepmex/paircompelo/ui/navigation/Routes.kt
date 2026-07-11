package com.tepmex.paircompelo.ui.navigation

sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object ArchivedLists : Routes("archived_lists")
    data object ListEdit : Routes("list_edit?listId={listId}") {
        fun create(listId: String? = null) =
            if (listId == null) "list_edit" else "list_edit?listId=$listId"
    }
    data object ListDetail : Routes("list/{listId}") {
        fun create(listId: String) = "list/$listId"
    }
    data object ItemEdit : Routes("list/{listId}/item_edit?itemId={itemId}") {
        fun create(listId: String, itemId: String? = null) =
            if (itemId == null) "list/$listId/item_edit"
            else "list/$listId/item_edit?itemId=$itemId"
    }
    data object ItemCompare : Routes("list/{listId}/compare") {
        fun create(listId: String) = "list/$listId/compare"
    }
    data object ItemRanking : Routes("list/{listId}/ranking") {
        fun create(listId: String) = "list/$listId/ranking"
    }
    data object ItemHistory : Routes("list/{listId}/history") {
        fun create(listId: String) = "list/$listId/history"
    }
    data object ListCompare : Routes("list_compare")
    data object ListRanking : Routes("list_ranking")
    data object GlobalHistory : Routes("history")
    data object Settings : Routes("settings")
}
