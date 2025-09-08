package com.myprojects.scanwisp

// ==========================================================
// НОВЫЕ ИМПОРТЫ ДЛЯ UMP SDK
// ==========================================================
// ==========================================================
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
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
import androidx.metrics.performance.JankStats
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var jankStats: JankStats? = null

    // ==========================================================
    // НОВЫЕ СВОЙСТВА ДЛЯ UMP SDK
    // ==========================================================
    private lateinit var consentInformation: ConsentInformation
    private val isMobileAdsInitialized = AtomicBoolean(false)
    // ==========================================================


    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            setupStrictMode()
        }

        // ==========================================================
        // НОВЫЙ ВЫЗОВ: Запускаем процесс получения согласия.
        // ==========================================================
        gatherConsent()
        // ==========================================================

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

    override fun onResume() {
        super.onResume()
        // Включаем отслеживание JankStats, когда Activity активна
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        // Выключаем, когда неактивна, чтобы не тратить ресурсы
        jankStats?.isTrackingEnabled = false
    }

    // ==========================================================
    // НОВЫЕ МЕТОДЫ ДЛЯ UMP И ИНИЦИАЛИЗАЦИИ РЕКЛАМЫ
    // ==========================================================

    /**
     * Запускает процесс сбора согласия пользователя.
     */
    private fun gatherConsent() {
        // Устанавливаем параметры запроса. Для отладки можно указать географию.
        val params = ConsentRequestParameters.Builder().build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        // Запрашиваем актуальную информацию о согласии с серверов Google.
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                // Этот слушатель вызывается при успешном обновлении информации.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
                    if (loadAndShowError != null) {
                        Log.w(
                            TAG,
                            "Ошибка загрузки или показа формы согласия: ${loadAndShowError.message}"
                        )
                    }
                    // После того как форма была показана (или не требовалась),
                    // мы можем инициализировать SDK рекламы.
                    initializeMobileAdsSdk()
                }
            },
            { requestConsentError ->
                // Этот слушатель вызывается при ошибке обновления информации.
                Log.w(
                    TAG,
                    "Ошибка запроса информации о согласии: ${requestConsentError.message}"
                )
            }
        )
    }

    /**
     * Инициализирует Mobile Ads SDK, если согласие было получено
     * и SDK еще не был инициализирован.
     */
    private fun initializeMobileAdsSdk() {
        // Проверяем, можно ли запрашивать рекламу и не была ли уже проведена инициализация.
        if (consentInformation.canRequestAds() && isMobileAdsInitialized.compareAndSet(
                false,
                true
            )
        ) {
            // Инициализируем SDK
            MobileAds.initialize(this) {
                Log.d(TAG, "Mobile Ads SDK initialized.")
            }

            // Запускаем отладку производительности только после основной инициализации
            if (BuildConfig.DEBUG) {
                setupJankStats()
            }
        }
    }

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
        // Запускаем в фоновом потоке, чтобы не блокировать onCreate
        val jankStatsScope = CoroutineScope(Dispatchers.Default)
        jankStats = JankStats.createAndTrack(window) { frameData ->
            // Этот блок будет вызываться для каждого кадра, который был признан "тормозящим" (jank)
            if (frameData.isJank) {
                Log.w("JankStats", "Janky frame detected: $frameData")
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