package com.myprojects.scanwisp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.detail.DocumentDetailScreen
import com.myprojects.scanwisp.ui.screens.folders.FoldersScreen
import com.myprojects.scanwisp.ui.screens.home.HomeScreen
import com.myprojects.scanwisp.ui.screens.onboarding.OnboardingScreen
import com.myprojects.scanwisp.ui.screens.preview.PreviewScreen
import com.myprojects.scanwisp.ui.screens.router.RouterScreen
import com.myprojects.scanwisp.ui.screens.settings.SettingsScreen
import com.myprojects.scanwisp.ui.theme.ScanWispTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePreference by viewModel.themePreference.collectAsState(initial = ThemePreference.SYSTEM)
            val onboardingCheckState by viewModel.onboardingCheckState.collectAsState()

            val useDarkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            ScanWispTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                val windowSizeClass = calculateWindowSizeClass(this)

                AppNavHost(
                    navController = navController,
                    windowSizeClass = windowSizeClass.widthSizeClass,
                    onboardingCheckState = onboardingCheckState
                )
            }
        }
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowWidthSizeClass,
    onboardingCheckState: OnboardingCheckState
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Router.route,
        modifier = modifier,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        composable(
            route = Screen.Router.route,
            // START: AI_MODIFIED_BLOCK - Отключаем анимацию для самого первого экрана
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
            // END: AI_MODIFIED_BLOCK
        ) {
            RouterScreen(
                navController = navController,
                onboardingCheckState = onboardingCheckState
            )
        }
        composable(route = Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }
        composable(
            route = Screen.Home.route,
            arguments = listOf(navArgument("folderId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            HomeScreen(
                navController = navController,
                widthSizeClass = windowSizeClass
            )
        }
        composable(route = Screen.Folders.route) {
            FoldersScreen(navController = navController)
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(
            route = Screen.DocumentDetail.route,
            enterTransition = { slideInVertically(initialOffsetY = { it }) },
            popExitTransition = { slideOutVertically(targetOffsetY = { it }) }
        ) {
            DocumentDetailScreen(
                navController = navController,
                widthSizeClass = windowSizeClass
            )
        }
        composable(
            route = Screen.PreviewPage.route,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) {
            PreviewScreen(navController = navController)
        }
    }
}