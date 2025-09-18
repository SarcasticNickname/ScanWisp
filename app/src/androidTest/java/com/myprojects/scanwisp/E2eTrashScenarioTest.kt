package com.myprojects.scanwisp

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
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
class E2eTrashScenarioTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule =
        createAndroidComposeRule<MainActivity>() // Используем реальную MainActivity

    @Inject
    lateinit var dao: DocumentDao

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        hiltRule.inject()

        // Запускаем наш NavHost внутри теста
        composeTestRule.setContent {
            navController = rememberNavController()
            AppNavHost(
                navController = navController,
                windowSizeClass = WindowWidthSizeClass.Compact,
                onboardingCheckState = OnboardingCheckState.Completed // Сразу переходим на главный экран
            )
        }
    }

    @Test
    fun documentDeletionAndRestoration_fullCycle() = runTest {
        // --- ЧАСТЬ 1: Подготовка и Удаление ---

        // 1. Arrange: Создаем документ напрямую в БД
        val docToDelete = DocumentEntity(
            id = "doc-e2e",
            title = "My E2E Document",
            creationTimestamp = 1L,
            coverImagePath = "",
            folderId = null
        )
        dao.insertDocument(docToDelete)

        // Ждем, пока UI обновится и документ появится на экране
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("My E2E Document").assertIsDisplayed()

        // 2. Act: Выполняем длинное нажатие и клик по иконке удаления
        composeTestRule.onNodeWithText("My E2E Document").performTouchInput { longClick() }
        val deleteButtonDescription =
            composeTestRule.activity.getString(R.string.home_selection_bar_cd_delete)
        composeTestRule.onNodeWithContentDescription(deleteButtonDescription).performClick()

        // 3. Assert: Проверяем, что документ исчез с главного экрана
        // (он попадет в EmptyState, так как других документов нет)
        val emptyStateText =
            composeTestRule.activity.getString(R.string.empty_state_title_no_documents)
        composeTestRule.onNodeWithText(emptyStateText).assertIsDisplayed()

        // --- ЧАСТЬ 2: Переход в корзину и проверка ---

        // 4. Act: Нажимаем на иконку корзины в BottomAppBar
        val trashNavDescription = composeTestRule.activity.getString(R.string.bottom_nav_trash)
        composeTestRule.onNodeWithText(trashNavDescription).performClick()

        // 5. Assert: Проверяем, что мы на экране корзины и документ там
        composeTestRule.waitForIdle()
        val trashTitle = composeTestRule.activity.getString(R.string.trash_screen_title)
        composeTestRule.onNodeWithText(trashTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText("My E2E Document").assertIsDisplayed()

        // --- ЧАСТЬ 3: Восстановление документа ---

        // 6. Act: Нажимаем на меню "..." и на "Восстановить"
        val moreActionsDescription = composeTestRule.activity.getString(
            R.string.document_card_cd_more_actions,
            "My E2E Document"
        )
        composeTestRule.onNodeWithContentDescription(moreActionsDescription).performClick()

        val restoreActionText = composeTestRule.activity.getString(R.string.trash_action_restore)
        composeTestRule.onNodeWithText(restoreActionText).performClick()

        // 7. Assert: Проверяем, что документ исчез из корзины (появился EmptyState)
        val trashEmptyStateText =
            composeTestRule.activity.getString(R.string.trash_empty_state_title)
        composeTestRule.onNodeWithText(trashEmptyStateText).assertIsDisplayed()

        // --- ЧАСТЬ 4: Возвращение и финальная проверка ---

        // 8. Act: Возвращаемся на главный экран
        val homeNavDescription = composeTestRule.activity.getString(R.string.bottom_nav_home)
        composeTestRule.onNodeWithText(homeNavDescription).performClick()

        // 9. Assert: Проверяем, что документ снова появился на главном экране
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("My E2E Document").assertIsDisplayed()
    }
}