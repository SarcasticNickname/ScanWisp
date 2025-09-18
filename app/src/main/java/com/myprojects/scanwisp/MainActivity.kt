package com.myprojects.scanwisp

import android.os.Bundle
import android.os.StrictMode
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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.myprojects.scanwisp.consent.ConsentManager
import com.myprojects.scanwisp.domain.model.ThemePreference
import com.myprojects.scanwisp.ui.navigation.Screen
import com.myprojects.scanwisp.ui.screens.detail.DocumentDetailScreen
import com.myprojects.scanwisp.ui.screens.folders.FoldersScreen
import com.myprojects.scanwisp.ui.screens.home.HomeScreen
import com.myprojects.scanwisp.ui.screens.onboarding.OnboardingScreen
import com.myprojects.scanwisp.ui.screens.preview.PreviewScreen
import com.myprojects.scanwisp.ui.screens.router.RouterScreen
import com.myprojects.scanwisp.ui.screens.settings.SettingsScreen
import com.myprojects.scanwisp.ui.screens.trash.TrashScreen
import com.myprojects.scanwisp.ui.theme.ScanWispTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var jankStats: JankStats? = null

    @Inject
    lateinit var consentManager: ConsentManager

    private lateinit var consentInformation: ConsentInformation
    // ИЗМЕНЕНИЕ: Поле isMobileAdsInitialized удалено.

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }

        enableEdgeToEdge()
        setContent {
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle(
                initialValue = ThemePreference.SYSTEM
            )
            val onboardingCheckState by viewModel.onboardingCheckState.collectAsStateWithLifecycle()

            ScanWispTheme(themePreference = themePreference) {
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

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true

        lifecycleScope.launch {
            gatherConsent()
        }
    }

    override fun onPause() {
        super.onPause()
        jankStats?.isTrackingEnabled = false
    }

    private fun gatherConsent() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
                    if (loadAndShowError != null) {
                        Timber.w(
                            "Ошибка загрузки или показа формы согласия: ${loadAndShowError.message}"
                        )
                    }

                    val canRequestAds = consentInformation.canRequestAds()
                    consentManager.updateConsentStatus(canRequestAds)
                    Timber.d("UMP flow finished. Can request ads: $canRequestAds")

                    // ИЗМЕНЕНИЕ: Вызов initializeMobileAdsSdk() отсюда убран.
                }
            },
            { requestConsentError ->
                Timber.w(
                    "Ошибка запроса информации о согласии: ${requestConsentError.message}"
                )
                // В случае ошибки запроса, считаем, что рекламу показывать нельзя.
                consentManager.updateConsentStatus(false)
            }
        )
    }

    // ИЗМЕНЕНИЕ: Метод initializeMobileAdsSdk() полностью удален.

    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    private fun setupJankStats() {
        // Инициализация JankStats теперь происходит внутри удаленного метода initializeMobileAdsSdk(),
        // но так как мы его убрали, этот вызов тоже можно будет перенести или удалить,
        // если статистика нужна только при работе с рекламой.
        // Пока оставим его здесь, но он не будет вызываться.
        val jankStatsScope = CoroutineScope(Dispatchers.Default)
        jankStats = JankStats.createAndTrack(window) { frameData ->
            if (frameData.isJank) {
                Timber.w("Janky frame detected: $frameData")
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
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
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

        composable(route = Screen.Trash.route) {
            TrashScreen(
                navController = navController,
                widthSizeClass = windowSizeClass
            )
        }
    }
}