package com.myprojects.scanwisp.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.navigation.testing.TestNavHostController
import com.myprojects.scanwisp.R
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.ui.navigation.Screen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class HomeScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Hilt внедрит нам DAO из нашего TestDatabaseModule
    @Inject
    lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun launchHomeScreen() {
        composeTestRule.setContent {
            // Мы не можем внедрять ViewModel напрямую в UI-тесте,
            // но Hilt сам предоставит ее для HomeScreen, когда тот будет создан.
            HomeScreen(
                navController = rememberNavController(),
                widthSizeClass = WindowWidthSizeClass.Compact
            )
        }
    }

    @Test
    fun emptyState_isShown_whenDatabaseIsEmpty() {
        // Arrange
        launchHomeScreen()
        val emptyStateText =
            composeTestRule.activity.getString(R.string.empty_state_title_no_documents)

        // Assert
        composeTestRule.onNodeWithText(emptyStateText).assertIsDisplayed()
    }

    @Test
    fun documentsInDatabase_areShownOnScreen() = runTest {
        // Arrange
        // Напрямую добавляем данные в БД через DAO
        dao.insertDocument(
            DocumentEntity(
                id = "doc1",
                title = "Test Document 1",
                creationTimestamp = 1L,
                coverImagePath = "",
                folderId = null
            )
        )
        dao.insertDocument(
            DocumentEntity(
                id = "doc2",
                title = "Test Document 2",
                creationTimestamp = 2L,
                coverImagePath = "",
                folderId = null
            )
        )

        // Act
        launchHomeScreen()

        // Assert
        composeTestRule.onNodeWithText("Test Document 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Document 2").assertIsDisplayed()
    }

    // Это уже практически End-to-End тест, хоть и очень простой
    @Test
    fun clickingDocument_navigatesToDetailScreen() = runTest {
        // Arrange
        dao.insertDocument(
            DocumentEntity(
                id = "doc1",
                title = "Clickable Doc",
                creationTimestamp = 1L,
                coverImagePath = "",
                folderId = null
            )
        )

        val navController = TestNavHostController(composeTestRule.activity)

        composeTestRule.setContent {
            // Используем тестовый NavController, чтобы отслеживать навигацию
            HomeScreen(navController = navController, widthSizeClass = WindowWidthSizeClass.Compact)
        }

        // Act
        composeTestRule.onNodeWithText("Clickable Doc").performClick()

        // Assert
        // Проверяем, что текущий маршрут в NavController изменился на правильный
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        assertTrue(
            currentRoute?.startsWith(Screen.DocumentDetail.route.substringBefore("/{")) ?: false
        )
    }
}