package com.myprojects.scanwisp

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.myprojects.scanwisp.di.AppModule
import com.myprojects.scanwisp.di.RepositoryModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Эта аннотация говорит Hilt, какие производственные модули нужно "выгрузить"
// перед тем, как использовать те, что мы предоставим.
@UninstallModules(AppModule::class, RepositoryModule::class)
@HiltAndroidTest
class ErrorStateDisplayTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        // Внедряем зависимости, определенные в TestErrorAppModule
        hiltRule.inject()
    }

    @Test
    fun whenDataLoadingFails_errorStateIsShownOnHomeScreen() {
        // Arrange
        composeTestRule.setContent {
            AppNavHost(
                navController = rememberNavController(),
                windowSizeClass = WindowWidthSizeClass.Compact,
                onboardingCheckState = OnboardingCheckState.Completed
            )
        }

        // Act
        // Никаких действий не требуется. ViewModel при инициализации попытается
        // загрузить данные, наш мок DAO выбросит исключение,
        // ViewModel обработает его и выставит Error состояние.

        // Assert
        composeTestRule.waitForIdle()

        val errorTitle = composeTestRule.activity.getString(R.string.error_database_title)
        val errorSubtitle = composeTestRule.activity.getString(R.string.error_database_subtitle)

        composeTestRule.onNodeWithText(errorTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(errorSubtitle).assertIsDisplayed()
    }
}