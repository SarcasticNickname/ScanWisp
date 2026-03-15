package com.myprojects.scanwisp.ui.screens.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.ui.navigation.Screen

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Нижняя панель навигации для главного экрана.
 * Использует NavigationBar и подсвечивает активный элемент.
 */
@Composable
fun ScanWispBottomAppBar(
    navController: NavController
) {
    val navItems = listOf(
        BottomNavItem(
            stringResource(R.string.bottom_nav_home),
            Screen.Home.createRoute(),
            Icons.Filled.Home,
            Icons.Outlined.Home
        ),
        BottomNavItem(
            stringResource(R.string.bottom_nav_search),
            Screen.Search.route,
            Icons.Filled.Search,
            Icons.Outlined.Search
        ),
        BottomNavItem(
            stringResource(R.string.bottom_nav_folders),
            Screen.Folders.route,
            Icons.Filled.Folder,
            Icons.Outlined.Folder
        ),
        BottomNavItem(
            stringResource(R.string.bottom_nav_trash),
            Screen.Trash.route,
            Icons.Filled.RestoreFromTrash,
            Icons.Outlined.RestoreFromTrash
        ),
        BottomNavItem(
            stringResource(R.string.bottom_nav_settings),
            Screen.Settings.route,
            Icons.Filled.Settings,
            Icons.Outlined.Settings
        )
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val navBackStackEntry = navController.currentBackStackEntry
        val currentDestination = navBackStackEntry?.destination

        navItems.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any {
                it.route?.startsWith(item.route.substringBefore("?")) == true
            } == true

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}