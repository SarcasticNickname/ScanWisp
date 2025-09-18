package com.myprojects.scanwisp

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class E2eSearchAndSortTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        hiltRule.inject()

        // Запускаем UI перед каждым тестом
        composeTestRule.setContent {
            AppNavHost(
                navController = rememberNavController(),
                windowSizeClass = WindowWidthSizeClass.Compact,
                onboardingCheckState = OnboardingCheckState.Completed
            )
        }
    }

    @Test
    fun searchAndSort_updatesDocumentListCorrectly() = runTest {
        // --- ЧАСТЬ 1: Подготовка ---
        // 1. Arrange: Создаем 3 документа в разном порядке
        dao.insertDocument(
            DocumentEntity(
                id = "doc1",
                title = "Cherry Scan",
                creationTimestamp = 300L,
                coverImagePath = "",
                folderId = null
            )
        )
        dao.insertDocument(
            DocumentEntity(
                id = "doc2",
                title = "Apple Scan",
                creationTimestamp = 200L,
                coverImagePath = "",
                folderId = null
            )
        )
        dao.insertDocument(
            DocumentEntity(
                id = "doc3",
                title = "Banana Scan",
                creationTimestamp = 100L,
                coverImagePath = "",
                folderId = null
            )
        )

        composeTestRule.waitForIdle()

        // 2. Assert: Проверяем начальный порядок (по дате, DESC)
        val allNodes = composeTestRule.onAllNodesWithText(" Scan", substring = true)
        allNodes[0].assertTextContains("Cherry Scan") // 300L
        allNodes[1].assertTextContains("Apple Scan")  // 200L
        allNodes[2].assertTextContains("Banana Scan") // 100L

        // --- ЧАСТЬ 2: Тестирование Поиска ---

        // 3. Act: Открываем поиск
        val searchIconDesc = composeTestRule.activity.getString(R.string.home_top_bar_action_search)
        composeTestRule.onNodeWithContentDescription(searchIconDesc).performClick()

        // 4. Act: Вводим текст
        val searchPlaceholder =
            composeTestRule.activity.getString(R.string.home_top_bar_search_placeholder)
        composeTestRule.onNodeWithText(searchPlaceholder).performTextInput("Apple")
        composeTestRule.waitForIdle()

        // 5. Assert: Проверяем, что в списке остался только один документ
        composeTestRule.onNodeWithText("Apple Scan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cherry Scan").assertDoesNotExist()
        composeTestRule.onNodeWithText("Banana Scan").assertDoesNotExist()

        // 6. Act: Очищаем поиск
        val clearSearchDesc =
            composeTestRule.activity.getString(R.string.home_top_bar_cd_clear_search)
        composeTestRule.onNodeWithContentDescription(clearSearchDesc).performClick()
        composeTestRule.waitForIdle()

        // 7. Assert: Проверяем, что все документы вернулись
        composeTestRule.onAllNodesWithText(" Scan", substring = true).assertCountEquals(3)

        // 8. Act: Закрываем поиск
        val backButtonDesc = composeTestRule.activity.getString(R.string.home_top_bar_cd_back)
        composeTestRule.onNodeWithContentDescription(backButtonDesc).performClick()

        // --- ЧАСТЬ 3: Тестирование Сортировки ---

        // 9. Act: Открываем SortBottomSheet
        val sortIconDesc = composeTestRule.activity.getString(R.string.home_top_bar_action_sort)
        composeTestRule.onNodeWithContentDescription(sortIconDesc).performClick()
        composeTestRule.waitForIdle()

        // 10. Act: Выбираем "По названию" и "По возрастанию"
        val sortByNameText = composeTestRule.activity.getString(R.string.sort_option_name)
        val sortByAscText = composeTestRule.activity.getString(R.string.sort_option_asc)
        composeTestRule.onNodeWithText(sortByNameText).performClick()
        composeTestRule.onNodeWithText(sortByAscText).performClick()

        // BottomSheet закроется автоматически, ждем рекомпозиции
        composeTestRule.waitForIdle()

        // 11. Assert: Проверяем новый алфавитный порядок
        val allNodesSorted = composeTestRule.onAllNodesWithText(" Scan", substring = true)
        allNodesSorted[0].assertTextContains("Apple Scan")
        allNodesSorted[1].assertTextContains("Banana Scan")
        allNodesSorted[2].assertTextContains("Cherry Scan")
    }
}