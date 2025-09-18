package com.myprojects.scanwisp.ui.navigation

sealed class Screen(val route: String) {
    object Router : Screen("router_screen")
    object Onboarding : Screen("onboarding_screen")

    // HomeScreen теперь может принимать опциональный folderId
    object Home : Screen("home_screen?folderId={folderId}") {
        // Маршрут по умолчанию для корневых документов
        fun createRoute() = "home_screen"

        // Маршрут для просмотра документов в конкретной папке
        fun createRouteWithFolder(folderId: String) = "home_screen?folderId=$folderId"
    }

    object DocumentDetail : Screen("document_detail_screen/{documentId}") {
        fun createRoute(documentId: String) = "document_detail_screen/$documentId"
    }

    object PreviewPage : Screen("preview_page_screen/{pageId}") {
        fun createRoute(pageId: String) = "preview_page_screen/$pageId"
    }

    object Folders : Screen("folders_screen")

    object Settings : Screen("settings_screen")

    object Trash : Screen("trash_screen")
}